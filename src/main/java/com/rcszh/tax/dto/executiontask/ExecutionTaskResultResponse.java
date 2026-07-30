package com.rcszh.tax.dto.executiontask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户执行任务关联的内部解析结果")
public class ExecutionTaskResultResponse {
    @Schema(description = "内部解析任务 ID")
    private String id;

    @Schema(description = "内部解析任务状态")
    private String status;

    @Schema(description = "内部解析任务项")
    private List<ExecutionTaskResultItemResponse> items;
}
