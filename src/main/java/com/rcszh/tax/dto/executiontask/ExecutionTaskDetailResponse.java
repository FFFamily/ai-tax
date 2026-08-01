package com.rcszh.tax.dto.executiontask;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/** 用户执行任务详情。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionTaskDetailResponse extends ExecutionTaskSummaryResponse {
    /** 按预期顺序排列的材料清单。 */
    private List<ExecutionTaskMaterialResponse> materials;

    /** 尚未上传文件的材料。 */
    private List<ExecutionMaterialOptionResponse> missingMaterials;

    /** 是否所有预期材料均已上传。 */
    private boolean complete;

    /** 任务提交处理时间，材料收集阶段为空。 */
    private LocalDateTime submittedAt;

    /** 处理失败原因，非失败状态为空。 */
    private String errorMessage;

    /** 按尝试序号倒序排列的解析历史。 */
    private List<ExecutionTaskAttemptResponse> attempts;
}
