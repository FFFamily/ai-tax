package com.rcszh.tax.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class AIParseResult {
    // AI 调用日志
    private Map<String,Object> globalParam;
    private List<Map<String, Object>> records;
    private List<Map<String, Object>> errorRecords;
    private List<String> warnings;
    private List<String> errors;

    public AIParseResult() {
        globalParam = new HashMap<>();
        records = new ArrayList<>();
        errorRecords = new ArrayList<>();
        warnings = new ArrayList<>();
        errors = new ArrayList<>();
    }
}
