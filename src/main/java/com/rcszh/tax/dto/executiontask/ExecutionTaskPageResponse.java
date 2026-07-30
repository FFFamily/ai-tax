package com.rcszh.tax.dto.executiontask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户执行任务分页结果")
public class ExecutionTaskPageResponse {
    @Schema(description = "当前页任务摘要")
    private List<ExecutionTaskSummaryResponse> items;

    @Schema(description = "任务总数", example = "42")
    private long total;

    @Schema(description = "当前页码", example = "1")
    private int page;

    @Schema(description = "每页数量", example = "20")
    private int size;
}
