package com.rcszh.tax.ir;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.dto.ExcelParseResult;
import com.rcszh.tax.dto.MinerUFileParseResult;
import com.rcszh.tax.parser.HtmlTableParser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将不同来源的解析结果转换成统一、无业务语义的文档模型。 */
@Component
public class ParsePreparationService {
    @Resource
    private HtmlTableParser htmlTableParser;

    public ParsedDocument preparePdf(List<MinerUFileParseResult> parseResults) {
        ParsedDocument document = new ParsedDocument();
        if (parseResults == null || parseResults.isEmpty()) {
            return document;
        }
        int tableIndex = 0;
        for (int blockIndex = 0; blockIndex < parseResults.size(); blockIndex++) {
            MinerUFileParseResult block = parseResults.get(blockIndex);
            if (block == null) {
                continue;
            }
            if ("table".equalsIgnoreCase(block.getType()) && StrUtil.isNotBlank(block.getTable_body())) {
                DataTable table = htmlTableParser.parse(block.getTable_body());
                table.setTableId("pdf:p" + safeNumber(block.getPage_idx()) + ":t" + tableIndex);
                table.setSourceType("pdf");
                table.setTitle(normalizeTitle(block.getTable_caption()));
                table.setPageIndex(block.getPage_idx());
                table.setTableIndex(tableIndex);
                table.setBlockIndex(blockIndex);
                table.getMetadata().put("caption", block.getTable_caption());
                table.getMetadata().put("sourceBlockType", block.getType());
                document.getTables().add(table);
                tableIndex++;
                continue;
            }
            if (StrUtil.isNotBlank(block.getText())) {
                TextBlock textBlock = new TextBlock();
                textBlock.setBlockId("pdf:p" + safeNumber(block.getPage_idx()) + ":b" + blockIndex);
                textBlock.setSourceType("pdf");
                textBlock.setType(block.getType());
                textBlock.setPageIndex(block.getPage_idx());
                textBlock.setBlockIndex(blockIndex);
                textBlock.setText(block.getText());
                document.getTextBlocks().add(textBlock);
            }
        }
        return document;
    }

    public ParsedDocument prepareExcel(List<ExcelParseResult> parseResults) {
        ParsedDocument document = new ParsedDocument();
        if (parseResults == null || parseResults.isEmpty()) {
            return document;
        }
        Map<String, List<ExcelParseResult>> rowsBySheet = new LinkedHashMap<>();
        for (ExcelParseResult row : parseResults) {
            if (row == null) {
                continue;
            }
            String sheetName = StrUtil.blankToDefault(row.getSheetName(), "sheet0");
            rowsBySheet.computeIfAbsent(sheetName, ignored -> new ArrayList<>()).add(row);
        }

        int tableIndex = 0;
        for (Map.Entry<String, List<ExcelParseResult>> entry : rowsBySheet.entrySet()) {
            DataTable table = buildExcelTable(entry.getKey(), entry.getValue(), tableIndex);
            document.getTables().add(table);
            tableIndex++;
        }
        return document;
    }

    private DataTable buildExcelTable(String sheetName, List<ExcelParseResult> sourceRows, int tableIndex) {
        DataTable table = new DataTable();
        table.setTableId("excel:" + sheetName);
        table.setSourceType("excel");
        table.setTitle(sheetName);
        table.setTableIndex(tableIndex);
        table.setBlockIndex(tableIndex);
        table.getMetadata().put("sheetName", sheetName);

        List<String> headers = new ArrayList<>();
        for (ExcelParseResult sourceRow : sourceRows) {
            if (sourceRow.getExcelData() == null) {
                continue;
            }
            for (String header : sourceRow.getExcelData().keySet()) {
                if (!headers.contains(header)) {
                    headers.add(header);
                }
            }
        }
        table.setHeaders(headers);
        for (ExcelParseResult sourceRow : sourceRows) {
            Map<String, String> values = sourceRow.getExcelData() == null ? Map.of() : sourceRow.getExcelData();
            DataRow row = new DataRow();
            row.setRowIndex(sourceRow.getRowIndex());
            for (String header : headers) {
                row.getCells().add(values.getOrDefault(header, ""));
            }
            table.getRows().add(row);
        }
        return table;
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

    private int safeNumber(Integer value) {
        return value == null ? 0 : value;
    }
}
