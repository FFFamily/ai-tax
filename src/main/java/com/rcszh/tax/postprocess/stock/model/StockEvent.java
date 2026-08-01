package com.rcszh.tax.postprocess.stock.model;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.postprocess.RecordValueUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * AI 抽取的原始 record 规范化后的股票事件。
 */
public record StockEvent(
        /** 原始 record Map（用于追溯/告警摘要；不建议下游直接依赖其字段）。 */
        Map<String, Object> raw,
        /** 成交/发生日期（用于排序与按时间推进持仓台账）。 */
        LocalDate tradeDate,
        /** 事件类型（买入/卖出/拆股/股息等）。 */
        StockEventKind kind,
        /** 股票/证券标识（symbol/股票代码/证券代码）。 */
        String symbol,
        /** 币种（用于折算/归集；本任务不做 FX，但保留币种字段给下游）。 */
        String currency,
        /** 账户标识（用于把不同账户分开计算均价与持仓）。 */
        String account,
        /** 数量（股）；上游以负数表示卖出时保留原值，由卖出处理取绝对值。 */
        BigDecimal qty,
        /** 成交均价；缺失时只能输出卖出成本，无法计算卖出额和收益亏损。 */
        BigDecimal price,
        /** 交易发生额，可用于直接计算扣除成本后的收益。 */
        BigDecimal transactionAmount
) {
    /**
     * 生成分组 key：同一账户和同一标的维护一套持仓台账。
     *
     * @return 当前事件对应的持仓分组键
     */
    public StockGroupKey groupKey() {
        return new StockGroupKey(account, symbol);
    }

    /**
     * 将 AI 抽取的原始 record Map 解析成标准化的 StockEvent。
     * 解析过程包含：日期/数量/价格/拆股比例/股息金额等字段的容错取值与转换。
     *
     * @param r AI 抽取的原始股票记录
     * @return 标准股票事件；缺少股票代码时返回 {@code null}
     */
    public static StockEvent from(Map<String, Object> r) {
        // 股票
        String symbol = RecordValueUtil.firstString(r, "code");
        if (StrUtil.isBlank(symbol)) return null;
        // 交易日期
        LocalDate tradeDate = RecordValueUtil.firstLocalDate(r, "date");
        // 方向/动作
        String action = RecordValueUtil.firstString(r, "direction");
        StockEventKind kind = parseKind(action);
        // 成交数量
        BigDecimal qty = RecordValueUtil.firstDecimal(r, "count");
        // 单价
        BigDecimal price = RecordValueUtil.firstDecimal(r, "price", "tradePrice", "成交均价", "卖出均价", "买入均价", "单价", "实际卖出单价", "实际买入单价");
        // 币种
        String currency = RecordValueUtil.firstString(r, "currency", "币种");
        // 账户
        String account = RecordValueUtil.firstString(r, "account", "accountId", "账户", "账户号");
        // 发生额
        BigDecimal transactionAmount = RecordValueUtil.firstDecimal(r, "transactionAmount");
        return new StockEvent(r, tradeDate, kind, symbol, currency, account, qty, price,transactionAmount);
    }

    /**
     * 将“动作/描述”归一为事件类型（兼容中英关键字与 "4:1" 这类拆股表达）。
     *
     * @param action 原始交易动作或摘要
     * @return 归一化事件类型，无法识别时返回 {@link StockEventKind#UNKNOWN}
     */
    private static StockEventKind parseKind(String action) {

        if (StrUtil.isBlank(action)) return StockEventKind.UNKNOWN;
        String a = action.toLowerCase(Locale.ROOT);
        if (a.contains("buy") || action.contains("买") || action.contains("申购")) return StockEventKind.BUY;
        if (a.contains("sell") || action.contains("卖") || action.contains("赎回")) return StockEventKind.SELL;
        if (a.contains("dividend") || action.contains("股息") || action.contains("红利")) return StockEventKind.DIVIDEND;
        return StockEventKind.UNKNOWN;
    }
}
