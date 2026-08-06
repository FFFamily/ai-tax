package com.rcszh.tax.ir;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** PDF 等文档中的非表格文本块。 */
@Data
public class TextBlock {
    private String blockId;
    private String sourceType;
    private String type;
    private Integer pageIndex;
    private Integer blockIndex;
    private String text;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
