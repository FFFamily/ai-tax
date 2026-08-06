package com.rcszh.tax.postprocess.dividend.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 股息专项处理中由通用表格行归一化得到的来源流水。 */
@Data
public class DividendSourceLine {
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
