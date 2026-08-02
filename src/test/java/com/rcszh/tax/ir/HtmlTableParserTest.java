package com.rcszh.tax.ir;

import com.rcszh.tax.dto.HtmlTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlTableParserTest {
    private final HtmlTableParser parser = new HtmlTableParser();

    @Test
    void parsesHeaderAndRowspan() {
        HtmlTable result = parser.parse("""
                <table>
                  <tr><th>Date</th><th>Amount</th></tr>
                  <tr><td rowspan="2">2026-01-01</td><td>100</td></tr>
                  <tr><td>200</td></tr>
                </table>
                """);

        assertThat(result.getHead()).containsExactly("Date", "Amount");
        assertThat(result.getItems()).containsExactly(
                java.util.List.of("2026-01-01", "100"),
                java.util.List.of("2026-01-01", "200")
        );
    }

    @Test
    void rejectsHtmlWithoutTable() {
        assertThatThrownBy(() -> parser.parse("<p>no table</p>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有找到");
    }
}
