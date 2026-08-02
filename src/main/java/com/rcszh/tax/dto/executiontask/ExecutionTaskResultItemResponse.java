package com.rcszh.tax.dto.executiontask;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

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

    /** 路由变体。 */
    @JsonProperty("route_variant")
    private String routeVariant;

    /** 路由置信度。 */
    @JsonProperty("route_confidence")
    private BigDecimal routeConfidence;

    /** 路由原因原文。 */
    @JsonProperty("route_reason")
    private String routeReason;

    /** 是否需要人工复核。 */
    @JsonProperty("need_human_review")
    private Boolean needHumanReview;

    /** PDF 或图片对应的远程解析任务 ID。 */
    @JsonProperty("remote_task_id")
    private String remoteTaskId;

    /** 文档原始解析结果 JSON。 */
    @JsonProperty("task_result")
    private String taskResult;

    /** 待解析文件地址。 */
    @JsonProperty("file_url")
    private String fileUrl;

    /** 文件解析状态。 */
    @JsonProperty("parse_status")
    private String parseStatus;

    /** 结构化处理结果 JSON。 */
    @JsonProperty("change_result")
    private String changeResult;

    /** 表格解析结果 JSON。 */
    @JsonProperty("table_result")
    private String tableResult;

    /** 人工复核原因。 */
    @JsonProperty("review_reasons")
    private String reviewReasons;

    /** 文档路由摘要。 */
    @JsonProperty("route_summary")
    private ExecutionTaskRouteSummaryResponse routeSummary;
}
