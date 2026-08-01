package com.rcszh.tax.dto.executiontask;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户执行任务的一次解析尝试。 */
@Data
public class ExecutionTaskAttemptResponse {
    /** 尝试序号，从 1 开始。 */
    private Integer attemptNo;

    /** 本次尝试创建的内部解析任务 ID。 */
    private Long parseTaskId;

    /** 状态编码：PROCESSING、COMPLETED 或 FAILED。 */
    private String status;

    /** 状态中文名称。 */
    private String statusLabel;

    /** 失败原因，非失败状态为空。 */
    private String errorMessage;

    /** 尝试开始时间。 */
    private LocalDateTime startedAt;

    /** 尝试结束时间，处理中为空。 */
    private LocalDateTime finishedAt;
}
