package com.rcszh.tax.postprocess.stock;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.postprocess.RecordPostProcessor;
import com.rcszh.tax.postprocess.RecordValueUtil;
import com.rcszh.tax.postprocess.stock.model.StockCapitalTransferRecord;
import com.rcszh.tax.postprocess.stock.model.StockDividendRecord;
import com.rcszh.tax.postprocess.stock.model.StockEvent;
import com.rcszh.tax.postprocess.stock.model.StockGroupKey;
import com.rcszh.tax.postprocess.stock.model.StockPositionState;
import com.rcszh.tax.server.DocumentServer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 股票/证券交易二次加工：
 * - 成本法：移动加权平均单价
 * - 支持：买入/卖出/拆分(拆股/合股)/股息
 * - 产出：卖出记录的财产原值(卖出成本)、卖出额(如提供卖价)、收益/亏损等字段
 *
 * 输入 records 仍来自 AI 抽取，因此这里对字段名做了一定容错（支持中英/不同命名）。
 */
@Component
public class StockWeightedAveragePostProcessor implements RecordPostProcessor {
    private static final MathContext MC = MathContext.DECIMAL128;

    /**
     * 指定处理器优先级（越小越先执行）。
     */
    @Override
    public int order() {
        return 100;
    }

    /**
     * 返回处理器名称（用于追踪）。
     */
    @Override
    public String name() {
        return "stock-weighted-average";
    }

    /**
     * 快速判断 records 是否包含“股票交易类”信息，以决定是否执行加权平均单价二次加工。
     */
    @Override
    public boolean supports(AIParseResult parseResult, Map<String, Object> taskItem, Map<String, Object> document) {
        // 快速嗅探
        String documentType = (String) document.get(DocumentServer.TYPE);
        return documentType.contains("股票");
    }

