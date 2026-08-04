package com.rcszh.tax.ir;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.dto.ExcelParseResult;
import com.rcszh.tax.dto.HtmlTable;
import com.rcszh.tax.dto.MinerUFileParseResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ParsePreparationService {
    private static final String[] TRADE_DATE_HEADERS = {"tradeDate", "transactiondate", "date", "tradedate", "valuedate", "交易日期", "日期", "入账日期", "记账日期"};
    private static final String[] POST_DATE_HEADERS = {"postDate", "bookingdate", "postdate", "settlementdate", "记账日期", "入账日期", "到账日期"};
    private static final String[] SUMMARY_HEADERS = {"summary", "description", "narrative", "remark", "memo", "摘要", "备注", "交易摘要", "说明"};
    private static final String[] COUNTERPARTY_HEADERS = {"counterparty", "payee", "payer", "name", "对方户名", "对方名称", "付款方", "收款方"};
    private static final String[] CREDIT_HEADERS = {"credit", "deposit", "income", "receipt", "收入", "贷方", "存入"};
    private static final String[] DEBIT_HEADERS = {"debit", "withdrawal", "payment", "expense", "支出", "借方", "取出"};
    private static final String[] AMOUNT_HEADERS = {"amount", "transactionamount", "发生额", "金额", "变动金额", "成交金额"};
    private static final String[] BALANCE_HEADERS = {"balance", "余额", "结余"};
    private static final String[] CURRENCY_HEADERS = {"currency", "ccy", "币种", "currencycode"};
    @Resource
    private HtmlTableParser htmlTableParser;

    public ParsePreparationResult preparePdf(List<MinerUFileParseResult> parseResults) {
        ParsePreparationResult result = new ParsePreparationResult();
        if (parseResults == null || parseResults.isEmpty()) {
            return result;
        }
        // PDF 先从 OCR 结果里抽取表格块，再统一转换成交易行 IR。
        List<MinerUFileParseResult> tableBlocks = parseResults.stream()
                .filter(item -> "table".equalsIgnoreCase(item.getType()))
                .filter(item -> StrUtil.isNotBlank(item.getTable_body()))
                .toList();
        List<TransactionLine> lines = new ArrayList<>();
        int tableIndex = 0;
        for (MinerUFileParseResult tableBlock : tableBlocks) {
            HtmlTable htmlTable = buildPdfTable(tableBlock);
            lines.addAll(buildLinesFromTable(htmlTable, tableIndex, "pdf"));
            tableIndex++;
        }
        result.setTransactionLines(lines);
        return result;
    }

    public ParsePreparationResult prepareExcel(List<ExcelParseResult> parseResults) {
        ParsePreparationResult result = new ParsePreparationResult();
        if (parseResults == null || parseResults.isEmpty()) {
            return result;
        }
        // Excel 天然是结构化行数据，这里直接转成统一 TransactionLine，拉平 PDF/Excel 差异。
        List<TransactionLine> lines = new ArrayList<>();
        for (ExcelParseResult parseResult : parseResults) {
            lines.add(buildLineFromMap(
                    parseResult.getExcelData(),
                    buildExcelRowId(parseResult),
                    null,
                    "excel",
                    parseResult.getSheetName(),
                    buildExcelEvidence(parseResult)
            ));
        }
        result.setTransactionLines(lines);
        return result;
    }

    private HtmlTable buildPdfTable(MinerUFileParseResult parseResult) {
        HtmlTable htmlTable = htmlTableParser.parse(parseResult.getTable_body());
        htmlTable.setTitle(normalizeTitle(parseResult.getTable_caption()));
        htmlTable.setPageIdx(parseResult.getPage_idx());
        return htmlTable;
    }

    private List<TransactionLine> buildLinesFromTable(HtmlTable table, int tableIndex, String sourceType) {
        List<TransactionLine> lines = new ArrayList<>();
        if (table == null || table.getItems() == null || table.getHead() == null) {
            return lines;
        }
        // 每一行都会保留页码、表序号、原始单元格等证据，后续可直接定位到来源行做人工核对。
        for (int rowIndex = 0; rowIndex < table.getItems().size(); rowIndex++) {
            List<Object> row = table.getItems().get(rowIndex);
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int cellIndex = 0; cellIndex < table.getHead().size(); cellIndex++) {
                String key = table.getHead().get(cellIndex);
                Object value = cellIndex < row.size() ? row.get(cellIndex) : "";
                rowMap.put(key, value == null ? "" : value.toString());
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("page", table.getPageIdx());
            evidence.put("tableIndex", tableIndex);
            evidence.put("rowIndex", rowIndex + 1);
            evidence.put("headers", table.getHead());
            evidence.put("cells", row);
            lines.add(buildLineFromMap(
                    rowMap,
                    "pdf:p" + safeNumber(table.getPageIdx()) + ":t" + tableIndex + ":r" + (rowIndex + 1),
                    table.getPageIdx(),
                    sourceType,
                    table.getTitle(),
                    evidence
            ));
        }
        return lines;
    }

    private TransactionLine buildLineFromMap(Map<String, String> rowMap,
                                             String rowId,
                                             Integer pageIndex,
                                             String sourceType,
                                             String sourceTitle,
                                             Map<String, Object> evidence) {
        // 通过表头同义词归一化，把不同银行/券商账单先转换为统一的交易行中间表示。
        Map<String, String> source = rowMap == null ? Map.of() : rowMap;
        TransactionLine line = new TransactionLine();
        line.setRowId(rowId);
        line.setPageIndex(pageIndex);
        line.setSourceType(sourceType);
        line.setSourceTitle(sourceTitle);
        line.setTradeDate(findValue(source, TRADE_DATE_HEADERS));
        line.setPostDate(findValue(source, POST_DATE_HEADERS));
        line.setSummary(findValue(source, SUMMARY_HEADERS));
        line.setCounterparty(findValue(source, COUNTERPARTY_HEADERS));
        line.setCurrency(resolveCurrency(source));
        line.setBalance(parseDecimal(findValue(source, BALANCE_HEADERS)));
        resolveAmountAndDirection(line, source);
        line.setRawText(source.values().stream()
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" | ")));
        line.getRawData().putAll(source);
        if (evidence != null) {
            line.getEvidence().putAll(evidence);
        }
        return line;
    }

    private void resolveAmountAndDirection(TransactionLine line, Map<String, String> rowMap) {
        BigDecimal credit = parseDecimal(findValue(rowMap, CREDIT_HEADERS));
        BigDecimal debit = parseDecimal(findValue(rowMap, DEBIT_HEADERS));
        BigDecimal amount = parseDecimal(findValue(rowMap, AMOUNT_HEADERS));
        // 优先使用借贷分栏；只有缺少分栏时，才回退到单金额列并用正负号推断收支方向。
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

    private Map<String, Object> buildExcelEvidence(ExcelParseResult parseResult) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sheetName", parseResult.getSheetName());
        evidence.put("rowIndex", parseResult.getRowIndex());
        evidence.put("headers", parseResult.getExcelData() == null ? List.of() : new ArrayList<>(parseResult.getExcelData().keySet()));
        evidence.put("cells", parseResult.getExcelData());
        return evidence;
    }

    private String buildExcelRowId(ExcelParseResult parseResult) {
        String sheet = StrUtil.blankToDefault(parseResult.getSheetName(), "sheet0");
        int rowIndex = parseResult.getRowIndex() == null ? 0 : parseResult.getRowIndex();
        return "excel:" + sheet + ":r" + rowIndex;
    }

    private boolean matchesAnyHeader(String header, String[] candidates) {
        String normalizedHeader = normalizeKey(header);
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

    private String resolveCurrency(Map<String, String> rowMap) {
        String currency = findValue(rowMap, CURRENCY_HEADERS);
        if (StrUtil.isNotBlank(currency)) {
            return currency.trim();
        }
        // 当表头缺失币种列时，回退到整行文本做弱识别，尽量不丢掉币种信息。
        String rawText = rowMap.values().stream()
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" "));
        if (rawText.contains("人民币") || rawText.contains("CNY")) {
            return "CNY";
        }
        if (rawText.contains("美元") || rawText.contains("USD")) {
            return "USD";
        }
        if (rawText.contains("港币") || rawText.contains("港元") || rawText.contains("HKD")) {
            return "HKD";
        }
        return null;
    }

    private String findValue(Map<String, String> rowMap, String[] candidates) {
        if (rowMap == null || rowMap.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : rowMap.entrySet()) {
            if (!matchesAnyHeader(entry.getKey(), candidates)) {
                continue;
            }
            if (StrUtil.isNotBlank(entry.getValue())) {
                return entry.getValue().trim();
            }
        }
        return null;
    }

    private BigDecimal parseDecimal(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String normalized = value.trim()
                .replace(",", "")
                .replace("，", "")
                .replace("¥", "")
                .replace("$", "")
                .replace("HK$", "")
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
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeTitle(String title) {
        if (StrUtil.isBlank(title)) {
            return "";
        }
        String normalized = title.replace("[", "").replace("]", "").trim();
        int start = normalized.indexOf('"');
        int end = normalized.lastIndexOf('"');
        if (start >= 0 && end > start) {
            return normalized.substring(start + 1, end).trim();
        }
        return normalized;
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
