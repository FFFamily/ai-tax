package com.rcszh.tax.dto.executiontask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户执行任务详情")
public class ExecutionTaskDetailResponse extends ExecutionTaskSummaryResponse {
    @Schema(description = "按预期顺序排列的材料清单")
    private List<ExecutionTaskMaterialResponse> materials;

    @Schema(description = "尚未上传文件的材料")
    private List<ExecutionMaterialOptionResponse> missingMaterials;

    @Schema(description = "是否所有预期材料均已上传", example = "false")
    private boolean complete;

    @Schema(description = "任务提交处理时间，材料收集阶段为空")
    private LocalDateTime submittedAt;

    @Schema(description = "处理失败原因，非失败状态为空")
    private String errorMessage;
}