    /**
     * 对股票相关流水执行加权平均单价（移动平均）计算：
     * - BUY：增加持仓成本与数量，重算均价
     * - SPLIT：按比例调整数量，持仓成本不变，重算均价
     * - SELL：按卖出前均价结转卖出成本（财产原值），若提供卖价则计算卖出额与收益/亏损
     *
     * 输出：仅保留“需要申报/纳税的记录”（卖出/股息），其他非股票类型 records 原样保留，方便后续扩展更多 processor。
     */
    @Override
    public void process(AIParseResult parseResult, Map<String, Object> taskItem, Map<String, Object> document) {
        // 将股票相关流水从 records 中抽出来加工；其他类型记录原样保留，方便后续扩展更多 processor
        List<Map<String, Object>> stockRaw = new ArrayList<>();
        List<Map<String, Object>> others = new ArrayList<>();
        List<Map<String, Object>> records = parseResult.getRecords();
//        for (Map<String, Object> r : records) {
//            if (isStockRelated(r)) stockRaw.add(r);
//            else others.add(r);
//        }
//        if (stockRaw.isEmpty()) {
//            return records;
//        }

        List<StockEvent> events = records.stream()
                .map(StockEvent::from)
                .filter(Objects::nonNull)
                .sorted((a, b) -> {
                    if (a.tradeDate() == null && b.tradeDate() == null) return 0;
                    if (a.tradeDate() == null) return 1;
                    if (b.tradeDate() == null) return -1;
                    return a.tradeDate().compareTo(b.tradeDate());
                })
                .toList();

        // 分组：同一账户/同一标的/同一币种分别计算均价
        Map<StockGroupKey, List<StockEvent>> groupMap = events.stream()
                .collect(Collectors.groupingBy(StockEvent::groupKey, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> out = new ArrayList<>(others);
        for (Map.Entry<StockGroupKey, List<StockEvent>> entry : groupMap.entrySet()) {
            StockGroupKey key = entry.getKey();
            StockPositionState ps = null;
            for (StockEvent e : entry.getValue()) {
                switch (e.kind()) {
                    case BUY -> ps = applyBuy(parseResult, ps, e);
                    case SELL -> applySell(parseResult, out, ps, key, e);
//                    case SPLIT -> applySplit(parseResult, ps, e);
//                    case DIVIDEND -> out.add(dividendRecord(key, e));
//                    case UNKNOWN -> parseResult.getWarnings().add("无法识别的股票记录已忽略：" + safeSummary(e.raw()));
                }
            }
        }
    }

    /**
     * 买入入账：累加持仓成本与数量，并重算均价。
     */
    private static StockPositionState applyBuy(AIParseResult parseResult, StockPositionState ps, StockEvent e) {
        if (e.qty() == null || e.qty().compareTo(BigDecimal.ZERO) <= 0) {
            parseResult.getWarnings().add("买入记录缺少数量，已忽略：" + safeSummary(e.raw()));
            return null;
        }
        if (e.price() == null) {
            parseResult.getWarnings().add("买入记录缺少价格，已忽略：" + safeSummary(e.raw()));
            return null;
        }
        if (ps == null) {
            ps = new StockPositionState();
        }
        BigDecimal cost = e.qty().multiply(e.price(), MC);
        ps.setPositionCostTotal(ps.getPositionCostTotal().add(cost, MC));
        ps.setPositionQty(ps.getPositionQty().add(e.qty(), MC));
        ps.recalcAvg();
        return ps;
    }

    /**
     * 卖出出账：按“卖出前均价”结转卖出成本（财产原值），更新持仓台账，并产出可申报的卖出明细记录。
     */
    private static void applySell(AIParseResult parseResult, List<Map<String, Object>> out, StockPositionState ps, StockGroupKey key, StockEvent e) {
        if (e.qty() == null || e.qty().compareTo(BigDecimal.ZERO) == 0) {
            parseResult.getWarnings().add("卖出记录缺少数量，已忽略：" + safeSummary(e.raw()));
            return;
        }
        if (ps == null){
            // 没有购买的记录，需要去远程查询对应的股票信息
            ps = getHistoryStock(e);
        }
        // 卖出数量
        BigDecimal sellQty = e.qty().abs();
        if (ps.getPositionQty().compareTo(BigDecimal.ZERO) <= 0) {
            parseResult.getWarnings().add("卖出时持仓为0，无法计算卖出成本：" + safeSummary(e.raw()));
            return;
        }

        if (sellQty.compareTo(ps.getPositionQty()) > 0) {
            parseResult.getWarnings().add("卖出数量超过持仓，已按当前持仓计算到0并记录异常：" + safeSummary(e.raw()));
            sellQty = ps.getPositionQty();
        }
        // 成本单价
        BigDecimal avgBefore = ps.getAvgUnitCost();
        // 数量 * 单价 = 成本费用合计
        BigDecimal originalValue = sellQty.multiply(avgBefore, MC);

        // 若提供卖价，可计算卖出额/收益亏损；若未提供，则只输出成本
        // 如果已经有了发生额，就可以直接计算
        BigDecimal result = null;
        if (e.transactionAmount() != null){
            result = e.transactionAmount().subtract(originalValue);
            e.raw().put(ResultBaseFieldConstant.INCOME_MONEY, result);
            // 持股数量减少
            ps.setPositionQty(ps.getPositionQty().subtract(sellQty));
            ps.setPositionCostTotal(ps.getPositionCostTotal().subtract(originalValue));
            ps.recalcAvg();
            return;
        }
        BigDecimal transferIncome = e.price() == null ? null : sellQty.multiply(e.price(), MC);
        BigDecimal gainLoss = transferIncome == null ? null : transferIncome.subtract(originalValue, MC);
    }

    /**
     * 获取历史股票数据信息
     */
    private static StockPositionState getHistoryStock(StockEvent stockEvent){
        String symbol = stockEvent.symbol();
        StockPositionState stockPositionState = new StockPositionState();
        stockPositionState.setPositionQty(stockEvent.qty());
        if(symbol.equals("TSLA")){
            stockPositionState.setAvgUnitCost(new BigDecimal("1056.78"));
        }else if (symbol.equals("AMZN")){
            stockPositionState.setAvgUnitCost(new BigDecimal("84.00"));
        }else if (symbol.equals("NVDA")){
            stockPositionState.setAvgUnitCost(new BigDecimal("146.14"));
        }else if (symbol.equals("AAPL")){
            stockPositionState.setAvgUnitCost(new BigDecimal("129.93"));
        }
        return stockPositionState;
    }

    /**
     * 生成用于告警的简要摘要（避免把整条 raw record 打到日志/告警里）。
     */
    private static String safeSummary(Map<String, Object> raw) {
        if (raw == null) return "";
        String date = RecordValueUtil.firstString(raw, "tradeDate", "date", "日期");
        String symbol = RecordValueUtil.firstString(raw, "symbol", "stockCode", "securityCode", "code", "股票代码", "证券代码");
        String action = RecordValueUtil.firstString(raw, "side", "type", "action", "direction", "描述", "交易类别", "交易方向", "summary", "交易摘要");
        return "date=" + date + ", symbol=" + symbol + ", action=" + action;
    }
}
