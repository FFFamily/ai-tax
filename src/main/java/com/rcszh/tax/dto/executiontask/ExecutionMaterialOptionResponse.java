package com.rcszh.tax.dto.executiontask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "执行任务材料选项")
public class ExecutionMaterialOptionResponse {
    @Schema(description = "材料类型稳定编码", example = "SALARY_PAYMENT_DETAIL")
    private String code;

    @Schema(description = "材料中文名称", example = "工资发放明细表")
    private String label;
}
