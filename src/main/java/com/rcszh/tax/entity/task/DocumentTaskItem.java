package com.rcszh.tax.entity.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rcszh.tax.ir.TransactionLine;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
public class DocumentTaskItem {
    private Long id;

    @JsonProperty("task_id")
    private Long taskId;

    @JsonProperty("workflow_code")
    private String workflowCode;

    @JsonProperty("need_human_review")
    private Boolean needHumanReview;

    @JsonProperty("task_result")
    private String taskResult;

    @JsonProperty("file_url")
    private String fileUrl;

    @JsonProperty("change_result")
    private String changeResult;

    @JsonProperty("review_reasons")
    private String reviewReasons;

    private String reviewer;

    @JsonProperty("review_comment")
    private String reviewComment;

    @JsonIgnore
    private List<TransactionLine> preparedTransactionLines = List.of();

    @JsonIgnore
    private Path localFilePath;
}
