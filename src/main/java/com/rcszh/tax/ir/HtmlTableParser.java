package com.rcszh.tax.ir;

import com.rcszh.tax.dto.HtmlTable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 将 MinerU 返回的 HTML 表格转换为内部表格模型。 */
@Component
public class HtmlTableParser {
    public HtmlTable parse(String html) {
        Document document = Jsoup.parse(html);
        Element table = document.selectFirst("table");
        if (table == null) {
            throw new IllegalArgumentException("HTML 中没有找到 <table>");
        }
        Elements rows = table.select("tr");
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("HTML 表格中没有数据行");
        }

        HtmlTable result = new HtmlTable();
        Elements headCells = rows.getFirst().select("th,td");
        result.setHead(headCells.stream().map(cell -> cell.text().trim()).toList());
        for (int index = 1; index < rows.size(); index++) {
            result.getItems().add(new ArrayList<>());
        }
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            Elements cells = rows.get(rowIndex).select("td");
            List<Object> currentRow = result.getItems().get(rowIndex - 1);
            for (Element cell : cells) {
                String value = cell.text().trim();
                int rowspan = parseRowspan(cell.attr("rowspan"));
                for (int offset = 1; offset < rowspan && rowIndex - 1 + offset < result.getItems().size(); offset++) {
                    result.getItems().get(rowIndex - 1 + offset).add(value);
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
