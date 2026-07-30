package com.rcszh.tax.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_review_learning")
public class ReviewLearning {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String taskId;
    private String taskItemId;
    private String requestedDocumentType;
    private String resolvedDocumentId;
    private String routeSummary;
    private String reviewReasons;
    private String reviewedRecords;
    private String reviewer;
    private String comment;
    private String suggestedMatchRule;
    private String fewShotExample;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
