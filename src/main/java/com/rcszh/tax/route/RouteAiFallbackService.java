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

@Component
public class RouteAiFallbackService {
    private final AppProperties appProperties;
    private final ChatLogService chatLogService;
    private final ReviewLearningService reviewLearningService;

    public RouteAiFallbackService(AppProperties appProperties,
                                  ChatLogService chatLogService,
                                  ReviewLearningService reviewLearningService) {
        this.appProperties = appProperties;
        this.chatLogService = chatLogService;
        this.reviewLearningService = reviewLearningService;
    }

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

    private String buildSystemPrompt() {
        return """
                你是一个文档路由分类器。
                你只能从提供的候选模板中选择一个最匹配的 documentId，或者返回 UNKNOWN。
                你必须基于输入特征、表头、关键词、材料语义进行判断。
                请严格返回 JSON，不要输出额外说明。
                """;
    }

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
