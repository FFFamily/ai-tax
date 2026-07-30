package com.rcszh.tax.postprocess.dividend;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.ir.TransactionLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DividendCandidateService {
    private static final List<String> DIVIDEND_KEYWORDS = List.of(
            "股息", "红利", "分红", "派息", "股利", "dividend", "distribution"
    );
    private static final List<String> TAX_KEYWORDS = List.of(
            "股息税", "红利税", "withholding", "tax", "预扣税"
    );
    private static final List<String> INCOME_HINTS = List.of(
            "入账", "收入", "派发", "到账", "credit", "received"
    );

    public List<DividendCandidateRecord> collectCandidates(List<TransactionLine> lines) {
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

    private DividendCandidateRecord evaluateLine(TransactionLine line) {
        if (line == null) {
            return null;
        }
        String normalizedSummary = normalize(line.getSummary());
        String normalizedRawText = normalize(line.getRawText());
        List<String> reasons = new ArrayList<>();
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

    private String resolveCategory(String normalizedSummary, String normalizedRawText) {
        // 先把税费行和实际入账行拆开，后续分组时才能正确还原净额、税额和毛额。
        if (containsAny(normalizedSummary, TAX_KEYWORDS) || containsAny(normalizedRawText, TAX_KEYWORDS)) {
            return "DIVIDEND_TAX";
        }
        return "DIVIDEND_INCOME";
    }

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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-:/\\\\()\\[\\]{}]+", "");
    }
}
