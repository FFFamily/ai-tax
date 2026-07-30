package com.rcszh.tax.postprocess.dividend;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.postprocess.RecordPostProcessor;
import com.rcszh.tax.server.DocumentServer;
import com.rcszh.tax.server.DocumentTaskServer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DividendExtractPostProcessor implements RecordPostProcessor {
    private final DividendExtractService dividendExtractService;

    public DividendExtractPostProcessor(DividendExtractService dividendExtractService) {
        this.dividendExtractService = dividendExtractService;
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public String name() {
        return "dividend-extract";
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean supports(AIParseResult parseResult, Map<String, Object> taskItem, Map<String, Object> document) {
        Object candidates = parseResult.getGlobalParam().get(ResultBaseFieldConstant.DIVIDEND_CANDIDATES);
        if (!(candidates instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        String documentType = document == null ? null : (String) document.get(DocumentServer.TYPE);
        String requestedDocumentType = taskItem == null ? null : (String) taskItem.get(DocumentTaskServer.Item.REQUESTED_DOCUMENT_TYPE);
        return containsDividendHint(documentType) || containsDividendHint(requestedDocumentType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(AIParseResult parseResult, Map<String, Object> taskItem, Map<String, Object> document) {
        Object candidateObject = parseResult.getGlobalParam().get(ResultBaseFieldConstant.DIVIDEND_CANDIDATES);
        if (!(candidateObject instanceof List<?> candidateMaps) || candidateMaps.isEmpty()) {
            return;
        }
        List<DividendCandidateRecord> candidates = candidateMaps.stream()
                .map(this::toCandidateRecord)
                .toList();
        List<DividendExtractRecord> extracted = dividendExtractService.extract(candidates);
        if (extracted.isEmpty()) {
            return;
        }
        List<Map<String, Object>> extractedMaps = extracted.stream()
                .map(DividendExtractRecord::toMap)
                .toList();
        parseResult.getGlobalParam().put(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS, extractedMaps);
        parseResult.getGlobalParam().put("dividendExtractCount", extractedMaps.size());
        if (parseResult.getRecords() == null || parseResult.getRecords().isEmpty()) {
            parseResult.setRecords(extractedMaps);
        }
        parseResult.getWarnings().add("已生成股息专项记录 " + extractedMaps.size() + " 条。");
    }

    @SuppressWarnings("unchecked")
    private DividendCandidateRecord toCandidateRecord(Object source) {
        if (source instanceof DividendCandidateRecord record) {
            return record;
        }
        Map<String, Object> map = (Map<String, Object>) source;
        DividendCandidateRecord record = new DividendCandidateRecord();
        record.setRowId((String) map.get("rowId"));
        record.setDividendDate((String) map.get("dividendDate"));
        record.setPayer((String) map.get("payer"));
        record.setSummary((String) map.get("summary"));
        record.setCurrency((String) map.get("currency"));
        record.setDirection((String) map.get("direction"));
        Object amount = map.get("amount");
        if (amount != null) {
            record.setAmount(new java.math.BigDecimal(amount.toString()));
        }
        Object balance = map.get("balance");
        if (balance != null) {
            record.setBalance(new java.math.BigDecimal(balance.toString()));
        }
        Object confidence = map.get("confidence");
        if (confidence != null) {
            record.setConfidence(new java.math.BigDecimal(confidence.toString()));
        }
        record.setCategory((String) map.get("category"));
        Object reasons = map.get("reasons");
        if (reasons instanceof List<?> list) {
            record.setReasons(list.stream().map(String::valueOf).toList());
        }
        Object evidence = map.get("evidence");
        if (evidence instanceof Map<?, ?> evidenceMap) {
            record.getEvidence().putAll((Map<String, Object>) evidenceMap);
        }
        Object rawData = map.get("rawData");
        if (rawData instanceof Map<?, ?> rawMap) {
            record.getRawData().putAll((Map<String, Object>) rawMap);
        }
        return record;
    }

    private boolean containsDividendHint(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        return value.contains("股息") || value.contains("红利") || value.toLowerCase().contains("dividend");
    }
}
