package com.rcszh.tax.dto.executiontask;

import lombok.Data;

import java.util.List;

/** 用户执行任务分页结果。 */
@Data
public class ExecutionTaskPageResponse {
    /** 当前页任务摘要。 */
    private List<ExecutionTaskSummaryResponse> items;

    /** 任务总数。 */
    private long total;

    /** 当前页码。 */
    private int page;

    /** 每页数量。 */
    private int size;
}
