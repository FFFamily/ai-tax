package com.rcszh.tax.util.export;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Data
public class SheetConfig {
    private String sheetName;
    // 列宽配置：Map<列索引, 宽度值(字符数 * 256)>
    // 例如：{0: 3000, 1: 5000} 对应 A列、B列宽度
    private Map<Integer, Integer> columnWidths;
    // 所有的行定义
    private List<RowConfig> rows;
    public SheetConfig(){
        this.rows = new ArrayList<>();
    }
}
