package com.rcszh.tax.dto.executiontask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "境外所得类型及其预期材料")
public class ExecutionIncomeTypeOptionResponse {
    @Schema(description = "所得类型稳定编码", example = "SALARY")
    private String code;

    @Schema(description = "所得类型中文名称", example = "工资薪金所得")
    private String label;

    @Schema(description = "该所得类型对应的预期材料")
    private List<ExecutionMaterialOptionResponse> materials;
}
