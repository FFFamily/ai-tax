package com.rcszh.tax.entity;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ExcelFileRule {
    // 功能区名称
    private String sheetName;
    // 功能区位置
    private Integer sheetNum;
    // 字段映射
    private Map<String,String> fieldMapping;
    /**
     * 构造方法
     */
    public ExcelFileRule(){
        this.fieldMapping = new HashMap<>();
    }
}
