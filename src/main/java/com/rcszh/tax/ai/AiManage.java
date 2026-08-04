package com.rcszh.tax.ai;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.rcszh.tax.dto.BaseParseResult;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.ChatLog;
import com.rcszh.tax.server.AIDocumentParseServer;
import com.rcszh.tax.service.ChatLogService;
import com.rcszh.tax.workflow.DocumentWorkflow;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public abstract class AiManage {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiManage.class);

    @Resource
    protected ChatLogService chatLogService;

    public abstract AIParseResult chat(List<? extends BaseParseResult> array,
                                       String prompt,
                                       String agentCall,
                                       DocumentWorkflow workflow);

    public List<List<? extends BaseParseResult>> groupArray(List<? extends BaseParseResult> array, int pageStep) {
        int step = pageStep <= 0 ? 20 : pageStep;
        List<List<? extends BaseParseResult>> result = new ArrayList<>();
        int size = (array.size() / step) + (array.size() % step == 0 ? 0 : 1);
        printInfo(array.size(), size);
        for (int i = 0; i < size; i++) {
            int start = i * step;
            int end = Math.min((i + 1) * step, array.size());
            result.add(array.subList(start, end));
        }
        return result;
    }

    private static void printInfo(int list, int size) {
        log.info("数据数量为：{}", list);
        log.info("ai 调用次数:{}次", size);
    }

    public AIParseResult doTask(ReactAgent agent, String agentCall, List<List<? extends BaseParseResult>> taskList) {
        log.info("ai 执行 doTask 任务开始");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AIParseResult result = new AIParseResult();
            log.info("开始执行异步 CompletableFuture 任务");
            // 多分片并发调用模型，再将记录和问题信息汇总为统一结果。
            List<CompletableFuture<AIParseResult>> futureTaskList =
                    taskList.stream()
                            .map(data -> CompletableFuture.supplyAsync(() -> sendMsg(agent, agentCall, data), executor))
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

    private AIParseResult sendMsg(ReactAgent agent, String agentCall, List<? extends BaseParseResult> objects) {
        ChatLog chatLog = new ChatLog();
        String userCall = agentCall + JSONUtil.toJsonStr(objects);
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
