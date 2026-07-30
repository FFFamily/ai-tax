package com.rcszh.tax.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public enum OverseasIncomeTypeEnum {
    SALARY("工资薪金所得", List.of(
            IncomeMaterialTypeEnum.SALARY_PAYMENT_DETAIL,
            IncomeMaterialTypeEnum.SALARY_BANK_PROOF,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF
    )),
    LABOR_REMUNERATION("劳务报酬所得", List.of(
            IncomeMaterialTypeEnum.LABOR_PAYMENT_DETAIL,
            IncomeMaterialTypeEnum.LABOR_BANK_PROOF,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF,
            IncomeMaterialTypeEnum.LABOR_INVOICE_OR_BILL,
            IncomeMaterialTypeEnum.LABOR_CONTRACT
    )),
    AUTHOR_REMUNERATION("稿酬所得", List.of(
            IncomeMaterialTypeEnum.AUTHOR_PAYMENT_DETAIL,
            IncomeMaterialTypeEnum.AUTHOR_BANK_PROOF,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF,
            IncomeMaterialTypeEnum.AUTHOR_INVOICE_OR_BILL,
            IncomeMaterialTypeEnum.AUTHOR_CONTRACT
    )),
    ROYALTY("特许权使用费所得", List.of(
            IncomeMaterialTypeEnum.ROYALTY_PAYMENT_DETAIL,
            IncomeMaterialTypeEnum.ROYALTY_BANK_PROOF,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF,
            IncomeMaterialTypeEnum.ROYALTY_INVOICE_OR_BILL,
            IncomeMaterialTypeEnum.ROYALTY_CONTRACT
    )),
    BUSINESS_INCOME("境外经营所得", List.of(
            IncomeMaterialTypeEnum.BUSINESS_FINANCIAL_STATEMENTS,
            IncomeMaterialTypeEnum.BUSINESS_FOREIGN_TAX_PAYMENT_PROOF,
            IncomeMaterialTypeEnum.BUSINESS_CARRYFORWARD_LOSS_DOCUMENTS
    )),
    INTEREST_INCOME("利息所得", List.of(
            IncomeMaterialTypeEnum.INTEREST_DETAIL,
            IncomeMaterialTypeEnum.BANK_STATEMENT,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF
    )),
    DIVIDEND_INCOME("股息、红利所得", List.of(
            IncomeMaterialTypeEnum.DIVIDEND_DETAIL,
            IncomeMaterialTypeEnum.BANK_STATEMENT,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF,
            IncomeMaterialTypeEnum.BROKER_DAILY_STATEMENT
    )),
    PROPERTY_RENTAL_INCOME("财产租赁所得", List.of(
            IncomeMaterialTypeEnum.PROPERTY_RENTAL_CONTRACT,
            IncomeMaterialTypeEnum.BANK_STATEMENT,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF
    )),
    OVERSEAS_REAL_ESTATE_TRANSFER_INCOME("转让境外不动产所得", List.of(
            IncomeMaterialTypeEnum.OVERSEAS_REAL_ESTATE_TRANSFER_CONTRACT,
            IncomeMaterialTypeEnum.OVERSEAS_REAL_ESTATE_TRANSFER_INVOICE_OR_BILL,
            IncomeMaterialTypeEnum.OVERSEAS_REAL_ESTATE_ACQUISITION_CONTRACT,
            IncomeMaterialTypeEnum.OVERSEAS_REAL_ESTATE_ACQUISITION_INVOICE_OR_BILL,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF
    )),
    OVERSEAS_STOCK_TRANSFER_INCOME("转让境外股票所得", List.of(
            IncomeMaterialTypeEnum.STOCK_TRANSACTION_DETAIL,
            IncomeMaterialTypeEnum.BANK_STATEMENT,
            IncomeMaterialTypeEnum.BROKER_DAILY_STATEMENT,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF
    )),
    OVERSEAS_EQUITY_TRANSFER_INCOME("转让境外股权所得", List.of(
            IncomeMaterialTypeEnum.OVERSEAS_EQUITY_TRANSFER_CONTRACT,
            IncomeMaterialTypeEnum.OVERSEAS_EQUITY_ACQUISITION_CONTRACT,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF,
            IncomeMaterialTypeEnum.EQUITY_TRANSFER_BANK_STATEMENT,
            IncomeMaterialTypeEnum.EQUITY_ACQUISITION_BANK_STATEMENT
    )),
    OVERSEAS_OTHER_EQUITY_ASSET_TRANSFER_INCOME("转让境外其他权益性资产所得", List.of(
            IncomeMaterialTypeEnum.OTHER_EQUITY_ASSET_TRANSFER_CONTRACT,
            IncomeMaterialTypeEnum.OTHER_EQUITY_ASSET_ACQUISITION_CONTRACT,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF,
            IncomeMaterialTypeEnum.OTHER_EQUITY_ASSET_TRANSFER_BANK_STATEMENT,
            IncomeMaterialTypeEnum.OTHER_EQUITY_ASSET_ACQUISITION_BANK_STATEMENT
    )),
    OVERSEAS_OTHER_PROPERTY_TRANSFER_INCOME("转让境外其他财产所得", List.of(
            IncomeMaterialTypeEnum.OTHER_ASSET_TRANSFER_CONTRACT,
            IncomeMaterialTypeEnum.OTHER_ASSET_ACQUISITION_CONTRACT,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF,
            IncomeMaterialTypeEnum.OTHER_ASSET_TRANSFER_BANK_STATEMENT,
            IncomeMaterialTypeEnum.OTHER_ASSET_ACQUISITION_BANK_STATEMENT
    )),
    INCIDENTAL_INCOME("偶然所得", List.of(
            IncomeMaterialTypeEnum.RELATED_BUSINESS_CONTRACT,
            IncomeMaterialTypeEnum.BANK_STATEMENT,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF
    )),
    OTHER_INCOME("其他所得", List.of(
            IncomeMaterialTypeEnum.RELATED_BUSINESS_CONTRACT,
            IncomeMaterialTypeEnum.BANK_STATEMENT,
            IncomeMaterialTypeEnum.FOREIGN_TAX_PAYMENT_PROOF
    ));

    private final String label;
    private final List<IncomeMaterialTypeEnum> materials;

    public static OverseasIncomeTypeEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的境外所得类型: " + code));
    }

    public boolean supports(IncomeMaterialTypeEnum materialType) {
        return materials.contains(materialType);
    }
}
