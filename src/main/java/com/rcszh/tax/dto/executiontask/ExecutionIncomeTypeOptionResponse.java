package com.rcszh.tax.dto.executiontask;

import lombok.Data;

import java.util.List;

/** 境外所得类型及其预期材料。 */
@Data
public class ExecutionIncomeTypeOptionResponse {
    /** 所得类型稳定编码，例如 SALARY。 */
    private String code;

    /** 所得类型中文名称，例如工资薪金所得。 */
    private String label;

    /** 该所得类型对应的预期材料。 */
    private List<ExecutionMaterialOptionResponse> materials;
}
