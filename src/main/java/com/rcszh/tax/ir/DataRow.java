package com.rcszh.tax.ir;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 通用表格中的一行，保留来源行号和原始单元格。 */
@Data
public class DataRow {
    private Integer rowIndex;
    private List<Object> cells = new ArrayList<>();
}
