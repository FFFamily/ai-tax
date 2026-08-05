package com.rcszh.tax.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HtmlTable {
    // 标题
    private String title;
    // 表格头
    private List<String> head;
    // 表格内容
    private List<List<Object>> items;
    // 所在页码
    private Integer pageIdx;

    /**
     * 构造方法
     */
    public HtmlTable() {
        head = new ArrayList<>();
        items = new ArrayList<>();
    }
}
