package com.rcszh.tax.ir;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 文件解析完成后的统一、无业务语义文档模型。 */
@Data
public class ParsedDocument {
    private List<DataTable> tables = new ArrayList<>();
    private List<TextBlock> textBlocks = new ArrayList<>();
}
