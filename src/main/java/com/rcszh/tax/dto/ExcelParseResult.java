package com.rcszh.tax.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ExcelParseResult {
    private Map<String,String> excelData;
    private Integer rowIndex;
    private String sheetName;
}
