package com.rcszh.tax.dto.executiontask;

import lombok.Data;

import java.util.List;

/** 执行任务创建选项和文件上传限制。 */
@Data
public class ExecutionTaskOptionsResponse {
    /** 可选择的境外所得类型。 */
    private List<ExecutionIncomeTypeOptionResponse> incomeTypes;

    /** 允许上传的文件扩展名，例如 pdf、xlsx、png。 */
    private List<String> allowedExtensions;

    /** 单个文件最大字节数。 */
    private long maxFileSize;
}
