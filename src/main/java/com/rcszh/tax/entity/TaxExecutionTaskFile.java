package com.rcszh.tax.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_execution_task_file")
public class TaxExecutionTaskFile {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String executionTaskId;
    private String materialType;
    private String originalFileName;
    private String storagePath;
    private String contentType;
    private String extension;
    private Long sizeBytes;
    private String parseTaskItemId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
