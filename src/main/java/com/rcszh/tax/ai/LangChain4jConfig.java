package com.rcszh.tax.ai;

import com.rcszh.tax.config.AppProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.context.annotation.Lazy;
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

    @Bean
    @Lazy
    public EmbeddingModel embeddingModelBean(AppProperties properties) {
        AppProperties.Rag rag = properties.getAi().getRag();
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .apiKey(rag.getEmbeddingApiKey())
                .baseUrl(rag.getEmbeddingBaseUrl())
                .modelName(rag.getEmbeddingModel())
                .maxSegmentsPerBatch(10);
        if (rag.getEmbeddingDimensions() > 0) {
            builder.dimensions(rag.getEmbeddingDimensions());
        }
        return builder.build();
    }
}
