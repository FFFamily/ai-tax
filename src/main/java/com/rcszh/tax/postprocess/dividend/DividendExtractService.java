package com.rcszh.tax.postprocess.dividend;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class DividendExtractService {
    private final DividendAiEnhanceService dividendAiEnhanceService;

    public DividendExtractService(DividendAiEnhanceService dividendAiEnhanceService) {
        this.dividendAiEnhanceService = dividendAiEnhanceService;
    }

    public List<DividendExtractRecord> extract(List<DividendCandidateRecord> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, List<DividendCandidateRecord>> grouped = new LinkedHashMap<>();
        // 候选记录先按日期/付款方/币种聚合，尽量把“分红入账 + 预扣税”还原为一笔业务事件。
        for (DividendCandidateRecord candidate : candidates) {
            grouped.computeIfAbsent(groupKey(candidate), key -> new ArrayList<>()).add(candidate);
        }
        List<DividendExtractRecord> result = new ArrayList<>();
        for (List<DividendCandidateRecord> group : grouped.values()) {
            DividendExtractRecord record = toRecord(group);
            if (record != null) {
                result.add(record);
            }
        }
        // 规则聚合完成后，再交给 AI 做补齐和纠偏，避免一开始就把完整推断全部交给模型。
        return dividendAiEnhanceService.enhance(candidates, result);
    }

    private DividendExtractRecord toRecord(List<DividendCandidateRecord> group) {
        if (group == null || group.isEmpty()) {
            return null;
        }
        DividendCandidateRecord base = group.getFirst();
        DividendExtractRecord record = new DividendExtractRecord();
        record.setDividendDate(base.getDividendDate());
        record.setPayer(base.getPayer());
        record.setCurrency(base.getCurrency());
        record.setSummary(base.getSummary());
        record.setCategory("DIVIDEND");

        BigDecimal netAmount = null;
        BigDecimal withholdingTax = null;
        BigDecimal totalConfidence = BigDecimal.ZERO;
        List<String> reasons = new ArrayList<>();
        Map<String, Object> evidence = new LinkedHashMap<>();
        List<String> evidenceRowIds = new ArrayList<>();

        // 组内收入行汇总为净额，税费行汇总为预扣税，再反推出毛额。
        for (DividendCandidateRecord candidate : group) {
            totalConfidence = totalConfidence.add(nullSafe(candidate.getConfidence()));
            reasons.addAll(candidate.getReasons());
            if (candidate.getRowId() != null) {
                evidenceRowIds.add(candidate.getRowId());
            }
            evidence.put(candidate.getRowId(), candidate.getEvidence());
            if ("DIVIDEND_TAX".equalsIgnoreCase(candidate.getCategory())) {
                withholdingTax = add(withholdingTax, abs(candidate.getAmount()));
            } else {
                netAmount = add(netAmount, abs(candidate.getAmount()));
            }
        }

        record.setNetAmount(netAmount);
        record.setWithholdingTax(withholdingTax);
        record.setGrossAmount(calculateGross(netAmount, withholdingTax));
        record.setConfidence(totalConfidence
                .divide(BigDecimal.valueOf(group.size()), 4, RoundingMode.HALF_UP));
        record.setReasons(reasons.stream().distinct().toList());
        record.setEvidenceRowIds(evidenceRowIds);
        record.setEvidence(evidence);
        if (StrUtil.isBlank(record.getPayer())) {
            // 付款方缺失时，优先从同组其他候选回填，再退化到摘要字段，保证结果可读。
            record.setPayer(inferPayer(group));
        }
        return record;
    }

    private String inferPayer(List<DividendCandidateRecord> group) {
        return group.stream()
                .map(DividendCandidateRecord::getPayer)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(group.getFirst().getSummary());
    }

    private String groupKey(DividendCandidateRecord candidate) {
        return String.join("|",
                StrUtil.blankToDefault(candidate.getDividendDate(), ""),
                StrUtil.blankToDefault(candidate.getPayer(), ""),
                StrUtil.blankToDefault(candidate.getCurrency(), ""));
    }

    private BigDecimal calculateGross(BigDecimal netAmount, BigDecimal withholdingTax) {
        if (netAmount == null && withholdingTax == null) {
            return null;
        }
        return add(netAmount, withholdingTax);
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }

    private BigDecimal abs(BigDecimal value) {
        return value == null ? null : value.abs();
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
