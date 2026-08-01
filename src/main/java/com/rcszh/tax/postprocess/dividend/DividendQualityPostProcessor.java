package com.rcszh.tax.postprocess.dividend;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.postprocess.RecordPostProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DividendQualityPostProcessor implements RecordPostProcessor {
    @Override
    public int order() {
        return 70;
    }

    @Override
    public String name() {
        return "dividend-quality";
    }

    @Override
    public boolean supports(AIParseResult parseResult, DocumentTaskItem taskItem, Map<String, Object> document) {
        Object records = parseResult.getGlobalParam().get(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS);
        return records instanceof List<?> list && !list.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(AIParseResult parseResult, DocumentTaskItem taskItem, Map<String, Object> document) {
        Object records = parseResult.getGlobalParam().get(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS);
        if (!(records instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        // 质量校验负责把“可抽取”进一步提升为“可复核、可导出、可追责”的记录。
        List<Map<String, Object>> reviewed = new ArrayList<>();
        List<String> reviewReasons = new ArrayList<>();
        boolean needHumanReview = false;
        Set<String> duplicateGroups = new HashSet<>();
        Set<String> seenGroups = new HashSet<>();
        for (Object item : list) {
            Map<String, Object> record = new LinkedHashMap<>((Map<String, Object>) item);
            // 先做标准化，再校验字段一致性，避免格式差异把本来正确的记录误判为异常。
            normalize(record);
            String groupKey = buildDuplicateKey(record);
            if (StrUtil.isNotBlank(groupKey) && !seenGroups.add(groupKey)) {
                duplicateGroups.add(groupKey);
            }
            List<String> qualityWarnings = evaluate(record);
            boolean recordNeedReview = !qualityWarnings.isEmpty();
            Object confidence = record.get("confidence");
            if (confidence != null && new BigDecimal(confidence.toString()).compareTo(new BigDecimal("0.75")) < 0) {
                qualityWarnings.add("置信度低于0.75");
                recordNeedReview = true;
            }
            if (recordNeedReview) {
                needHumanReview = true;
                reviewReasons.addAll(qualityWarnings);
            }
            record.put("qualityWarnings", qualityWarnings.stream().distinct().toList());
            record.put("needHumanReview", recordNeedReview);
            reviewed.add(record);
        }
        if (!duplicateGroups.isEmpty()) {
            // 重复分红记录是高风险问题，直接升级为人工复核。
            needHumanReview = true;
            reviewReasons.add("存在疑似重复股息记录");
            for (Map<String, Object> record : reviewed) {
                if (duplicateGroups.contains(buildDuplicateKey(record))) {
                    appendWarning(record, "存在疑似重复记录");
                    record.put("needHumanReview", true);
                }
            }
        }
        parseResult.getGlobalParam().put(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS, reviewed);
        if (taskItem.getRouteSummary() != null) {
            parseResult.getGlobalParam().put("routeSummary", taskItem.getRouteSummary());
            // 模板路由本身置信度过低时，即使字段看起来完整，也要提醒人工确认模板是否选对。
            BigDecimal routeConfidence = taskItem.getRouteSummary().getConfidence();
            if (routeConfidence != null && routeConfidence.compareTo(new BigDecimal("0.60")) < 0) {
                needHumanReview = true;
                taskItem.setNeedHumanReview(true);
                reviewReasons.add("路由置信度低于0.60");
                parseResult.getWarnings().add("文档路由置信度较低，建议人工确认模板。");
            }
        }
        List<String> finalReviewReasons = reviewReasons.stream().distinct().toList();
        parseResult.getGlobalParam().put("dividendReviewReasons", finalReviewReasons);
        parseResult.getGlobalParam().put("reviewReasons", finalReviewReasons);
        parseResult.getGlobalParam().put("needHumanReview", needHumanReview);
        if (needHumanReview) {
            taskItem.setNeedHumanReview(true);
            taskItem.setReviewReasons(JSONUtil.toJsonStr(finalReviewReasons));
            parseResult.getWarnings().add("股息专项记录存在低置信或字段缺失，建议人工复核。");
        } else {
            taskItem.setNeedHumanReview(false);
            taskItem.setReviewReasons(JSONUtil.toJsonStr(finalReviewReasons));
        }
        if (parseResult.getRecords() != null && !parseResult.getRecords().isEmpty()) {
            parseResult.setRecords(reviewed);
        }
    }

    private void normalize(Map<String, Object> record) {
        // 统一日期、币种和金额格式，并尽量在净额/税额/毛额之间做自动补全。
        normalizeDate(record);
        normalizeCurrency(record);
        BigDecimal net = normalizeAmount(record, "netAmount");
        BigDecimal tax = normalizeAmount(record, "withholdingTax");
        BigDecimal gross = normalizeAmount(record, "grossAmount");
        if (gross == null && net != null && tax != null) {
            gross = net.add(tax);
            record.put("grossAmount", gross);
        }
        if (net == null && gross != null && tax != null && gross.compareTo(tax) >= 0) {
            net = gross.subtract(tax);
            record.put("netAmount", net);
        }
        if (tax == null && gross != null && net != null && gross.compareTo(net) >= 0) {
            tax = gross.subtract(net);
            record.put("withholdingTax", tax);
        }
    }

    private List<String> evaluate(Map<String, Object> record) {
        List<String> warnings = new ArrayList<>();
        // 这里关注的是“结果是否可申报/可解释”，而不是单纯字段是否非空。
        if (isBlank(record.get("dividendDate"))) {
            warnings.add("缺少股息日期");
        }
        if (isBlank(record.get("payer"))) {
            warnings.add("缺少付款方");
        }
        if (isBlank(record.get("currency"))) {
            warnings.add("缺少币种");
        }
        if (record.get("netAmount") == null && record.get("grossAmount") == null) {
            warnings.add("缺少金额信息");
        }
        if (record.get("grossAmount") != null && record.get("netAmount") != null && record.get("withholdingTax") != null) {
            BigDecimal gross = new BigDecimal(record.get("grossAmount").toString());
            BigDecimal net = new BigDecimal(record.get("netAmount").toString());
            BigDecimal tax = new BigDecimal(record.get("withholdingTax").toString());
            if (gross.compareTo(net.add(tax)) != 0) {
                warnings.add("毛额不等于净额加税额");
            }
            if (tax.compareTo(gross) > 0) {
                warnings.add("税额大于毛额");
            }
        }
        if (record.get("netAmount") != null && new BigDecimal(record.get("netAmount").toString()).compareTo(BigDecimal.ZERO) < 0) {
            warnings.add("净额为负数");
        }
        if (record.get("grossAmount") != null && new BigDecimal(record.get("grossAmount").toString()).compareTo(BigDecimal.ZERO) < 0) {
            warnings.add("毛额为负数");
        }
        Object evidenceRowIds = record.get("evidenceRowIds");
        if (!(evidenceRowIds instanceof List<?> list) || list.isEmpty()) {
            warnings.add("缺少证据行");
        } else {
            long distinctCount = list.stream().distinct().count();
            if (distinctCount != list.size()) {
                warnings.add("存在重复证据行");
            }
        }
        return warnings;
    }

    private void normalizeDate(Map<String, Object> record) {
        Object value = record.get("dividendDate");
        if (value == null || value.toString().isBlank()) {
            return;
        }
        try {
            record.put("dividendDate", DateUtil.parse(value.toString()).toDateStr());
        } catch (Exception ignored) {
        }
    }

    private void normalizeCurrency(Map<String, Object> record) {
        Object value = record.get("currency");
        if (value == null || value.toString().isBlank()) {
            return;
        }
        record.put("currency", value.toString().trim().toUpperCase());
    }

    private BigDecimal normalizeAmount(Map<String, Object> record, String key) {
        Object value = record.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        BigDecimal normalized = new BigDecimal(value.toString()).abs();
        record.put(key, normalized);
        return normalized;
    }

    private String buildDuplicateKey(Map<String, Object> record) {
        String date = stringValue(record.get("dividendDate"));
        String payer = stringValue(record.get("payer"));
        String currency = stringValue(record.get("currency"));
        String gross = stringValue(record.get("grossAmount"));
        if (date.isBlank() && payer.isBlank() && currency.isBlank() && gross.isBlank()) {
            return "";
        }
        return String.join("|", date, payer, currency, gross);
    }

    @SuppressWarnings("unchecked")
    private void appendWarning(Map<String, Object> record, String warning) {
        Object value = record.get("qualityWarnings");
        List<String> warnings = value instanceof List<?> list
                ? new ArrayList<>(list.stream().map(String::valueOf).toList())
                : new ArrayList<>();
        warnings.add(warning);
        record.put("qualityWarnings", warnings.stream().distinct().toList());
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }
}
