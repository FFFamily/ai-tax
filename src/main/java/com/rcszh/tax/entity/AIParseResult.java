package com.rcszh.tax.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AIParseResult {
    private List<Map<String, Object>> records;
    private List<Map<String, Object>> errorRecords;
    private List<String> warnings;
    private List<String> errors;

    public AIParseResult() {
        records = new ArrayList<>();
        errorRecords = new ArrayList<>();
        warnings = new ArrayList<>();
        errors = new ArrayList<>();
    }
}
