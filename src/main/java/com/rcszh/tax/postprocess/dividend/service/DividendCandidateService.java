package com.rcszh.tax.postprocess.dividend.service;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.ir.TransactionLine;
import com.rcszh.tax.postprocess.dividend.model.DividendCandidateRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 从标准化交易流水中召回疑似股息记录的规则服务。
 *
 * <p>该阶段以高召回为目标，通过关键词、收支方向和入账语义累计置信分，
 * 达到阈值的流水会转换为 {@link DividendCandidateRecord}。</p>
 */
@Component
public class DividendCandidateService {
    /** 表示股息收入业务的中英文关键词。 */
    private static final List<String> DIVIDEND_KEYWORDS = List.of(
            "股息", "红利", "分红", "派息", "股利", "dividend", "distribution"
    );
    /** 表示股息预扣税或相关税费的关键词。 */
    private static final List<String> TAX_KEYWORDS = List.of(
            "股息税", "红利税", "withholding", "tax", "预扣税"
    );
    /** 用于增强收入方向判断的入账语义词。 */
    private static final List<String> INCOME_HINTS = List.of(
            "入账", "收入", "派发", "到账", "credit", "received"
    );

    /**
     * 扫描交易流水并收集达到候选阈值的记录。
     *
     * @param lines 上游标准化后的交易流水
     * @return 按输入顺序返回的股息候选列表；无输入时返回空列表
     */
    public List<DividendCandidateRecord> collectCandidates(List<TransactionLine> lines) {
        // result 保留输入顺序，便于后续证据追溯和稳定聚合。
        List<DividendCandidateRecord> result = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return result;
        }
        // 这一层的目标是“高召回找疑似分红行”，宁可多找一些，也不在这里过早过滤掉候选。
        for (TransactionLine line : lines) {
            DividendCandidateRecord record = evaluateLine(line);
            if (record != null) {
                result.add(record);
            }
        }
        return result;
    }

    /**
     * 对单条流水进行规则评分，并构造候选记录。
     *
     * @param line 待评估的标准化流水
     * @return 分数不低于 0.45 的候选记录，否则返回 {@code null}
     */
    private DividendCandidateRecord evaluateLine(TransactionLine line) {
        if (line == null) {
            return null;
        }
        String normalizedSummary = normalize(line.getSummary());
        String normalizedRawText = normalize(line.getRawText());
        // reasons 记录每项加分依据，作为候选可解释性信息传递到抽取结果。
        List<String> reasons = new ArrayList<>();
        // score 为规则置信分，输出前限制在 [0, 1] 范围并保留四位小数。
        BigDecimal score = BigDecimal.ZERO;

        // 分数由关键词、税费语义、收支方向和入账语义共同组成，方便后续解释候选来源。
        if (containsAny(normalizedSummary, DIVIDEND_KEYWORDS) || containsAny(normalizedRawText, DIVIDEND_KEYWORDS)) {
            score = score.add(new BigDecimal("0.60"));
            reasons.add("命中股息关键词");
        }
        if (containsAny(normalizedSummary, TAX_KEYWORDS) || containsAny(normalizedRawText, TAX_KEYWORDS)) {
            score = score.add(new BigDecimal("0.15"));
            reasons.add("命中税费关键词");
        }
        if ("CREDIT".equalsIgnoreCase(line.getDirection())) {
            score = score.add(new BigDecimal("0.15"));
            reasons.add("交易方向为收入");
        }
        if (containsAny(normalizedSummary, INCOME_HINTS) || containsAny(normalizedRawText, INCOME_HINTS)) {
            score = score.add(new BigDecimal("0.10"));
            reasons.add("命中入账语义");
        }
        if (score.compareTo(new BigDecimal("0.45")) < 0) {
            return null;
        }

        DividendCandidateRecord record = new DividendCandidateRecord();
        record.setRowId(line.getRowId());
        record.setDividendDate(StrUtil.blankToDefault(line.getTradeDate(), line.getPostDate()));
        record.setPayer(line.getCounterparty());
        record.setSummary(line.getSummary());
        record.setCurrency(line.getCurrency());
        record.setDirection(line.getDirection());
        record.setAmount(line.getAmount());
        record.setBalance(line.getBalance());
        record.setCategory(resolveCategory(normalizedSummary, normalizedRawText));
        record.setConfidence(score.min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP));
        record.setReasons(reasons);
        record.getEvidence().putAll(line.getEvidence());
        record.getRawData().putAll(line.getRawData());
        return record;
    }

    /**
     * 将候选流水区分为股息收入或股息税费。
     *
     * @param normalizedSummary 已标准化摘要
     * @param normalizedRawText 已标准化原始文本
     * @return {@code DIVIDEND_TAX} 或 {@code DIVIDEND_INCOME}
     */
    private String resolveCategory(String normalizedSummary, String normalizedRawText) {
        // 先把税费行和实际入账行拆开，后续分组时才能正确还原净额、税额和毛额。
        if (containsAny(normalizedSummary, TAX_KEYWORDS) || containsAny(normalizedRawText, TAX_KEYWORDS)) {
            return "DIVIDEND_TAX";
        }
        return "DIVIDEND_INCOME";
    }

    /**
     * 判断文本是否包含任一关键词，比较前会使用相同规则标准化关键词。
     *
     * @param value 已标准化或待比较文本
     * @param keywords 关键词集合
     * @return 命中任一关键词时返回 {@code true}
     */
    private boolean containsAny(String value, List<String> keywords) {
        if (StrUtil.isBlank(value) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(normalize(keyword))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将文本转为小写并移除常见分隔符，降低格式差异对关键词匹配的影响。
     *
     * @param value 原始文本
     * @return 标准化文本，输入为 {@code null} 时返回空字符串
     */
    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-:/\\\\()\\[\\]{}]+", "");
    }
}
