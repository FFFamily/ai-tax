package com.rcszh.tax.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_document_field_mapping")
public class DocumentFieldMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private String fieldLabel;
    private String fieldCode;
    private String fieldDesc;
    private Integer sortNum;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
