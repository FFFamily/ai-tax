package com.rcszh.tax.postprocess.dividend.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选召回阶段输出的疑似股息流水。
 *
 * <p>该对象既保存标准化业务字段，也保留命中原因、原始数据和证据，作为候选处理器与
 * 专项抽取处理器之间的数据载体。</p>
 */
@Data
public class DividendCandidateRecord {
    /** 上游交易流水的稳定行标识，用于关联原始证据。 */
    private String rowId;
    /** 股息发生日期，优先取交易日期，缺失时取入账日期。 */
    private String dividendDate;
    /** 股息付款方或交易对手名称。 */
    private String payer;
    /** 原始交易摘要。 */
    private String summary;
    /** 交易币种。 */
    private String currency;
    /** 收支方向，如 {@code CREDIT} 或 {@code DEBIT}。 */
    private String direction;
    /** 当前流水发生金额；抽取阶段会按类别取绝对值汇总。 */
    private BigDecimal amount;
    /** 当前流水发生后的账户余额。 */
    private BigDecimal balance;
    /** 规则召回置信分，范围为 0 到 1。 */
    private BigDecimal confidence;
    /** 候选类别：{@code DIVIDEND_INCOME} 或 {@code DIVIDEND_TAX}。 */
    private String category;
    /** 该流水被召回为候选的规则命中原因。 */
    private List<String> reasons = new ArrayList<>();
    /** 与当前流水相关的结构化证据信息。 */
    private Map<String, Object> evidence = new LinkedHashMap<>();
    /** 上游流水携带的原始字段，用于复核和追溯。 */
    private Map<String, Object> rawData = new LinkedHashMap<>();

    /**
     * 转换为通用 Map，供日志和诊断输出使用。
     *
     * @return 按固定字段顺序生成的候选记录 Map
     */
    public Map<String, Object> toMap() {
        // LinkedHashMap 保持稳定字段顺序，便于日志阅读和 AI 请求复现。
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rowId", rowId);
        map.put("dividendDate", dividendDate);
        map.put("payer", payer);
        map.put("summary", summary);
        map.put("currency", currency);
        map.put("direction", direction);
        map.put("amount", amount);
        map.put("balance", balance);
        map.put("confidence", confidence);
        map.put("category", category);
        map.put("reasons", reasons);
        map.put("evidence", evidence);
        map.put("rawData", rawData);
        return map;
    }
}
