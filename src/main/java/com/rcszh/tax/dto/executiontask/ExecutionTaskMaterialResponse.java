package com.rcszh.tax.dto.executiontask;

import lombok.Data;

import java.util.List;

/** 执行任务材料及已上传文件。 */
@Data
public class ExecutionTaskMaterialResponse {
    /** 材料类型稳定编码，例如 SALARY_PAYMENT_DETAIL。 */
    private String code;

    /** 材料中文名称。 */
    private String label;

    /** 是否为该所得类型的预期材料。 */
    private boolean required;

    /** 该材料是否已经上传至少一个文件。 */
    private boolean uploaded;

    /** 该材料下已上传的文件，同类材料允许多个文件。 */
    private List<ExecutionTaskFileResponse> files;
}
