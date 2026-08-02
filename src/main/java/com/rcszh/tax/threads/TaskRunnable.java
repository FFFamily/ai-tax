package com.rcszh.tax.threads;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.common.CommonConstant;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTask;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.enums.RunTaskStatusEnum;
import com.rcszh.tax.parser.BaseParser;
import com.rcszh.tax.parser.DocumentParserRegistry;
import com.rcszh.tax.postprocess.RecordPostProcessService;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.workflow.DocumentWorkflow;
import com.rcszh.tax.workflow.DocumentWorkflowRegistry;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class TaskRunnable implements Runnable {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(TaskRunnable.class);

    private final Long taskId;
    private final DocumentTaskServer documentTaskServer;
    private final DocumentWorkflowRegistry workflowRegistry;
    private final DocumentParserRegistry documentParserRegistry;
    private final RecordPostProcessService recordPostProcessService;
    private final Map<Long, Path> localFilePaths;

    public TaskRunnable(Long taskId,
                        DocumentTaskServer documentTaskServer,
                        DocumentWorkflowRegistry workflowRegistry,
                        DocumentParserRegistry documentParserRegistry,
                        RecordPostProcessService recordPostProcessService,
                        Map<Long, Path> localFilePaths) {
        this.taskId = taskId;
        this.documentTaskServer = documentTaskServer;
        this.workflowRegistry = workflowRegistry;
        this.documentParserRegistry = documentParserRegistry;
        this.recordPostProcessService = recordPostProcessService;
        this.localFilePaths = localFilePaths == null ? Map.of() : Map.copyOf(localFilePaths);
    }

    @Override
    public void run() {
        logger.info("开始执行异步任务");
        DocumentTask task = documentTaskServer.getTaskAndItemById(taskId);
        if (task == null) {
            logger.warn("任务不存在: {}", taskId);
            return;
        }
        List<DocumentTaskItem> items = task.getItems();
        items.forEach(item -> item.setLocalFilePath(localFilePaths.get(item.getId())));

        logger.info("开始解析文档");
        logger.info("需要解析的文档数量：{}", items.size());

        for (DocumentTaskItem item : items) {
            // change_result 作为幂等标记，避免任务重跑时重复触发 AI 抽取和后处理。
            if (StrUtil.isNotBlank(item.getChangeResult())) {
                logger.info("当前文件已经进行过AI解析,无需再次解析");
                continue;
            }
            try {
                AIParseResult parserResult;
                // 依据文件类型进入不同预处理链路，但后续都收敛到统一的 AIParseResult。
                BaseParser baseParser = documentParserRegistry.resolve(item);
                if (baseParser.requiresRemoteParse() && StrUtil.isBlank(item.getTaskResult())) {
                    throw new IllegalStateException("远程解析结果未准备完成: " + item.getFileUrl());
                }
                parserResult = baseParser.doParse(item);
                if (parserResult == null) {
                    throw new IllegalStateException("文档解析结果为空: " + item.getFileUrl());
                }
                DocumentWorkflow workflow = workflowRegistry.require(item.getWorkflowCode());
                logger.info("后处理使用的固定流程：{}", workflow.code());
                // AI 首次抽取只保证“识别出来”，业务可用性由后处理层补齐、归并和质检。
                recordPostProcessService.postProcess(parserResult, item, workflow);
                item.setChangeResult(JSONUtil.parse(parserResult).toString());
                item.setParseStatus(CommonConstant.YES);
                documentTaskServer.updateTaskItem(item);
            } catch (Exception e) {
                logger.error("解析失败", e);
                DocumentTask failedTask = documentTaskServer.getTaskById(taskId);
                if (failedTask != null) {
                    failedTask.setStatus(RunTaskStatusEnum.FAIL.getStatus());
                    documentTaskServer.updateTask(failedTask);
                }
                throw new RuntimeException("解析失败: " + e.getMessage(), e);
            }
        }
        DocumentTask oldTask = documentTaskServer.getTaskById(taskId);
        if (oldTask != null) {
            oldTask.setStatus(RunTaskStatusEnum.SUCCESS.getStatus());
            documentTaskServer.updateTask(oldTask);
        }
        logger.info("异步任务执行完成");
    }
}
