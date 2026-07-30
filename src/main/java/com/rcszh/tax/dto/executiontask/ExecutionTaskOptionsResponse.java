package com.rcszh.tax.dto.executiontask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "执行任务创建选项和文件上传限制")
public class ExecutionTaskOptionsResponse {
    @Schema(description = "可选择的境外所得类型")
    private List<ExecutionIncomeTypeOptionResponse> incomeTypes;

    @Schema(description = "允许上传的文件扩展名", example = "[\"pdf\",\"xlsx\",\"png\"]")
    private List<String> allowedExtensions;

    @Schema(description = "单个文件最大字节数", example = "52428800")
    private long maxFileSize;
}
