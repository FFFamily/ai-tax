package com.rcszh.tax.entity.ai;

import lombok.Data;

@Data
public class AiMessage {
    // 文本聊天消息、需要用户操作消息
    private String type;
    private String message;
}
