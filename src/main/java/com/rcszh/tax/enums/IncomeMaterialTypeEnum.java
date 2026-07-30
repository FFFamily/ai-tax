package com.rcszh.tax.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum IncomeMaterialTypeEnum {
    SALARY_PAYMENT_DETAIL("工资发放明细表", null),
    SALARY_BANK_PROOF("工资发放银行流水单或系统截图", "BANK_STATEMENT"),
    LABOR_PAYMENT_DETAIL("劳务报酬发放明细表", null),
    LABOR_BANK_PROOF("劳务报酬发放银行流水单或系统截图", "BANK_STATEMENT"),
    AUTHOR_PAYMENT_DETAIL("稿酬发放明细表", null),
    AUTHOR_BANK_PROOF("稿酬发放银行流水单或系统截图", "BANK_STATEMENT"),
    ROYALTY_PAYMENT_DETAIL("特许权使用费发放明细表", null),
    ROYALTY_BANK_PROOF("特许权使用费发放银行流水单或系统截图", "BANK_STATEMENT"),
    FOREIGN_TAX_PAYMENT_PROOF("境外完税证明/税收缴款书/纳税记录", null),
    LABOR_INVOICE_OR_BILL("劳务报酬相关发票或账单", null),
    AUTHOR_INVOICE_OR_BILL("稿酬相关发票或账单", null),
    ROYALTY_INVOICE_OR_BILL("特许权使用费相关发票或账单", null),
    LABOR_CONTRACT("劳务报酬相关合同", null),
    AUTHOR_CONTRACT("稿酬相关合同", null),
    ROYALTY_CONTRACT("特许权使用费相关合同", null),
    BUSINESS_FINANCIAL_STATEMENTS("境外各国经营相关财务报表", null),
    BUSINESS_FOREIGN_TAX_PAYMENT_PROOF("境外各国经营相关的完税证明/税收缴款书/纳税记录", null),
    BUSINESS_CARRYFORWARD_LOSS_DOCUMENTS("境外各国以前年度结转的亏损资料", null),
    INTEREST_DETAIL("利息明细表", null),
    BANK_STATEMENT("银行流水单", "BANK_STATEMENT"),
    DIVIDEND_DETAIL("股息、红利所得明细表", "DIVIDEND"),
    BROKER_DAILY_STATEMENT("证券账户日结单", "BROKER_STATEMENT"),
    PROPERTY_RENTAL_CONTRACT("财产租赁合同", null),
    OVERSEAS_REAL_ESTATE_TRANSFER_CONTRACT("转让境外不动产合同", null),
    OVERSEAS_REAL_ESTATE_TRANSFER_INVOICE_OR_BILL("转让境外不动产发票或账单", null),
    OVERSEAS_REAL_ESTATE_ACQUISITION_CONTRACT("取得境外不动产相关合同", null),
    OVERSEAS_REAL_ESTATE_ACQUISITION_INVOICE_OR_BILL("取得境外不动产发票或账单", null),
    STOCK_TRANSACTION_DETAIL("股票交易明细表", "BROKER_STATEMENT"),
    OVERSEAS_EQUITY_TRANSFER_CONTRACT("转让境外股权合同", null),
    OVERSEAS_EQUITY_ACQUISITION_CONTRACT("取得境外股权相关合同", null),
    EQUITY_TRANSFER_BANK_STATEMENT("转让股权相关的银行流水单", "BANK_STATEMENT"),
    EQUITY_ACQUISITION_BANK_STATEMENT("取得股权相关的银行流水单", "BANK_STATEMENT"),
    OTHER_EQUITY_ASSET_TRANSFER_CONTRACT("转让境外其他权益性资产合同", null),
    OTHER_EQUITY_ASSET_ACQUISITION_CONTRACT("取得境外其他权益性资产相关合同", null),
    OTHER_EQUITY_ASSET_TRANSFER_BANK_STATEMENT("转让其他权益性资产相关的银行流水单", "BANK_STATEMENT"),
    OTHER_EQUITY_ASSET_ACQUISITION_BANK_STATEMENT("取得其他权益性资产相关的银行流水单", "BANK_STATEMENT"),
    OTHER_ASSET_TRANSFER_CONTRACT("转让境外其他资产合同", null),
    OTHER_ASSET_ACQUISITION_CONTRACT("取得境外资产相关合同", null),
    OTHER_ASSET_TRANSFER_BANK_STATEMENT("转让其他资产相关的银行流水单", "BANK_STATEMENT"),
    OTHER_ASSET_ACQUISITION_BANK_STATEMENT("取得其他资产相关的银行流水单", "BANK_STATEMENT"),
    RELATED_BUSINESS_CONTRACT("相关业务合同", null);

    private final String label;
    private final String requestedDocumentType;

    public static IncomeMaterialTypeEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的材料类型: " + code));
    }
}
