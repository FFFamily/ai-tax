package com.rcszh.tax.dto.result;

import com.rcszh.tax.dto.HtmlTable;
import lombok.Data;

import java.util.List;

/**
 * 文档解析结果
 */
@Data
public class DocumentResult {
    // 文档id
    private Long documentId;
    // 文档名称
    private String documentName;
    // 是否解析成功
    private Boolean isSuccess;
    // 解析结果
    private List<HtmlTable> result;
}
