package com.rcszh.tax.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_task")
public class TaxTask {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
