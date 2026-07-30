package com.rcszh.tax.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tax_word_area")
public class WordArea {
    @TableId
    private Long id;
    private Long parentId;
    private String label;
    private String value;
}
