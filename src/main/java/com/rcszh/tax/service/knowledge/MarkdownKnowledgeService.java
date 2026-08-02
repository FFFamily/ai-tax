package com.rcszh.tax.service.knowledge;

import com.rcszh.tax.config.AppProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class MarkdownKnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(MarkdownKnowledgeService.class);
    private static final String SOURCE_METADATA_KEY = "source";

    private final EmbeddingModel embeddingModel;
    private final Path knowledgeDir;
    private final boolean enabled;
    private final boolean embeddingConfigured;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int maxResults;
    private final double minScore;
    private volatile InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private volatile int documentCount;
    private volatile int segmentCount;

    public MarkdownKnowledgeService(AppProperties properties, @Lazy EmbeddingModel embeddingModel) {
        AppProperties.Rag rag = properties.getAi().getRag();
        this.embeddingModel = embeddingModel;
        this.knowledgeDir = Path.of(rag.getKnowledgeDir()).toAbsolutePath().normalize();
        this.enabled = rag.isEnabled();
        this.embeddingConfigured = !rag.getEmbeddingApiKey().isBlank();
        this.chunkSize = Math.max(1, rag.getChunkSize());
        this.chunkOverlap = Math.max(0, Math.min(rag.getChunkOverlap(), chunkSize - 1));
        this.maxResults = Math.max(1, rag.getMaxResults());
        this.minScore = Math.max(0, Math.min(rag.getMinScore(), 1));
    }

    @PostConstruct
    public void reload() {
        if (!enabled) {
            clearIndex();
            log.info("Markdown RAG 已关闭");
            return;
        }
        if (!embeddingConfigured) {
            clearIndex();
            log.warn("未配置 app.ai.rag.embedding-api-key，跳过 Markdown 向量索引");
            return;
        }
        if (!Files.isDirectory(knowledgeDir)) {
            clearIndex();
            log.warn("Markdown 知识目录不存在：{}", knowledgeDir);
            return;
        }

        try (Stream<Path> paths = Files.walk(knowledgeDir)) {
            List<Path> markdownFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isMarkdown)
                    .sorted()
                    .toList();
            rebuildIndex(markdownFiles);
        } catch (IOException exception) {
            clearIndex();
            log.error("加载 Markdown 知识目录失败：{}", knowledgeDir, exception);
        }
    }

    public List<KnowledgeSegment> retrieve(String query) {
        InMemoryEmbeddingStore<TextSegment> currentStore = embeddingStore;
        if (query == null || query.isBlank() || currentStore.isEmpty()) {
            return List.of();
        }
        try {
            Embedding queryEmbedding = embeddingModel.embed(query.strip()).content();
            List<EmbeddingMatch<TextSegment>> matches = currentStore.search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(maxResults)
                            .minScore(minScore)
                            .build())
                    .matches();
            return matches.stream()
                    .map(match -> new KnowledgeSegment(
                            match.embedded().text(),
                            match.embedded().metadata().getString(SOURCE_METADATA_KEY),
                            match.score()))
                    .toList();
        } catch (RuntimeException exception) {
            log.error("检索 Markdown 知识失败，本次对话将不使用参考资料", exception);
            return List.of();
        }
    }

    public int documentCount() {
        return documentCount;
    }

    public int segmentCount() {
        return segmentCount;
    }

    private void rebuildIndex(List<Path> markdownFiles) {
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        List<TextSegment> segments = new ArrayList<>();
        int loadedDocuments = 0;

        for (Path file : markdownFiles) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8).strip();
                if (content.isEmpty()) {
                    continue;
                }
                String source = knowledgeDir.relativize(file).toString();
                Metadata metadata = Metadata.from(SOURCE_METADATA_KEY, source);
                segments.addAll(splitter.split(Document.from(content, metadata)));
                loadedDocuments++;
            } catch (IOException exception) {
                log.warn("跳过无法读取的 Markdown 文件：{}", file, exception);
            }
        }

        if (segments.isEmpty()) {
            clearIndex();
            log.warn("Markdown 知识目录中没有可索引的文档：{}", knowledgeDir);
            return;
        }

        try {
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            InMemoryEmbeddingStore<TextSegment> newStore = new InMemoryEmbeddingStore<>();
            newStore.addAll(embeddings, segments);
            embeddingStore = newStore;
            documentCount = loadedDocuments;
            segmentCount = segments.size();
            log.info("已从 {} 加载 {} 个 Markdown 文档，生成 {} 个向量片段",
                    knowledgeDir, documentCount, segmentCount);
        } catch (RuntimeException exception) {
            clearIndex();
            log.error("构建 Markdown 向量索引失败，RAG 暂不可用", exception);
        }
    }

    private void clearIndex() {
        embeddingStore = new InMemoryEmbeddingStore<>();
        documentCount = 0;
        segmentCount = 0;
    }

    private boolean isMarkdown(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    public record KnowledgeSegment(String text, String source, double score) {
    }
}
