package com.rcszh.tax.controller.ai;

import com.rcszh.tax.common.ApiResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ai/chat")
public class AiChatController {

    private static final String SYSTEM_PROMPT = """
            你是境外所得税务材料助手。当用户不确定所得类型时，通过简短、清晰的追问帮助其描述收入来源。
            优先询问付款方、收入产生原因、资产或服务类型等关键信息。回答应简洁，一次最多追问两个问题。
            不要声称已完成申报或给出最终税务结论；信息足够时，可以告知用户最可能对应的所得类型。
            """;

    private final ChatModel chatModel;

    public AiChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @PostMapping("/query")
    public ApiResponse<String> chat(@Valid @RequestBody ChatRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        if (request.history() != null) {
            request.history().forEach(item -> {
                if ("assistant".equalsIgnoreCase(item.role())) {
                    messages.add(AiMessage.from(item.content()));
                } else if ("user".equalsIgnoreCase(item.role())) {
                    messages.add(UserMessage.from(item.content()));
                }
            });
        }
        messages.add(UserMessage.from(request.message().trim()));
        String response = chatModel.chat(messages).aiMessage().text();
        return ApiResponse.success(response == null ? "" : response.trim());
    }

    public record ChatRequest(
            @NotBlank(message = "请输入需要咨询的内容")
            @Size(max = 2000, message = "单条消息不能超过 2000 个字符")
            String message,
            @Size(max = 20, message = "对话上下文不能超过 20 条")
            List<@Valid HistoryMessage> history) {
    }

    public record HistoryMessage(
            @NotBlank(message = "对话角色不能为空") String role,
            @NotBlank(message = "对话内容不能为空")
            @Size(max = 4000, message = "历史消息不能超过 4000 个字符") String content) {
    }
}
