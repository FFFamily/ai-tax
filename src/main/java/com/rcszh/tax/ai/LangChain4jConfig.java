package com.rcszh.tax.ai;

import com.rcszh.tax.config.AppProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {
    @Bean
    public ChatModel chatModelBean(AppProperties properties) {
        AppProperties.Ai ai = properties.getAi();

        return OpenAiChatModel.builder()
                .apiKey(ai.getDeepseekApiKey())
                .baseUrl(ai.getDeepseekBaseUrl())
                .modelName(ai.getDeepseekModel())
                .build();
    }
}
