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
    private String workflowCode;
    private Boolean needHumanReview;
    private String taskResult;
    private String fileUrl;
    private String changeResult;
    private String reviewReasons;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
