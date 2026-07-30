package com.rcszh.tax.ir;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TransactionLine {
    private String rowId;
    private Integer pageIndex;
    private String sourceType;
    private String sourceTitle;
    private String tradeDate;
    private String postDate;
    private String summary;
    private String counterparty;
    private String direction;
    private BigDecimal amount;
    private String currency;
    private BigDecimal balance;
    private String rawText;
    private Map<String, Object> rawData = new LinkedHashMap<>();
    private Map<String, Object> evidence = new LinkedHashMap<>();
}
