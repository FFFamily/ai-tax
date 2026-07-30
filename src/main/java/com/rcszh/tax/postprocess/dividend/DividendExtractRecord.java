package com.rcszh.tax.postprocess.dividend;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DividendExtractRecord {
    private String dividendDate;
    private String payer;
    private String currency;
    private BigDecimal netAmount;
    private BigDecimal withholdingTax;
    private BigDecimal grossAmount;
    private BigDecimal confidence;
    private String category;
    private String summary;
    private List<String> evidenceRowIds = new ArrayList<>();
    private List<String> reasons = new ArrayList<>();
    private List<String> qualityWarnings = new ArrayList<>();
    private Boolean needHumanReview;
    private Map<String, Object> evidence = new LinkedHashMap<>();

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dividendDate", dividendDate);
        map.put("payer", payer);
        map.put("currency", currency);
        map.put("netAmount", netAmount);
        map.put("withholdingTax", withholdingTax);
        map.put("grossAmount", grossAmount);
        map.put("confidence", confidence);
        map.put("category", category);
        map.put("summary", summary);
        map.put("evidenceRowIds", evidenceRowIds);
        map.put("reasons", reasons);
        map.put("qualityWarnings", qualityWarnings);
        map.put("needHumanReview", needHumanReview);
        map.put("evidence", evidence);
        return map;
    }
}
