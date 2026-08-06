package com.rcszh.tax.parser;

import com.rcszh.tax.ir.DataRow;
import com.rcszh.tax.ir.DataTable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.List;

/** 将 MinerU 返回的 HTML 表格转换为内部表格模型。 */
@Component
public class HtmlTableParser {
    public DataTable parse(String html) {
        Document document = Jsoup.parse(html);
        Element table = document.selectFirst("table");
        if (table == null) {
            throw new IllegalArgumentException("HTML 中没有找到 <table>");
        }
        Elements rows = table.select("tr");
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("HTML 表格中没有数据行");
        }

        DataTable result = new DataTable();
        Elements headCells = rows.getFirst().select("th,td");
        result.setHeaders(headCells.stream().map(cell -> cell.text().trim()).toList());
        for (int index = 1; index < rows.size(); index++) {
            DataRow row = new DataRow();
            row.setRowIndex(index);
            result.getRows().add(row);
        }
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            Elements cells = rows.get(rowIndex).select("td");
            List<Object> currentRow = result.getRows().get(rowIndex - 1).getCells();
            for (Element cell : cells) {
                String value = cell.text().trim();
                int rowspan = parseRowspan(cell.attr("rowspan"));
                for (int offset = 1; offset < rowspan && rowIndex - 1 + offset < result.getRows().size(); offset++) {
                    result.getRows().get(rowIndex - 1 + offset).getCells().add(value);
                }
                currentRow.add(value);
            }
        }
        return result;
    }

    private int parseRowspan(String value) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        try {
            return Math.max(Integer.parseInt(value.replace("\\\"", "")), 1);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
