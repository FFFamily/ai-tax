package com.rcszh.tax.dto.executiontask;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户执行任务摘要。 */
@Data
public class ExecutionTaskSummaryResponse {
    /** 用户执行任务 ID。 */
    private String id;

    /** 所得类型编码，例如 SALARY。 */
    private String incomeType;

    /** 所得类型中文名称，例如工资薪金所得。 */
    private String incomeTypeLabel;

    /** 任务状态编码：COLLECTING、PROCESSING、COMPLETED 或 FAILED。 */
    private String status;

    /** 任务状态中文名称。 */
    private String statusLabel;

    /** 关联的内部解析任务 ID，尚未提交时为空。 */
    private String parseTaskId;

    /** 该所得类型的预期材料种类数。 */
    private int expectedMaterialCount;

    /** 已上传文件的材料种类数。 */
    private int uploadedMaterialCount;

    /** 尚未上传文件的材料种类数。 */
    private int missingMaterialCount;

    /** 任务下已上传文件总数。 */
    private int fileCount;

    /** 任务创建时间。 */
    private LocalDateTime createdAt;

    /** 任务最后更新时间。 */
    private LocalDateTime updatedAt;
}
