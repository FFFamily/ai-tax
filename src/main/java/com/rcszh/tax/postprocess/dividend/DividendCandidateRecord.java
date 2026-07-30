package com.rcszh.tax.postprocess.dividend;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DividendCandidateRecord {
    private String rowId;
    private String dividendDate;
    private String payer;
    private String summary;
    private String currency;
    private String direction;
    private BigDecimal amount;
    private BigDecimal balance;
    private BigDecimal confidence;
    private String category;
    private List<String> reasons = new ArrayList<>();
    private Map<String, Object> evidence = new LinkedHashMap<>();
    private Map<String, Object> rawData = new LinkedHashMap<>();

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rowId", rowId);
        map.put("dividendDate", dividendDate);
        map.put("payer", payer);
        map.put("summary", summary);
        map.put("currency", currency);
        map.put("direction", direction);
        map.put("amount", amount);
        map.put("balance", balance);
        map.put("confidence", confidence);
        map.put("category", category);
        map.put("reasons", reasons);
        map.put("evidence", evidence);
        map.put("rawData", rawData);
        return map;
    }
}
