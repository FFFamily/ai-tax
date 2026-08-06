package com.rcszh.tax.parser;

import com.rcszh.tax.ir.DataTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlTableParserTest {
    private final HtmlTableParser parser = new HtmlTableParser();

    @Test
    void parsesHeadersRowsAndSourceRowIndexes() {
        DataTable table = parser.parse("""
                <table>
                  <tr><th>Date</th><th>Amount</th></tr>
                  <tr><td>2026-01-01</td><td>100</td></tr>
                  <tr><td>2026-01-02</td><td>200</td></tr>
                </table>
                """);

        assertEquals(java.util.List.of("Date", "Amount"), table.getHeaders());
        assertEquals(2, table.getRows().size());
        assertEquals(1, table.getRows().getFirst().getRowIndex());
        assertEquals(java.util.List.of("2026-01-01", "100"), table.getRows().getFirst().getCells());
    }
}
