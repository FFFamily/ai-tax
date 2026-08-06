package com.rcszh.tax.ir;

import com.rcszh.tax.dto.ExcelParseResult;
import com.rcszh.tax.dto.MinerUFileParseResult;
import com.rcszh.tax.parser.HtmlTableParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParsePreparationServiceTest {
    private ParsePreparationService service;

    @BeforeEach
    void setUp() {
        service = new ParsePreparationService();
        ReflectionTestUtils.setField(service, "htmlTableParser", new HtmlTableParser());
    }

    @Test
    void preservesPdfTablesAndTextBlocksInOneDocument() {
        MinerUFileParseResult text = new MinerUFileParseResult();
        text.setType("text");
        text.setText("Account statement");
        text.setPage_idx(1);

        MinerUFileParseResult table = new MinerUFileParseResult();
        table.setType("table");
        table.setTable_caption("[\"Transactions\"]");
        table.setTable_body("<table><tr><th>Date</th><th>Amount</th></tr><tr><td>2026-01-01</td><td>10</td></tr></table>");
        table.setPage_idx(1);

        ParsedDocument document = service.preparePdf(List.of(text, table));

        assertEquals(1, document.getTextBlocks().size());
        assertEquals("Account statement", document.getTextBlocks().getFirst().getText());
        assertEquals(1, document.getTables().size());
        assertEquals("pdf:p1:t0", document.getTables().getFirst().getTableId());
        assertEquals("Transactions", document.getTables().getFirst().getTitle());
    }

    @Test
    void groupsExcelRowsBySheetAsDataTables() {
        ExcelParseResult first = excelRow("Sheet1", 1, "2026-01-01", "10");
        ExcelParseResult second = excelRow("Sheet1", 2, "2026-01-02", "20");
        ExcelParseResult third = excelRow("Sheet2", 1, "2026-02-01", "30");

        ParsedDocument document = service.prepareExcel(List.of(first, second, third));

        assertEquals(2, document.getTables().size());
        DataTable sheet1 = document.getTables().getFirst();
        assertEquals("excel:Sheet1", sheet1.getTableId());
        assertEquals(List.of("Date", "Amount"), sheet1.getHeaders());
        assertEquals(2, sheet1.getRows().size());
        assertEquals(List.of("2026-01-02", "20"), sheet1.getRows().get(1).getCells());
    }

    private ExcelParseResult excelRow(String sheet, int rowIndex, String date, String amount) {
        ExcelParseResult result = new ExcelParseResult();
        result.setSheetName(sheet);
        result.setRowIndex(rowIndex);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("Date", date);
        values.put("Amount", amount);
        result.setExcelData(values);
        return result;
    }
}
