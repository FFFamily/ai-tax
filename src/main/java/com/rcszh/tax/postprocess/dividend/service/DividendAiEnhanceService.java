package com.rcszh.tax.postprocess.dividend.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.rcszh.tax.config.AppProperties;
import com.rcszh.tax.entity.ChatLog;
import com.rcszh.tax.postprocess.dividend.model.DividendCandidateRecord;
import com.rcszh.tax.postprocess.dividend.model.DividendExtractRecord;
import com.rcszh.tax.service.ChatLogService;
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
 * 使用大模型补齐和纠正规则股息抽取结果的降级增强服务。
 *
 * <p>增强建立在规则结果之上，不替代规则基线。API Key 缺失、模型调用异常或返回结果为空时，
 * 均直接返回 {@code currentRecords}，保证后处理链仍可继续。</p>
 */
@Component
public class DividendAiEnhanceService {
    /** 提供模型 API Key、地址和模型名称等 AI 配置。 */
    @Resource
    private AppProperties appProperties;
    /** 持久化模型请求、响应和异常信息的日志服务。 */
    @Resource
    private ChatLogService chatLogService;

    /**
     * 使用候选证据对规则抽取结果进行可选增强。
     *
     * @param candidates 原始股息候选行，为模型提供证据上下文
     * @param currentRecords 规则聚合生成的基线记录
     * @return AI 字段与规则字段合并后的记录；不可增强时返回规则基线
     */
    public List<DividendExtractRecord> enhance(List<DividendCandidateRecord> candidates,
                                               List<DividendExtractRecord> currentRecords) {
        if (candidates == null || candidates.isEmpty() || StrUtil.isBlank(appProperties.getAi().getDeepseekApiKey())) {
            return currentRecords;
        }
        try {
            // 每次增强构建独立 agent 和内存状态，避免不同文档之间共享对话上下文。
            ReactAgent agent = ReactAgent.builder()
                    .name("dividend_extract_agent")
                    .model(buildChatModel())
                    .systemPrompt(buildSystemPrompt())
                    .saver(new MemorySaver())
                    .build();
            String prompt = buildUserPrompt(candidates, currentRecords);
            String response = send(agent, prompt);
            List<DividendExtractRecord> aiRecords = parseRecords(response);
            return aiRecords.isEmpty() ? currentRecords : mergeRecords(currentRecords, aiRecords);
        } catch (Exception e) {
            return currentRecords;
        }
    }

    /**
     * 根据应用配置创建兼容 OpenAI 协议的 DeepSeek 聊天模型。
     *
     * @return 股息增强使用的聊天模型
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
     * 构造约束模型角色、目标字段和输出格式的系统提示词。
     *
     * @return 系统提示词
     */
    private String buildSystemPrompt() {
        return """
                你是一个股息专项结构化抽取器。
                你会收到已经初步识别出的股息候选行和规则聚合结果。
                你需要尽量补齐：dividendDate、payer、currency、netAmount、withholdingTax、grossAmount、confidence、summary、evidenceRowIds。
                如果无法确认，不要编造，保留为空并降低 confidence。
                你必须严格返回 JSON 数组，不输出其他内容。
                """;
    }

