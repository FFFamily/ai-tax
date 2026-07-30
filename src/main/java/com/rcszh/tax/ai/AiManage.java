package com.rcszh.tax.ai;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.rcszh.tax.dto.BaseParseResult;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.ChatLog;
import com.rcszh.tax.enums.DocumentPageTypeEnum;
import com.rcszh.tax.server.AIDocumentParseServer;
import com.rcszh.tax.service.ChatLogService;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public abstract class AiManage {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiManage.class);

    protected final ChatLogService chatLogService;

    protected AiManage(ChatLogService chatLogService) {
        this.chatLogService = chatLogService;
    }

    public abstract AIParseResult chat(List<? extends BaseParseResult> array, String prompt, String agentCall, Map<String, Object> documentConfig);

    public List<List<? extends BaseParseResult>> groupArrayByConfig(List<? extends BaseParseResult> array, Object pageType, Object pageStep) {
        String type = pageType == null ? DocumentPageTypeEnum.DATA.getCode() : pageType.toString();
        int step = pageStep == null ? 20 : new BigDecimal(pageStep.toString()).intValue();
        List<List<? extends BaseParseResult>> result = new ArrayList<>();
        // 根据模板配置控制分片粒度，兼顾大文件成本和上下文完整性。
        if (type.equals(DocumentPageTypeEnum.PAGE.getCode())) {
            List<? extends List<? extends BaseParseResult>> list = array.stream()
                    .collect(Collectors.groupingBy(BaseParseResult::getPageIndex))
                    .values().stream().toList();
            int size = (list.size() / step) + (list.size() % step == 0 ? 0 : 1);
            printInfo(list.size(), size);
            for (int i = 0; i < size; i++) {
                int start = i * step;
                int end = Math.min((i + 1) * step, list.size());
                List<? extends BaseParseResult> collect = list.subList(start, end).stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
                result.add(collect);
            }
        } else if (type.equals(DocumentPageTypeEnum.DATA.getCode())) {
            int size = (array.size() / step) + (array.size() % step == 0 ? 0 : 1);
            printInfo(array.size(), size);
            for (int i = 0; i < size; i++) {
                int start = i * step;
                int end = Math.min((i + 1) * step, array.size());
                List<? extends BaseParseResult> objects = array.subList(start, end);
                result.add(objects);
            }
        } else {
            throw new RuntimeException("不支持的分页类型");
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
            // 多分片并发调用模型，再将 records / warnings / globalParam 汇总为统一结果。
            List<CompletableFuture<AIParseResult>> futureTaskList =
                    taskList.stream()
                            .map(data -> CompletableFuture.supplyAsync(() -> sendMsg(agent, agentCall, data), executor))
                            .toList();
            CompletableFuture<Void> allDone = CompletableFuture.allOf(futureTaskList.toArray(new CompletableFuture[0]));
            allDone.join();
            futureTaskList.stream().map(CompletableFuture::join).forEach(item -> {
                result.getGlobalParam().putAll(item.getGlobalParam());
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
        chatLog.setToken(sendResult == null ? 0 : sendResult.length());
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
