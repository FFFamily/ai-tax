package com.rcszh.tax.dto.executiontask;

import lombok.Data;

import java.util.List;

/** 用户执行任务关联的内部解析结果。 */
@Data
public class ExecutionTaskResultResponse {
    /** 内部解析任务 ID。 */
    private String id;

    /** 内部解析任务状态。 */
    private String status;

    /** 内部解析任务项。 */
    private List<ExecutionTaskResultItemResponse> items;
}
