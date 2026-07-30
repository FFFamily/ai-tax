package com.rcszh.tax.dto.result;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FileTaskResult {
    // 总数
    private Integer total;
    // 成功数量
    private Integer success;
    // 部分成功数量
    private Integer partialSuccess;
    // 失败数量
    private Integer fail;
    // 解析文档结果
    private List<DocumentResult> result;

    public FileTaskResult() {
        total = 0;
        success = 0;
        partialSuccess = 0;
        fail = 0;
        result = new ArrayList<>();
    }
}
