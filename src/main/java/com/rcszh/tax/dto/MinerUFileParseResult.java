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
    // 文本层级
    private String text_level;
    // 图片路径
    private String img_path;
    // 表格标题
    private String table_caption;
    // 表格脚注
    private String table_footnote;
    // 表格主体
    private String table_body;
    // 坐标
    private String bbox;
    // 页码
    private Integer page_idx;
}
