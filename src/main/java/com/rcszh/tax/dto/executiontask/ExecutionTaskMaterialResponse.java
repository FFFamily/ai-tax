package com.rcszh.tax.dto.executiontask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "执行任务材料及已上传文件")
public class ExecutionTaskMaterialResponse {
    @Schema(description = "材料类型稳定编码", example = "SALARY_PAYMENT_DETAIL")
    private String code;

    @Schema(description = "材料中文名称", example = "工资发放明细表")
    private String label;

    @Schema(description = "是否为该所得类型的预期材料", example = "true")
    private boolean required;

    @Schema(description = "该材料是否已经上传至少一个文件", example = "true")
    private boolean uploaded;

    @Schema(description = "该材料下已上传的文件，同类材料允许多个文件")
    private List<ExecutionTaskFileResponse> files;
}
