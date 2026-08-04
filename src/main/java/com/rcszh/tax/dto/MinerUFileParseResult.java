package com.rcszh.tax.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MinerUFileParseResult extends BaseParseResult {
    // 类型
    private String type;
    // 文本内容
    private String text;
    // 表格标题
    private String table_caption;
    // 表格主体
    private String table_body;
    // 页码
    private Integer page_idx;
}
