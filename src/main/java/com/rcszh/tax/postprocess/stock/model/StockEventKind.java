package com.rcszh.tax.postprocess.stock.model;

/**
 * 股票二次加工事件类型。
 */
public enum StockEventKind {
    /**
     * 买入（增加持仓数量与成本）。
     */
    BUY,
    /**
     * 卖出（减少持仓数量，并按卖出前均价结转卖出成本/财产原值）。
     */
    SELL,
    /**
     * 拆分/合股（按比例调整持仓数量；持仓总成本不变；均价随之变化）。
     */
    SPLIT,
    /**
     * 股息/红利（形成单独的股息申报记录，不影响持仓成本口径可按需扩展）。
     */
    DIVIDEND,
    /**
     * 无法识别的事件类型（将产生 warnings，并在处理时忽略或降级处理）。
     */
    UNKNOWN
}
