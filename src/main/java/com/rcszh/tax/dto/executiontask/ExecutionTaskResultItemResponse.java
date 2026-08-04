package com.rcszh.tax.dto.executiontask;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 内部解析任务项结果。 */
@Data
public class ExecutionTaskResultItemResponse {
    /** 内部解析任务项 ID。 */
    private Long id;

    /** 内部解析任务 ID。 */
    @JsonProperty("task_id")
    private Long taskId;

    /** 固定流程编码。 */
    @JsonProperty("workflow_code")
    private String workflowCode;

    /** 是否需要人工复核。 */
    @JsonProperty("need_human_review")
    private Boolean needHumanReview;

    /** 待解析文件地址。 */
    @JsonProperty("file_url")
    private String fileUrl;

    /** 结构化处理结果 JSON。 */
    @JsonProperty("change_result")
    private String changeResult;

    /** 人工复核原因。 */
    @JsonProperty("review_reasons")
    private String reviewReasons;

    /** 最近一次人工复核人。 */
    private String reviewer;

    /** 最近一次人工复核意见。 */
    @JsonProperty("review_comment")
    private String reviewComment;

}
