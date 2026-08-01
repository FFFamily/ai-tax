package com.rcszh.tax.entity.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_task_item")
public class TaxTaskItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long documentId;
    private String requestedDocumentType;
    private Long resolvedDocumentId;
    private String routeVariant;
    private java.math.BigDecimal routeConfidence;
    private String routeReason;
    private Boolean needHumanReview;
    private String remoteTaskId;
    private String taskResult;
    private String fileUrl;
    private String parseStatus;
    private String changeResult;
    private String tableResult;
    private String fileRule;
    private String reviewReasons;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
