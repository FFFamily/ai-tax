package com.rcszh.tax.util.export;

import lombok.Data;

@Data
public class StyleConfig {
    // 是否加粗
    private boolean bold = false;
    // 字体大小
    private short fontSize = 11;
    // 字体名称
    private String fontName = "宋体";
    // 对齐方式 LEFT, CENTER, RIGHT
    private String alignment = "LEFT";
    // 对齐方式 TOP, CENTER, BOTTOM
    private String verticalAlignment = "CENTER";
    // 是否边框
    private boolean isBorder = true;

}
