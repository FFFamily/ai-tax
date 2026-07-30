package com.rcszh.tax.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建用户执行任务请求")
public class CreateExecutionTaskRequest {
    @NotBlank(message = "请选择境外所得类型")
    @Schema(
            description = "境外所得类型编码，创建后不可修改",
            example = "SALARY",
            allowableValues = {
                    "SALARY", "LABOR_REMUNERATION", "AUTHOR_REMUNERATION", "ROYALTY", "BUSINESS_INCOME",
                    "INTEREST_INCOME", "DIVIDEND_INCOME", "PROPERTY_RENTAL_INCOME",
                    "OVERSEAS_REAL_ESTATE_TRANSFER_INCOME", "OVERSEAS_STOCK_TRANSFER_INCOME",
                    "OVERSEAS_EQUITY_TRANSFER_INCOME", "OVERSEAS_OTHER_EQUITY_ASSET_TRANSFER_INCOME",
                    "OVERSEAS_OTHER_PROPERTY_TRANSFER_INCOME", "INCIDENTAL_INCOME", "OTHER_INCOME"
            }
    )
    private String incomeType;
}
