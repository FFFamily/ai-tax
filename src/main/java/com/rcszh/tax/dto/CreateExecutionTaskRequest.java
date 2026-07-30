package com.rcszh.tax.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateExecutionTaskRequest {
    @NotBlank(message = "请选择境外所得类型")
    private String incomeType;
}
