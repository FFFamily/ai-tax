package com.rcszh.tax.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_task_item")
public class TaxTaskItem {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String documentId;
    private String requestedDocumentType;
    private String resolvedDocumentId;
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
