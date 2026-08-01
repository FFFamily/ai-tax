package com.rcszh.tax.postprocess.stock.model;

/**
 * 加权平均单价的分组维度：同一账户和同一标的维护一套持仓台账。
 */
public record StockGroupKey(
        /** 账户标识（来自 records；用于把不同账户的持仓分开计算）。 */
        String account,
        /** 股票/证券标识（用于把不同标的的持仓分开计算）。 */
        String symbol
) {
}
