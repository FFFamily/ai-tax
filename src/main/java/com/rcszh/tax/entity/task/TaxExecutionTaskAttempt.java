package com.rcszh.tax.entity.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_execution_task_attempt")
public class TaxExecutionTaskAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long executionTaskId;
    private Long parseTaskId;
    private Integer attemptNo;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
