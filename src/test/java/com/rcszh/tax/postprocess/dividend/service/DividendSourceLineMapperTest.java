package com.rcszh.tax.postprocess.dividend.service;

import com.rcszh.tax.ir.DataRow;
import com.rcszh.tax.ir.DataTable;
import com.rcszh.tax.ir.ParsedDocument;
import com.rcszh.tax.postprocess.dividend.model.DividendSourceLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DividendSourceLineMapperTest {
    private final DividendSourceLineMapper mapper = new DividendSourceLineMapper();

    @Test
    void mapsGenericTableOnlyWhenDividendSpecialtyRequestsIt() {
        DataTable table = new DataTable();
        table.setTableId("excel:Dividend");
        table.setSourceType("excel");
        table.setTitle("Dividend");
        table.setTableIndex(0);
        table.setHeaders(List.of("日期", "摘要", "收款方", "收入", "余额"));
        DataRow row = new DataRow();
        row.setRowIndex(3);
        row.setCells(new ArrayList<>(List.of(
                "2026-01-01", "Dividend received", "Example Corp", "HK$1,234.50", "2000"
        )));
        table.getRows().add(row);
        ParsedDocument document = new ParsedDocument();
        document.getTables().add(table);

        DividendSourceLine line = mapper.map(document).getFirst();

        assertEquals("excel:Dividend:r3", line.getRowId());
        assertEquals("2026-01-01", line.getTradeDate());
        assertEquals("Example Corp", line.getCounterparty());
        assertEquals("CREDIT", line.getDirection());
        assertEquals(new BigDecimal("1234.50"), line.getAmount());
        assertEquals("HKD", line.getCurrency());
        assertEquals(3, line.getEvidence().get("rowIndex"));
    }
}
