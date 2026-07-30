package com.rcszh.tax.util.export;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
@Data
public class RowConfig {
    // 行号 (0-based)
    private Integer index;
    // 行高 (可选)
    private Short height = 400;
    // 该行包含的单元格
    private List<CellConfig> cells;
    public RowConfig(){
        this.cells = new ArrayList<>();
    }
}
