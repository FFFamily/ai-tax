package com.rcszh.tax.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_document")
public class MaterialDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String variant;
    private String filterType;
    private String pageType;
    private Integer pageStep;
    private String matchRule;
    private String prompt;
    private String globalPrompt;
    private String errorRecord;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