    /**
     * 将候选证据和规则结果序列化为模型输入。
     *
     * @param candidates 原始候选记录
     * @param currentRecords 当前规则抽取结果
     * @return 包含输入数据和 JSON 输出示例的用户提示词
     */
    private String buildUserPrompt(List<DividendCandidateRecord> candidates,
                                   List<DividendExtractRecord> currentRecords) {
        /** 序列化后的候选证据列表。 */
        List<Map<String, Object>> candidatePayload = candidates.stream()
                .map(DividendCandidateRecord::toMap)
                .toList();
        /** 序列化后的规则基线列表。 */
        List<Map<String, Object>> currentPayload = currentRecords.stream()
                .map(DividendExtractRecord::toMap)
                .toList();
        /** 发送给模型的完整结构化载荷。 */
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidateRecords", candidatePayload);
        payload.put("currentRecords", currentPayload);
        return """
                请基于以下股息候选行与规则抽取结果，生成更完整的股息专项记录。
                若发现某条是税费记录，请放入 withholdingTax；若是收入记录，请放入 netAmount。
                若能合理判断 grossAmount，请输出 grossAmount = netAmount + withholdingTax。
                
                输入：
                """ + JSONUtil.toJsonStr(payload) + """
                
                输出 JSON 数组示例：
                [
                  {
                    "dividendDate": "2025-01-18",
                    "payer": "XXXX公司",
                    "currency": "USD",
                    "netAmount": 1234.56,
                    "withholdingTax": 265.44,
                    "grossAmount": 1500.00,
                    "confidence": 0.82,
                    "category": "DIVIDEND",
                    "summary": "股息入账",
                    "evidenceRowIds": ["p3_t2_r15"],
                    "reasons": ["..."]
                  }
                ]
                """;
    }

    /**
     * 调用智能体并记录请求、响应或异常。
     *
     * @param agent 已配置的股息抽取智能体
     * @param prompt 用户提示词
     * @return 模型响应文本；响应为空时返回空字符串
     */
    private String send(ReactAgent agent, String prompt) {
        // chatLog 为本次模型调用的审计记录，异常时同样持久化。
        ChatLog chatLog = new ChatLog();
        chatLog.setPrompt(prompt);
        try {
            AssistantMessage response = agent.call(prompt);
            chatLog.setResult(response == null ? "" : response.getText());
            chatLogService.save(chatLog);
            return response == null ? "" : response.getText();
        } catch (GraphRunnerException e) {
            chatLog.setResult("DIVIDEND_AI_ERROR:" + e.getMessage());
            chatLogService.save(chatLog);
            throw new RuntimeException(e);
        }
    }

    /**
     * 将模型返回的 JSON 数组解析为专项股息记录。
     *
     * @param response 模型原始响应，可包含 Markdown JSON 代码块
     * @return 解析后的记录列表；响应为空时返回空列表
     */
    private List<DividendExtractRecord> parseRecords(String response) {
        if (StrUtil.isBlank(response)) {
            return List.of();
        }
        String json = extractJson(response);
        JSONArray array = JSONUtil.parseArray(json);
        // records 按模型响应顺序保存，以便与规则结果按索引合并。
        List<DividendExtractRecord> records = new ArrayList<>();
        for (Object item : array) {
            JSONObject object = JSONUtil.parseObj(item);
            DividendExtractRecord record = new DividendExtractRecord();
            record.setDividendDate(object.getStr("dividendDate"));
            record.setPayer(object.getStr("payer"));
            record.setCurrency(object.getStr("currency"));
            record.setSummary(object.getStr("summary"));
            record.setCategory(object.getStr("category", "DIVIDEND"));
            record.setNetAmount(getDecimal(object, "netAmount"));
            record.setWithholdingTax(getDecimal(object, "withholdingTax"));
            record.setGrossAmount(getDecimal(object, "grossAmount"));
            record.setConfidence(getDecimal(object, "confidence"));
            JSONArray rowIds = object.getJSONArray("evidenceRowIds");
            if (rowIds != null) {
                record.setEvidenceRowIds(new ArrayList<>(rowIds.toList(String.class)));
            }
            JSONArray reasons = object.getJSONArray("reasons");
            if (reasons != null) {
                record.setReasons(new ArrayList<>(reasons.toList(String.class)));
            }
            records.add(record);
        }
        return records;
    }

