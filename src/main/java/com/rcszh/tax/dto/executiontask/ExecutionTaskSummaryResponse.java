package com.rcszh.tax.dto.executiontask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "用户执行任务摘要")
public class ExecutionTaskSummaryResponse {
    @Schema(description = "用户执行任务 ID")
    private String id;

    @Schema(description = "所得类型编码", example = "SALARY")
    private String incomeType;

    @Schema(description = "所得类型中文名称", example = "工资薪金所得")
    private String incomeTypeLabel;

    @Schema(description = "任务状态编码", allowableValues = {"COLLECTING", "PROCESSING", "COMPLETED", "FAILED"})
    private String status;

    @Schema(description = "任务状态中文名称", example = "材料收集中")
    private String statusLabel;

    @Schema(description = "关联的内部解析任务 ID，尚未提交时为空")
    private String parseTaskId;

    @Schema(description = "该所得类型的预期材料种类数", example = "3")
    private int expectedMaterialCount;

    @Schema(description = "已上传文件的材料种类数", example = "2")
    private int uploadedMaterialCount;

    @Schema(description = "尚未上传文件的材料种类数", example = "1")
    private int missingMaterialCount;

    @Schema(description = "任务下已上传文件总数", example = "4")
    private int fileCount;

    @Schema(description = "任务创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "任务最后更新时间")
    private LocalDateTime updatedAt;
}
