package com.rcszh.tax.route;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.rcszh.tax.config.AppProperties;
import com.rcszh.tax.entity.ChatLog;
import com.rcszh.tax.service.ChatLogService;
import com.rcszh.tax.service.ReviewLearningService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 低置信规则路由的 AI 兜底服务。
 *
 * <p>AI 只能从调用方提供的模板候选集合中选择，不负责扩大候选范围。缺少模型配置、模型调用失败、
 * 返回 UNKNOWN 或响应无法解析时均返回 {@code null}，由调用方继续使用规则结果。</p>
 */
@Component
public class RouteAiFallbackService {
    @Resource
    private AppProperties appProperties;
    @Resource
    private ChatLogService chatLogService;
    @Resource
    private ReviewLearningService reviewLearningService;

    /**
     * 请求 AI 在候选模板中完成一次路由判断。
     *
     * @param context 当前文档的轻量路由特征
     * @param candidates 已经过文档类型收窄的候选模板
     * @return 可用的 AI 决策；服务不可用、无明确结论或响应异常时返回 {@code null}
     */
    public RouteAiDecision decide(DocumentRouteContext context, List<Map<String, Object>> candidates) {
        if (candidates == null || candidates.isEmpty() || StrUtil.isBlank(appProperties.getAi().getDeepseekApiKey())) {
            return null;
        }
        try {
            ReactAgent agent = ReactAgent.builder()
                    .name("route_ai_agent")
                    .model(buildChatModel())
                    .systemPrompt(buildSystemPrompt())
                    .saver(new MemorySaver())
                    .build();
            String prompt = buildUserPrompt(context, candidates);
            String response = send(agent, prompt);
            return parseDecision(response);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据应用配置创建兼容 OpenAI 协议的 DeepSeek 聊天模型。
     */
    private ChatModel buildChatModel() {
        OpenAiApi deepSeekApi = OpenAiApi.builder()
                .apiKey(appProperties.getAi().getDeepseekApiKey())
                .baseUrl(appProperties.getAi().getDeepseekBaseUrl())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(deepSeekApi)
                .defaultOptions(OpenAiChatOptions.builder().model(appProperties.getAi().getDeepseekModel()).build())
                .build();
    }

    /**
     * 构建限制模型只能选择候选模板并严格输出 JSON 的系统提示词。
     */
    private String buildSystemPrompt() {
        return """
                你是一个文档路由分类器。
                你只能从提供的候选模板中选择一个最匹配的 documentId，或者返回 UNKNOWN。
                你必须基于输入特征、表头、关键词、材料语义进行判断。
                请严格返回 JSON，不要输出额外说明。
                """;
    }

    /**
     * 组装 AI 路由输入，包含文档上下文、模板规则、复核关键词和少量复核样例。
     */
    private String buildUserPrompt(DocumentRouteContext context, List<Map<String, Object>> candidates) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("context", context);
        List<Map<String, Object>> candidatePayload = candidates.stream().map(candidate -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", candidate.get("id"));
            item.put("name", candidate.get("name"));
            item.put("type", candidate.get("type"));
            item.put("variant", candidate.get("variant"));
            item.put("matchRule", candidate.get("matchRule"));
            item.put("learnedKeywords", reviewLearningService.listSuggestedKeywords(
                    (Long) candidate.get("id"),
                    (String) candidate.get("type"),
                    8
            ));
            item.put("fewShotExamples", reviewLearningService.listFewShotExamples(
                    (Long) candidate.get("id"),
                    (String) candidate.get("type"),
                    2
            ));
            return item;
        }).toList();
        payload.put("candidates", candidatePayload);
        return """
                请从以下候选模板中选择最适合当前材料的模板。
                若无法确认，请返回 UNKNOWN 并将 needHumanReview 设为 true。
                
                输入数据：
                """ + JSONUtil.toJsonStr(payload) + """
                
                输出格式：
                {
                  "documentId": "候选documentId或UNKNOWN",
                  "confidence": 0.0,
                  "needHumanReview": true,
                  "reasons": ["原因1", "原因2"]
                }
                """;
    }

    /**
     * 调用路由 Agent，并把提示词、原始响应或调用错误写入聊天日志。
     */
    private String send(ReactAgent agent, String prompt) {
        ChatLog chatLog = new ChatLog();
        chatLog.setPrompt(prompt);
        try {
            AssistantMessage response = agent.call(prompt);
            chatLog.setResult(response == null ? "" : response.getText());
            chatLog.setToken(response == null || response.getText() == null ? 0 : response.getText().length());
            chatLogService.save(chatLog);
            return response == null ? "" : response.getText();
        } catch (GraphRunnerException e) {
            chatLog.setResult("ROUTE_AI_ERROR:" + e.getMessage());
            chatLogService.save(chatLog);
            throw new RuntimeException(e);
        }
    }

    /**
     * 将模型 JSON 响应转换为内部决策。
     *
     * <p>UNKNOWN 或空 documentId 表示模型无法确定，返回 {@code null}；缺少置信度时使用 0.50，
     * 缺少人工复核标记时默认需要复核。</p>
     */
    private RouteAiDecision parseDecision(String response) {
        if (StrUtil.isBlank(response)) {
            return null;
        }
        String json = extractJson(response);
        JSONObject object = JSONUtil.parseObj(json);
        String documentId = object.getStr("documentId");
        if (StrUtil.isBlank(documentId) || "UNKNOWN".equalsIgnoreCase(documentId)) {
            return null;
        }
        RouteAiDecision decision = new RouteAiDecision();
        decision.setDocumentId(Long.valueOf(documentId));
        Object confidence = object.get("confidence");
        decision.setConfidence(confidence == null ? new BigDecimal("0.50") : new BigDecimal(confidence.toString()));
        decision.setNeedHumanReview(object.getBool("needHumanReview", Boolean.TRUE));
        JSONArray reasons = object.getJSONArray("reasons");
        if (reasons != null) {
            decision.setReasons(new ArrayList<>(reasons.toList(String.class)));
        }
        return decision;
    }

    /**
     * 从 Markdown JSON 代码块或混合文本中提取首个完整 JSON 对象。
     */
    private String extractJson(String response) {
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}") + 1;
        if (start >= 0 && end > start) {
            return response.substring(start, end);
        }
        return response.trim();
    }
}