    /**
     * 按列表索引合并规则结果与 AI 结果。
     *
     * <p>任一侧多出的记录会直接保留；同一索引的记录由 {@link #mergeOne} 合并。</p>
     *
     * @param currentRecords 规则基线记录
     * @param aiRecords AI 返回记录
     * @return 合并后的记录列表
     */
    private List<DividendExtractRecord> mergeRecords(List<DividendExtractRecord> currentRecords,
                                                     List<DividendExtractRecord> aiRecords) {
        if (currentRecords == null || currentRecords.isEmpty()) {
            return aiRecords;
        }
        if (aiRecords == null || aiRecords.isEmpty()) {
            return currentRecords;
        }
        /** 保存最终合并结果。 */
        List<DividendExtractRecord> merged = new ArrayList<>();
        /** 两侧列表的最大长度，确保不丢失任一侧的尾部记录。 */
        int size = Math.max(currentRecords.size(), aiRecords.size());
        for (int i = 0; i < size; i++) {
            DividendExtractRecord base = i < currentRecords.size() ? currentRecords.get(i) : null;
            DividendExtractRecord ai = i < aiRecords.size() ? aiRecords.get(i) : null;
            if (base == null) {
                merged.add(ai);
                continue;
            }
            if (ai == null) {
                merged.add(base);
                continue;
            }
            merged.add(mergeOne(base, ai));
        }
        return merged;
    }

    /**
     * 合并同一索引的规则记录和 AI 记录。
     *
     * <p>业务字段优先采用 AI 的非空值，AI 未提供时保留规则值；原始 evidence 始终取规则结果，
     * 避免模型生成内容覆盖可审计证据。</p>
     *
     * @param base 规则基线记录
     * @param ai AI 增强记录
     * @return 新建的合并记录
     */
    private DividendExtractRecord mergeOne(DividendExtractRecord base, DividendExtractRecord ai) {
        DividendExtractRecord merged = new DividendExtractRecord();
        merged.setDividendDate(firstNonBlank(ai.getDividendDate(), base.getDividendDate()));
        merged.setPayer(firstNonBlank(ai.getPayer(), base.getPayer()));
        merged.setCurrency(firstNonBlank(ai.getCurrency(), base.getCurrency()));
        merged.setSummary(firstNonBlank(ai.getSummary(), base.getSummary()));
        merged.setCategory(firstNonBlank(ai.getCategory(), base.getCategory()));
        merged.setNetAmount(ai.getNetAmount() != null ? ai.getNetAmount() : base.getNetAmount());
        merged.setWithholdingTax(ai.getWithholdingTax() != null ? ai.getWithholdingTax() : base.getWithholdingTax());
        merged.setGrossAmount(ai.getGrossAmount() != null ? ai.getGrossAmount() : base.getGrossAmount());
        merged.setConfidence(ai.getConfidence() != null ? ai.getConfidence() : base.getConfidence());
        merged.setEvidenceRowIds(ai.getEvidenceRowIds() == null || ai.getEvidenceRowIds().isEmpty()
                ? base.getEvidenceRowIds() : ai.getEvidenceRowIds());
        merged.setReasons(ai.getReasons() == null || ai.getReasons().isEmpty()
                ? base.getReasons() : ai.getReasons());
        merged.setEvidence(base.getEvidence());
        return merged;
    }

    /**
     * 读取 JSON 数字字段并转换为 {@link BigDecimal}。
     *
     * @param object JSON 对象
     * @param key 数字字段名
     * @return 十进制数值，字段为空时返回 {@code null}
     */
    private BigDecimal getDecimal(JSONObject object, String key) {
        Object value = object.get(key);
        return value == null ? null : new BigDecimal(value.toString());
    }

    /**
     * 在主值和回退值之间选择首个非空字符串。
     *
     * @param primary 优先值
     * @param fallback 回退值
     * @return 优先值非空时返回优先值，否则返回回退值
     */
    private String firstNonBlank(String primary, String fallback) {
        return StrUtil.isNotBlank(primary) ? primary : fallback;
    }

    /**
     * 从模型响应中提取 JSON 数组文本。
     *
     * @param response 模型原始响应
     * @return JSON 代码块、首尾方括号内容或去空白后的原响应
     */
    private String extractJson(String response) {
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }
        int start = response.indexOf("[");
        int end = response.lastIndexOf("]") + 1;
        if (start >= 0 && end > start) {
            return response.substring(start, end);
        }
        return response.trim();
    }
}
