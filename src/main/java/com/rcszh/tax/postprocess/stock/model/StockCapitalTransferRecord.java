package com.rcszh.tax.postprocess.stock.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 境外财产转让（股票卖出）申报明细记录（加权平均单价口径）。
 *
 * 注意：当前系统落库/传输结构使用 List&lt;Map&lt;String,Object&gt;&gt;，因此提供 toMap() 以便序列化。
 */
@Data
public class StockCapitalTransferRecord {
    /**
     * 记录类型标识（用于下游区分记录类型）。
     */
    private final String recordType = "STOCK_CAPITAL_TRANSFER";

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
     * 卖出日期（建议使用 yyyy-MM-dd）。
     */
    private String sellDate;

    /**
     * 卖出数量（股，正数）。
     */
    private BigDecimal sellQty;
    /**
     * 卖出成交均价（单价）。缺失时无法计算卖出额与收益/亏损。
     */
    private BigDecimal sellPrice;

    /**
     * 卖出前的加权平均单价（用于结转成本）。
     */
    private BigDecimal avgUnitCostBeforeSell;
    /**
     * 财产原值（卖出成本）= sellQty * avgUnitCostBeforeSell。
     */
    private BigDecimal originalValue;
    /**
     * 卖出成本（与 originalValue 含义相同，便于下游按不同字段名对接）。
     */
    private BigDecimal sellCost;

    /**
     * 转让收入（卖出成交额）= sellQty * sellPrice（若 sellPrice 缺失则为空）。
     */
    private BigDecimal transferIncome;
    /**
     * 卖出额（与 transferIncome 含义相同，便于下游按不同字段名对接）。
     */
    private BigDecimal sellAmount;
    /**
     * 收益/亏损 = transferIncome - sellCost（若 sellPrice 缺失则为空）。
     */
    private BigDecimal gainLoss;

    /**
     * 卖出后剩余持仓数量（股）。
     */
    private BigDecimal positionQtyAfterSell;
    /**
     * 卖出后剩余持仓总成本。
     */
    private BigDecimal positionCostTotalAfterSell;
    /**
     * 卖出后剩余持仓的加权平均单价。
     */
    private BigDecimal avgUnitCostAfterSell;

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
        m.put("sellDate", sellDate);
        m.put("sellQty", sellQty);
        m.put("sellPrice", sellPrice);
        m.put("avgUnitCostBeforeSell", avgUnitCostBeforeSell);
        m.put("originalValue", originalValue);
        m.put("sellCost", sellCost);
        m.put("transferIncome", transferIncome);
        m.put("sellAmount", sellAmount);
        m.put("gainLoss", gainLoss);
        m.put("positionQtyAfterSell", positionQtyAfterSell);
        m.put("positionCostTotalAfterSell", positionCostTotalAfterSell);
        m.put("avgUnitCostAfterSell", avgUnitCostAfterSell);
        return m;
    }
}
