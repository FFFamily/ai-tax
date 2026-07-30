package com.rcszh.tax.postprocess.stock.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 境外股息红利申报明细记录。
 */
@Data
public class StockDividendRecord {
    /**
     * 记录类型标识（用于下游区分记录类型）。
     */
    private final String recordType = "STOCK_DIVIDEND";

    /**
     * 股票/证券标识（symbol/股票代码/证券代码）。
     */
    private String symbol;
    /**
     * 币种（用于折算/归集；本任务不做 FX，但保留币种字段给下游）。
     */
    private String currency;
    /**
     * 所得来源国家（地区）（用于归集输出）。
     */
    private String countryOrRegion;
    /**
     * 账户标识（用于对齐券商账户/导出分组）。
     */
    private String account;
    /**
     * 股息/红利发生日期（建议使用 yyyy-MM-dd）。
     */
    private String date;

    /**
     * 股息/红利收入额。
     */
    private BigDecimal dividendAmount;
    /**
     * 境外预扣税/已纳税额（若券商提供）。
     */
    private BigDecimal withholdingTax;

    /**
     * 将结构化对象转换为 Map，用于回写到 AIParseResult.records。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordType", recordType);
        m.put("symbol", symbol);
        m.put("currency", currency);
        m.put("countryOrRegion", countryOrRegion);
        m.put("account", account);
        m.put("date", date);
        m.put("dividendAmount", dividendAmount);
        m.put("withholdingTax", withholdingTax);
        return m;
    }
}
