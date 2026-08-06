package com.rcszh.tax.entity.ai;

import com.rcszh.tax.controller.ai.AiChatController;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;
@Data
public class ChatRequest {
    private String message;
    private List<@Valid HistoryMessage> history;
}
