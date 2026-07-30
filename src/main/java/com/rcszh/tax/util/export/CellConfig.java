package com.rcszh.tax.util.export;

import lombok.Data;

@Data
public class CellConfig {
    // 位置信息
    // 列号 (0-based)
    private int colIndex;
    // 跨行数 (默认1)
    private int rowSpan = 1;
    // 跨列数 (默认1)
    private int colSpan = 1;
    // 对应数据源的key
    private String sourceKey;
    // 数据源
    // "STATIC": 固定文本, "VARIABLE": 动态变量
    private String type = "STATIC";
    // 样式
    private StyleConfig style;
}
