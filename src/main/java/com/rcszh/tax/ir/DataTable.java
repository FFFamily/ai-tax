package com.rcszh.tax.ir;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与文件格式和税务业务无关的表格中间表示。
 *
 * <p>PDF 表格和 Excel Sheet 都转换为该结构后再进入 AI 与专项处理阶段。</p>
 */
@Data
public class DataTable {
    private String tableId;
    private String sourceType;
    private String title;
    private Integer pageIndex;
    private Integer tableIndex;
    private Integer blockIndex;
    private List<String> headers = new ArrayList<>();
    private List<DataRow> rows = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
