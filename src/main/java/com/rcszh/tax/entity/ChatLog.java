package com.rcszh.tax.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("tax_chat_log")
public class ChatLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String prompt;
    private String result;
    private Integer token;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
