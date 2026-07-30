package com.rcszh.tax.dto;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public static List<DetailRecord> covert(HtmlTable htmlTable) {
        if (!htmlTable.getIsSuccess()) {
            return new ArrayList<>();
        }
        List<DetailRecord> list = new ArrayList<>();
        String title = htmlTable.getTitle();
        String accountType = "";
        String account = "";
        if (StrUtil.isNotBlank(title)) {
//            int start = account.indexOf("\"");
            int end = title.indexOf("(");
            if (end > 0) {
                accountType = title.substring(0, end - 1).trim();
            }
            int lastIndex = title.lastIndexOf(")");
            if (end > 0 && lastIndex > 0) {
                account = title.substring(end + 1, lastIndex - 1).trim();
            }
        }

        int yearIdx;
        Integer dateIdx = null;
        Integer summaryIdx = null;
        Integer depositIdx = null;
        Integer withdrawalIdx = null;
        Integer balanceIdx = null;
        List<String> head = htmlTable.getHead();
        for (int i = 0; i < head.size(); i++) {
            if (head.get(i).equals("日期")) {
                dateIdx = i;
            } else if (head.get(i).equals("交易摘要")) {
                summaryIdx = i;
            } else if (head.get(i).equals("存入")) {
                depositIdx = i;
            } else if (head.get(i).equals("提取")) {
                withdrawalIdx = i;
            } else if (head.get(i).equals("原幣結餘")) {
                balanceIdx = i;
            }
        }

        for (List<Object> item : htmlTable.getItems()) {
            DetailRecord detailRecord = new DetailRecord();
            detailRecord.setAccount(account);
            detailRecord.setAccountType(accountType);
            int size = item.size();
            Optional.ofNullable(dateIdx).ifPresent(idx -> {
                if (idx < 0 || idx >= size) {
                    return;
                }
                detailRecord.setDate(item.get(idx).toString());
            });
            Optional.ofNullable(summaryIdx).ifPresent(idx -> {
                if (idx < 0 || idx >= size) {
                    return;
                }
                detailRecord.setSummary(item.get(idx).toString());
            });
            Optional.ofNullable(depositIdx).ifPresent(idx -> {
                if (idx < 0 || idx >= size) {
                    return;
                }
                detailRecord.setDeposit(item.get(idx).toString());
            });
            Optional.ofNullable(withdrawalIdx).ifPresent(idx -> {
                if (idx < 0 || idx >= size) {
                    return;
                }
                detailRecord.setWithdrawal(item.get(idx).toString());
            });
            Optional.ofNullable(balanceIdx).ifPresent(idx -> {
                if (idx < 0 || idx >= size) {
                    return;
                }
                detailRecord.setBalance(item.get(idx).toString());
            });
            Optional.ofNullable(detailRecord.getDate()).ifPresent(date -> {
                try {
                    detailRecord.setYear(DateUtil.parse(date).year());
                } catch (Exception e) {

                }
            });

            list.add(detailRecord);
        }
        return list;
    }

    public static void main(String[] args) {
        String str = "[\"ada(dasdas)";
        System.out.println(str.substring(str.indexOf("\"") + 1, str.indexOf("("))
                .trim());
    }
}
