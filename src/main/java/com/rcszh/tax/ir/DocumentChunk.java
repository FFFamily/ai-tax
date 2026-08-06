package com.rcszh.tax.ir;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 发送给 AI 的统一文档分片。 */
@Data
public class DocumentChunk {
    private Integer chunkIndex;
    private List<DataTable> tables = new ArrayList<>();
    private List<TextBlock> textBlocks = new ArrayList<>();

    public boolean isEmpty() {
        return tables.isEmpty() && textBlocks.isEmpty();
    }
}
