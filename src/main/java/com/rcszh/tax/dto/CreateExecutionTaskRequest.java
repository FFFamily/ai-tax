package com.rcszh.tax.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 创建用户执行任务请求。 */
@Data
public class CreateExecutionTaskRequest {
    /**
     * 境外所得类型编码，创建后不可修改。
     * 具体可选值由 {@code GET /execution-tasks/options} 返回。
     */
    @NotBlank(message = "请选择境外所得类型")
    private String incomeType;
}
