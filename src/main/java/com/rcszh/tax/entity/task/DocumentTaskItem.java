package com.rcszh.tax.entity.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rcszh.tax.dto.executiontask.ExecutionTaskRouteSummaryResponse;
import com.rcszh.tax.ir.DocumentFeatures;
import com.rcszh.tax.ir.TransactionLine;
import lombok.Data;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

@Data
public class DocumentTaskItem {
    private Long id;

    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("workflow_code")
    private String workflowCode;

    @JsonProperty("route_variant")
    private String routeVariant;

    @JsonProperty("route_confidence")
    private BigDecimal routeConfidence;

    @JsonProperty("route_reason")
    private String routeReason;

    @JsonProperty("need_human_review")
    private Boolean needHumanReview;

    @JsonProperty("remote_task_id")
    private String remoteTaskId;

    @JsonProperty("task_result")
    private String taskResult;

    @JsonProperty("file_url")
    private String fileUrl;

    @JsonProperty("parse_status")
    private String parseStatus;

    @JsonProperty("change_result")
    private String changeResult;

    @JsonProperty("table_result")
    private String tableResult;

    @JsonProperty("review_reasons")
    private String reviewReasons;

    private String reviewer;

    @JsonProperty("review_comment")
    private String reviewComment;

    @JsonProperty("route_summary")
    private ExecutionTaskRouteSummaryResponse routeSummary;

    @JsonIgnore
    private List<TransactionLine> preparedTransactionLines = List.of();

    @JsonIgnore
    private DocumentFeatures preparedDocumentFeatures;

    @JsonIgnore
    private Path localFilePath;
}
