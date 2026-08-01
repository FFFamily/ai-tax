package com.rcszh.tax.postprocess.dividend.service;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.postprocess.dividend.model.DividendCandidateRecord;
import com.rcszh.tax.postprocess.dividend.model.DividendExtractRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将股息候选流水聚合为专项股息记录的领域服务。
 *
 * <p>先按日期、付款方和币种进行确定性规则聚合，再调用 AI 增强服务补齐或纠偏；
 * AI 不可用或调用失败时仍返回规则结果。</p>
 */
@Component
public class DividendExtractService {
    /** 对规则抽取结果进行可选补齐的 AI 增强服务。 */
    @Resource
    private DividendAiEnhanceService dividendAiEnhanceService;

    /**
     * 聚合候选记录并执行可选 AI 增强。
     *
     * @param candidates 候选召回阶段产生的股息收入/税费记录
     * @return 专项股息记录；候选为空时返回不可变空列表
     */
    public List<DividendExtractRecord> extract(List<DividendCandidateRecord> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        // grouped 使用 LinkedHashMap 保持分组首次出现顺序，使输出稳定且便于追溯。
        Map<String, List<DividendCandidateRecord>> grouped = new LinkedHashMap<>();
        // 候选记录先按日期/付款方/币种聚合，尽量把“分红入账 + 预扣税”还原为一笔业务事件。
        for (DividendCandidateRecord candidate : candidates) {
            grouped.computeIfAbsent(groupKey(candidate), key -> new ArrayList<>()).add(candidate);
        }
        // result 是规则聚合基线，即使 AI 增强失败也会原样返回。
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

    /**
     * 将同一股息业务事件的候选行合并为一条专项记录。
     *
     * @param group 日期、付款方和币种相同的候选记录组
     * @return 聚合后的股息记录；分组为空时返回 {@code null}
     */
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

        /** 组内股息收入行绝对值之和。 */
        BigDecimal netAmount = null;
        /** 组内股息税费行绝对值之和。 */
        BigDecimal withholdingTax = null;
        /** 组内候选置信分总和，用于计算平均置信度。 */
        BigDecimal totalConfidence = BigDecimal.ZERO;
        /** 合并后的候选命中原因。 */
        List<String> reasons = new ArrayList<>();
        /** 以流水行号为键保存原始证据，便于审计追溯。 */
        Map<String, Object> evidence = new LinkedHashMap<>();
        /** 参与当前聚合结果的流水行号。 */
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

    /**
     * 从同组候选中推断付款方，均缺失时退化为首条摘要。
     *
     * @param group 候选记录组
     * @return 可用付款方或首条记录摘要
     */
    private String inferPayer(List<DividendCandidateRecord> group) {
        return group.stream()
                .map(DividendCandidateRecord::getPayer)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(group.getFirst().getSummary());
    }

    /**
     * 构造股息业务聚合键。
     *
     * @param candidate 股息候选记录
     * @return 由日期、付款方和币种拼接的分组键
     */
    private String groupKey(DividendCandidateRecord candidate) {
        return String.join("|",
                StrUtil.blankToDefault(candidate.getDividendDate(), ""),
                StrUtil.blankToDefault(candidate.getPayer(), ""),
                StrUtil.blankToDefault(candidate.getCurrency(), ""));
    }

    /**
     * 根据净额和预扣税计算股息毛额。
     *
     * @param netAmount 股息净额
     * @param withholdingTax 预扣税额
     * @return 两者之和；两者均为空时返回 {@code null}
     */
    private BigDecimal calculateGross(BigDecimal netAmount, BigDecimal withholdingTax) {
        if (netAmount == null && withholdingTax == null) {
            return null;
        }
        return add(netAmount, withholdingTax);
    }

    /**
     * 对两个可空金额求和。
     *
     * @param left 左操作数
     * @param right 右操作数
     * @return 非空金额之和；仅一项非空时直接返回该项
     */
    private BigDecimal add(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }

    /** @return 金额绝对值，输入为空时返回 {@code null} */
    private BigDecimal abs(BigDecimal value) {
        return value == null ? null : value.abs();
    }

    /** @return 原金额，输入为空时返回零 */
    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
