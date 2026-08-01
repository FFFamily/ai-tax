package com.rcszh.tax.postprocess.dividend;

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

@Component
public class DividendAiEnhanceService {
    @Resource
    private AppProperties appProperties;
    @Resource
    private ChatLogService chatLogService;

    public List<DividendExtractRecord> enhance(List<DividendCandidateRecord> candidates,
                                               List<DividendExtractRecord> currentRecords) {
        if (candidates == null || candidates.isEmpty() || StrUtil.isBlank(appProperties.getAi().getDeepseekApiKey())) {
            return currentRecords;
        }
        try {
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
                你是一个股息专项结构化抽取器。
                你会收到已经初步识别出的股息候选行和规则聚合结果。
                你需要尽量补齐：dividendDate、payer、currency、netAmount、withholdingTax、grossAmount、confidence、summary、evidenceRowIds。
                如果无法确认，不要编造，保留为空并降低 confidence。
                你必须严格返回 JSON 数组，不输出其他内容。
                """;
    }

    private String buildUserPrompt(List<DividendCandidateRecord> candidates,
                                   List<DividendExtractRecord> currentRecords) {
        List<Map<String, Object>> candidatePayload = candidates.stream()
                .map(DividendCandidateRecord::toMap)
                .toList();
        List<Map<String, Object>> currentPayload = currentRecords.stream()
                .map(DividendExtractRecord::toMap)
                .toList();
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
            chatLog.setResult("DIVIDEND_AI_ERROR:" + e.getMessage());
            chatLogService.save(chatLog);
            throw new RuntimeException(e);
        }
    }

    private List<DividendExtractRecord> parseRecords(String response) {
        if (StrUtil.isBlank(response)) {
            return List.of();
        }
        String json = extractJson(response);
        JSONArray array = JSONUtil.parseArray(json);
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

    private List<DividendExtractRecord> mergeRecords(List<DividendExtractRecord> currentRecords,
                                                     List<DividendExtractRecord> aiRecords) {
        if (currentRecords == null || currentRecords.isEmpty()) {
            return aiRecords;
        }
        if (aiRecords == null || aiRecords.isEmpty()) {
            return currentRecords;
        }
        List<DividendExtractRecord> merged = new ArrayList<>();
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

    private BigDecimal getDecimal(JSONObject object, String key) {
        Object value = object.get(key);
        return value == null ? null : new BigDecimal(value.toString());
    }

    private String firstNonBlank(String primary, String fallback) {
        return StrUtil.isNotBlank(primary) ? primary : fallback;
    }

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
