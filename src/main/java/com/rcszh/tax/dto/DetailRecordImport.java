package com.rcszh.tax.dto;

import lombok.Data;

@Data
public class DetailRecordImport {
    private String account;
    private String accountType;
    private Integer year;
    private String currency;
    private String date;
    private String summary;
    private String deposit;
    private String withdrawal;
    private String balance;
    private String category;

}
