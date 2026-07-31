package com.rcszh.tax.entity.document;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_document_config")
public class DocumentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private String titleFilter;
    private String tableHeadCheckRule;
    private Integer sortNum;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
