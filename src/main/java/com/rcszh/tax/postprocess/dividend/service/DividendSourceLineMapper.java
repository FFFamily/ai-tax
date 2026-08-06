package com.rcszh.tax.postprocess.dividend.service;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.ir.DataRow;
import com.rcszh.tax.ir.DataTable;
import com.rcszh.tax.ir.ParsedDocument;
import com.rcszh.tax.postprocess.dividend.model.DividendSourceLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** 将无业务语义的表格行转换为股息候选召回所需的标准流水。 */
@Component
public class DividendSourceLineMapper {
    private static final String[] TRADE_DATE_HEADERS = {"tradeDate", "transactiondate", "date", "tradedate", "valuedate", "交易日期", "日期", "入账日期", "记账日期"};
    private static final String[] POST_DATE_HEADERS = {"postDate", "bookingdate", "postdate", "settlementdate", "记账日期", "入账日期", "到账日期"};
    private static final String[] SUMMARY_HEADERS = {"summary", "description", "narrative", "remark", "memo", "摘要", "备注", "交易摘要", "说明"};
    private static final String[] COUNTERPARTY_HEADERS = {"counterparty", "payee", "payer", "name", "对方户名", "对方名称", "付款方", "收款方"};
    private static final String[] CREDIT_HEADERS = {"credit", "deposit", "income", "receipt", "收入", "贷方", "存入"};
    private static final String[] DEBIT_HEADERS = {"debit", "withdrawal", "payment", "expense", "支出", "借方", "取出"};
    private static final String[] AMOUNT_HEADERS = {"amount", "transactionamount", "发生额", "金额", "变动金额", "成交金额"};
    private static final String[] BALANCE_HEADERS = {"balance", "余额", "结余"};
    private static final String[] CURRENCY_HEADERS = {"currency", "ccy", "币种", "currencycode"};

    public List<DividendSourceLine> map(ParsedDocument document) {
        if (document == null || document.getTables() == null || document.getTables().isEmpty()) {
            return List.of();
        }
        List<DividendSourceLine> result = new ArrayList<>();
        for (DataTable table : document.getTables()) {
            result.addAll(mapTable(table));
        }
        return result;
    }

    private List<DividendSourceLine> mapTable(DataTable table) {
        if (table == null || table.getHeaders() == null || table.getRows() == null) {
            return List.of();
        }
        List<DividendSourceLine> result = new ArrayList<>();
        for (DataRow row : table.getRows()) {
            if (row == null) {
                continue;
            }
            Map<String, String> rowData = toRowData(table.getHeaders(), row.getCells());
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("page", table.getPageIndex());
            evidence.put("tableIndex", table.getTableIndex());
            evidence.put("rowIndex", row.getRowIndex());
            evidence.put("headers", table.getHeaders());
            evidence.put("cells", row.getCells());
            if (StrUtil.isNotBlank(table.getTitle()) && "excel".equalsIgnoreCase(table.getSourceType())) {
                evidence.put("sheetName", table.getTitle());
            }
            result.add(buildLine(table, row, rowData, evidence));
        }
        return result;
    }

    private Map<String, String> toRowData(List<String> headers, List<Object> cells) {
        Map<String, String> result = new LinkedHashMap<>();
        List<Object> sourceCells = cells == null ? List.of() : cells;
        for (int index = 0; index < headers.size(); index++) {
            Object value = index < sourceCells.size() ? sourceCells.get(index) : "";
            result.put(headers.get(index), value == null ? "" : value.toString());
        }
        return result;
    }

