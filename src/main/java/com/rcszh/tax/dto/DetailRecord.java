package com.rcszh.tax.dto;

import lombok.Data;

@Data
public class DetailRecord {
    // 账户
    private String account;
    // 账户类型
    private String accountType;
    // 年份
    private Integer year;
    // 币种
    private String currency;
    // 日期
    private String date;
    // 交易摘要
    private String summary;
    // 存入
    private String deposit;
    // 取出
    private String withdrawal;
    // 余额
    private String balance;
    //所属类别
    private String category;

}
