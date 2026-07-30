package com.rcszh.tax.dto.executiontask;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "内部解析任务项结果")
public class ExecutionTaskResultItemResponse {
    @Schema(description = "内部解析任务项 ID")
    private String id;

    @JsonProperty("task_id")
    @Schema(description = "内部解析任务 ID")
    private String taskId;

    @JsonProperty("document_id")
    @Schema(description = "显式指定的文档模板 ID")
    private String documentId;

    @JsonProperty("requested_document_type")
    @Schema(description = "请求的文档类型")
    private String requestedDocumentType;

    @JsonProperty("resolved_document_id")
    @Schema(description = "路由后采用的文档模板 ID")
    private String resolvedDocumentId;

    @JsonProperty("route_variant")
    @Schema(description = "路由变体")
    private String routeVariant;

    @JsonProperty("route_confidence")
    @Schema(description = "路由置信度")
    private BigDecimal routeConfidence;

    @JsonProperty("route_reason")
    @Schema(description = "路由原因原文")
    private String routeReason;

    @JsonProperty("need_human_review")
    @Schema(description = "是否需要人工复核")
    private Boolean needHumanReview;

    @JsonProperty("remote_task_id")
    @Schema(description = "PDF 或图片对应的远程解析任务 ID")
    private String remoteTaskId;

    @JsonProperty("task_result")
    @Schema(description = "文档原始解析结果 JSON")
    private String taskResult;

    @JsonProperty("file_url")
    @Schema(description = "待解析文件地址")
    private String fileUrl;

    @JsonProperty("parse_status")
    @Schema(description = "文件解析状态")
    private String parseStatus;

    @JsonProperty("change_result")
    @Schema(description = "结构化处理结果 JSON")
    private String changeResult;

    @JsonProperty("table_result")
    @Schema(description = "表格解析结果 JSON")
    private String tableResult;

    @JsonProperty("file_rule")
    @Schema(description = "文件识别规则")
    private String fileRule;

    @JsonProperty("review_reasons")
    @Schema(description = "人工复核原因")
    private String reviewReasons;

    @JsonProperty("route_summary")
    @Schema(description = "文档路由摘要")
    private ExecutionTaskRouteSummaryResponse routeSummary;
}
