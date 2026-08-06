package com.rcszh.tax.ai;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.ChatLog;
import com.rcszh.tax.ir.DocumentChunk;
import com.rcszh.tax.ir.ParsedDocument;
import com.rcszh.tax.server.AIDocumentParseServer;
import com.rcszh.tax.service.ChatLogService;
import com.rcszh.tax.workflow.DocumentWorkflow;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public abstract class AiManage {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiManage.class);

    @Resource
    protected ChatLogService chatLogService;

    public abstract AIParseResult chat(ParsedDocument document,
                                       String prompt,
                                       String agentCall,
                                       DocumentWorkflow workflow);

    public AIParseResult doTask(ReactAgent agent, String extractionPrompt, List<DocumentChunk> taskList) {
        log.info("ai 执行 doTask 任务开始");
        log.info("ai 调用次数:{}次", taskList.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AIParseResult result = new AIParseResult();
            log.info("开始执行异步 CompletableFuture 任务");
            // 多分片并发调用模型，再将记录和问题信息汇总为统一结果。
            List<CompletableFuture<AIParseResult>> futureTaskList =
                    taskList.stream()
                            .map(data -> CompletableFuture.supplyAsync(() -> sendMsg(agent, extractionPrompt, data), executor))
                            .toList();
            CompletableFuture<Void> allDone = CompletableFuture.allOf(futureTaskList.toArray(new CompletableFuture[0]));
            allDone.join();
            futureTaskList.stream().map(CompletableFuture::join).forEach(item -> {
                result.getErrors().addAll(item.getErrors());
                result.getRecords().addAll(item.getRecords());
                result.getErrorRecords().addAll(item.getErrorRecords());
                result.getWarnings().addAll(item.getWarnings());
            });
            log.info("所有异步 CompletableFuture 任务已完成");
            return result;
        } catch (Exception e) {
            log.error("ai 调用失败", e);
            return new AIParseResult();
        }
    }

    private AIParseResult sendMsg(ReactAgent agent, String extractionPrompt, DocumentChunk chunk) {
        ChatLog chatLog = new ChatLog();
        String userCall = extractionPrompt
                + "\n输入文档分片如下。tables 为统一表格，textBlocks 为非表格正文：\n"
                + JSONUtil.toJsonStr(chunk);
        chatLog.setPrompt(userCall);
        // 每个分片都记录原始 prompt 与响应，方便追查 AI 误抽取的根因。
        String sendResult = send(agent, userCall);
        chatLog.setResult(sendResult);
        chatLogService.save(chatLog);
        try {
            return AIDocumentParseServer.parseAIResponse(sendResult);
        } catch (Exception e) {
            return new AIParseResult();
        }
    }

    public String send(ReactAgent agent, String userCall) {
        AssistantMessage response;
        try {
            log.info("发送ai请求");
            response = agent.call(userCall);
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
        log.info("Ai 请求结束,返回的Token长度：{}", response.getText() == null ? 0 : response.getText().length());
        return response.getText();
    }
}
