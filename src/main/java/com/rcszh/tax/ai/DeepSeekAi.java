package com.rcszh.tax.ai;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.rcszh.tax.config.AppProperties;
import com.rcszh.tax.dto.BaseParseResult;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.server.AIDocumentParseServer;
import com.rcszh.tax.workflow.DocumentWorkflow;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeepSeekAi extends AiManage {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekAi.class);

    @Resource
    private AppProperties appProperties;

    @Override
    public AIParseResult chat(List<? extends BaseParseResult> array,
                              String prompt,
                              String agentCall,
                              DocumentWorkflow workflow) {
        OpenAiApi deepSeekApi = OpenAiApi.builder()
                .apiKey(appProperties.getAi().getDeepseekApiKey())
                .baseUrl(appProperties.getAi().getDeepseekBaseUrl())
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(deepSeekApi)
                .defaultOptions(OpenAiChatOptions.builder().model(appProperties.getAi().getDeepseekModel()).build())
                .build();

        String globalPrompt = AIDocumentParseServer.generateParsePrompt();
        String systemPrompt = globalPrompt + "注意事项：" + agentCall;
        ReactAgent agent = ReactAgent.builder()
                .name("pdf_agent")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .saver(new MemorySaver())
                .build();
        long startTime = System.currentTimeMillis();
        List<List<? extends BaseParseResult>> taskList = groupArray(array, workflow.pageStep());
        AIParseResult taskResult = doTask(agent, prompt, taskList);
        log.info("耗时：{}秒", (System.currentTimeMillis() - startTime) / 1000);
        return taskResult;
    }
}
