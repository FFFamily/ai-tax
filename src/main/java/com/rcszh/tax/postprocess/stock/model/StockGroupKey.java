package com.rcszh.tax.postprocess.stock.model;

/**
 * 加权平均单价的分组维度：同一账户/同一标的/同一币种/同一来源地分别维护一套持仓台账。
 */
public record StockGroupKey(
        /*
         * 账户标识（来自 records；用于把不同账户的持仓分开计算）。
         */
        String account,
        /*
         * 股票/证券标识（例如 symbol/股票代码/证券代码；用于把不同标的的持仓分开计算）。
         */
        String symbol
) {
}
