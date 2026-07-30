package com.rcszh.tax.dto.executiontask;

import lombok.Data;

/** 执行任务材料选项。 */
@Data
public class ExecutionMaterialOptionResponse {
    /** 材料类型稳定编码，例如 SALARY_PAYMENT_DETAIL。 */
    private String code;

    /** 材料中文名称，例如工资发放明细表。 */
    private String label;
}
