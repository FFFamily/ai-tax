package com.rcszh.tax.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_execution_task")
public class TaxExecutionTask {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String incomeType;
    private String status;
    private String parseTaskId;
    private LocalDateTime submittedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