    private DividendSourceLine buildLine(DataTable table,
                                         DataRow row,
                                         Map<String, String> rowData,
                                         Map<String, Object> evidence) {
        DividendSourceLine line = new DividendSourceLine();
        line.setRowId(table.getTableId() + ":r" + safeNumber(row.getRowIndex()));
        line.setPageIndex(table.getPageIndex());
        line.setSourceType(table.getSourceType());
        line.setSourceTitle(table.getTitle());
        line.setTradeDate(findValue(rowData, TRADE_DATE_HEADERS));
        line.setPostDate(findValue(rowData, POST_DATE_HEADERS));
        line.setSummary(findValue(rowData, SUMMARY_HEADERS));
        line.setCounterparty(findValue(rowData, COUNTERPARTY_HEADERS));
        line.setCurrency(resolveCurrency(rowData));
        line.setBalance(parseDecimal(findValue(rowData, BALANCE_HEADERS)));
        resolveAmountAndDirection(line, rowData);
        line.setRawText(rowData.values().stream()
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" | ")));
        line.getRawData().putAll(rowData);
        line.getEvidence().putAll(evidence);
        return line;
    }

    private void resolveAmountAndDirection(DividendSourceLine line, Map<String, String> rowData) {
        BigDecimal credit = parseDecimal(findValue(rowData, CREDIT_HEADERS));
        BigDecimal debit = parseDecimal(findValue(rowData, DEBIT_HEADERS));
        BigDecimal amount = parseDecimal(findValue(rowData, AMOUNT_HEADERS));
        if (credit != null && credit.compareTo(BigDecimal.ZERO) != 0) {
            line.setDirection("CREDIT");
            line.setAmount(credit.abs());
            return;
        }
        if (debit != null && debit.compareTo(BigDecimal.ZERO) != 0) {
            line.setDirection("DEBIT");
            line.setAmount(debit.abs());
            return;
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        line.setDirection(amount.compareTo(BigDecimal.ZERO) >= 0 ? "CREDIT" : "DEBIT");
        line.setAmount(amount.abs());
    }

    private String resolveCurrency(Map<String, String> rowData) {
        String currency = findValue(rowData, CURRENCY_HEADERS);
        if (StrUtil.isNotBlank(currency)) {
            return currency.trim();
        }
        String rawText = rowData.values().stream()
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" "))
                .toUpperCase(Locale.ROOT);
        if (rawText.contains("人民币") || rawText.contains("CNY")) {
            return "CNY";
        }
        if (rawText.contains("美元") || rawText.contains("USD")) {
            return "USD";
        }
        if (rawText.contains("港币") || rawText.contains("港元") || rawText.contains("HKD") || rawText.contains("HK$")) {
            return "HKD";
        }
        return null;
    }

    private String findValue(Map<String, String> rowData, String[] candidates) {
        for (Map.Entry<String, String> entry : rowData.entrySet()) {
            if (matchesAnyHeader(entry.getKey(), candidates) && StrUtil.isNotBlank(entry.getValue())) {
                return entry.getValue().trim();
            }
        }
        return null;
    }

    private boolean matchesAnyHeader(String header, String[] candidates) {
        String normalizedHeader = normalizeKey(header);
        if (normalizedHeader.isBlank()) {
            return false;
        }
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeKey(candidate);
            if (normalizedHeader.equals(normalizedCandidate)
                    || normalizedHeader.contains(normalizedCandidate)
                    || normalizedCandidate.contains(normalizedHeader)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal parseDecimal(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String normalized = value.trim()
                .replace("HK$", "")
                .replace(",", "")
                .replace("，", "")
                .replace("¥", "")
                .replace("$", "")
                .replace("USD", "")
                .replace("CNY", "")
                .replace("HKD", "")
                .replace("人民币", "")
                .replace("港币", "")
                .replace("港元", "");
        if (normalized.isBlank()) {
            return null;
        }
        boolean negative = normalized.startsWith("(") && normalized.endsWith(")");
        normalized = normalized.replace("(", "").replace(")", "").replace("+", "");
        try {
            BigDecimal decimal = new BigDecimal(normalized);
            return negative ? decimal.negate() : decimal;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.replaceAll("[\\s_\\-:/\\\\()\\[\\]{}]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private int safeNumber(Integer value) {
        return value == null ? 0 : value;
    }
}
