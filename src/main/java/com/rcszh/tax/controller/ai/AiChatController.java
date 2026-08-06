package com.rcszh.tax.controller.ai;

import com.rcszh.tax.common.CommonPrompt;
import com.rcszh.tax.entity.ai.ChatRequest;
import com.rcszh.tax.entity.ai.HistoryMessage;
import com.rcszh.tax.service.knowledge.MarkdownKnowledgeService;
import com.rcszh.tax.common.ApiResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.Resource;
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
    @Resource
    private  ChatModel chatModel;
    @Resource
    private  MarkdownKnowledgeService markdownKnowledgeService;

    @PostMapping("/query")
    public ApiResponse<String> chat(@Valid @RequestBody ChatRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(buildSystemPrompt(buildRetrievalQuery(request))));
        if (request.getHistory() != null) {
            request.getHistory().forEach(item -> {
                if ("assistant".equalsIgnoreCase(item.getRole())) {
                    messages.add(AiMessage.from(item.getContent()));
                } else if ("user".equalsIgnoreCase(item.getRole())) {
                    messages.add(UserMessage.from(item.getContent()));
                }
            });
        }
        messages.add(UserMessage.from(request.getMessage().trim()));
        String response = chatModel.chat(messages).aiMessage().text();
        return ApiResponse.success(response == null ? "" : response.trim());
    }

    String buildSystemPrompt(String query) {
        List<MarkdownKnowledgeService.KnowledgeSegment> segments = markdownKnowledgeService.retrieve(query);
        if (segments.isEmpty()) {
            return CommonPrompt.SYSTEM_PROMPT;
        }
        StringBuilder knowledge = new StringBuilder();
        for (MarkdownKnowledgeService.KnowledgeSegment segment : segments) {
            knowledge.append("\n\n### 资料：")
                    .append(segment.source())
                    .append("\n\n")
                    .append(segment.text());
        }
        return CommonPrompt.SYSTEM_PROMPT + CommonPrompt.KNOWLEDGE_INSTRUCTION + knowledge.toString().strip()
                + "\n===== 本地参考资料结束 =====";
    }

    private String buildRetrievalQuery(ChatRequest request) {
        StringBuilder query = new StringBuilder();
        if (request.getHistory() != null) {
            List<HistoryMessage> recentUserMessages = request.getHistory().stream()
                    .filter(item -> "user".equalsIgnoreCase(item.getRole()))
                    .toList();
            recentUserMessages.stream()
                    .skip(Math.max(0, recentUserMessages.size() - 2))
                    .forEach(item -> query.append(item.getContent()).append('\n'));
        }
        return query.append(request.getMessage().trim()).toString();
    }

}
