package com.rcszh.tax.service;

import cn.hutool.json.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.entity.task.DocumentTask;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.entity.task.TaxExecutionTaskFile;
import com.rcszh.tax.enums.RunTaskStatusEnum;
import com.rcszh.tax.mapper.TaxExecutionTaskFileMapper;
import com.rcszh.tax.parser.DocumentParserRegistry;
import com.rcszh.tax.postprocess.RecordPostProcessService;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.server.ParseFileServer;
import com.rcszh.tax.threads.TaskRunnable;
import com.rcszh.tax.workflow.DocumentWorkflowRegistry;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内部解析任务异步执行器，并负责回写关联的用户执行任务状态。
 */
@Component
public class DocumentTaskAsyncRunner {
    private static final Logger logger = LoggerFactory.getLogger(DocumentTaskAsyncRunner.class);

    @Resource
    private DocumentTaskServer documentTaskServer;
    @Resource
    private DocumentWorkflowRegistry workflowRegistry;
    @Resource
    private RecordPostProcessService recordPostProcessService;
    @Resource
    private ParseFileServer parseFileServer;
    @Resource
    private ExecutionTaskStateService executionTaskStateService;
    @Resource
    private TaxExecutionTaskFileMapper executionTaskFileMapper;
    @Resource
    private StorageService storageService;
    @Resource
    private DocumentParserRegistry documentParserRegistry;

    /**
     * 异步执行解析任务。成功时将用户任务标记为完成，异常时同步标记双方任务失败。
     *
     * @param parseTaskId 内部解析任务 ID
     * @param executionTaskId 用户执行任务 ID
     */
    @Async("taxTaskExecutor")
    public void start(Long parseTaskId, Long executionTaskId) {
        try {
            DocumentTask task = requireTask(parseTaskId);
            Map<Long, TaxExecutionTaskFile> sourceFiles = loadSourceFiles(task, executionTaskId);
            prepareRemoteResults(task, sourceFiles);
            Map<Long, Path> localFilePaths = sourceFiles.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> storageService.resolve(entry.getValue().getStoragePath()),
                    (left, right) -> left,
                    LinkedHashMap::new));
            new TaskRunnable(parseTaskId, documentTaskServer, workflowRegistry, documentParserRegistry,
                    recordPostProcessService, localFilePaths).run();
            executionTaskStateService.markCompleted(executionTaskId, parseTaskId);
        } catch (Exception exception) {
            logger.error("解析任务执行失败: {}", parseTaskId, exception);
            markParseTaskFailed(parseTaskId);
            executionTaskStateService.markFailed(executionTaskId, parseTaskId, exception.getMessage());
        }
    }

    /**
     * 将需要 MinerU 解析的任务项映射到本地存储文件，批量上传并一次性回写解析结果。
     */
    private void prepareRemoteResults(DocumentTask task, Map<Long, TaxExecutionTaskFile> sourceFiles) {
        List<DocumentTaskItem> remoteItems = task.getItems().stream()
                .filter(item -> documentParserRegistry.resolve(item).requiresRemoteParse())
                .toList();
        if (remoteItems.isEmpty()) {
            return;
        }

        List<ParseFileServer.LocalParseFile> localFiles = remoteItems.stream().map(item -> {
            TaxExecutionTaskFile sourceFile = sourceFiles.get(item.getId());
            if (sourceFile == null) {
                throw new IllegalStateException("任务项未关联本地文件: " + item.getId());
            }
            return new ParseFileServer.LocalParseFile(
                    item.getId(),
                    sourceFile.getOriginalFileName(),
                    storageService.resolve(sourceFile.getStoragePath()));
        }).toList();

        Map<Long, JSONArray> parseResults = parseFileServer.parseLocalFiles(localFiles);
        if (parseResults.size() != remoteItems.size()) {
            throw new IllegalStateException("MinerU 返回结果数量与待解析文件数量不一致");
        }
        Map<Long, String> serializedResults = parseResults.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().toString(),
                (left, right) -> left,
                LinkedHashMap::new));
        documentTaskServer.updateTaskResults(serializedResults);
    }

    private DocumentTask requireTask(Long parseTaskId) {
        DocumentTask task = documentTaskServer.getTaskAndItemById(parseTaskId);
        if (task == null) {
            throw new IllegalStateException("内部解析任务不存在: " + parseTaskId);
        }
        return task;
    }

    private Map<Long, TaxExecutionTaskFile> loadSourceFiles(DocumentTask task, Long executionTaskId) {
        if (executionTaskId == null) {
            return Map.of();
        }
        Set<Long> itemIds = task.getItems().stream().map(DocumentTaskItem::getId).collect(Collectors.toSet());
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        List<TaxExecutionTaskFile> sourceFiles = executionTaskFileMapper.selectList(
                new LambdaQueryWrapper<TaxExecutionTaskFile>()
                        .eq(TaxExecutionTaskFile::getExecutionTaskId, executionTaskId)
                        .in(TaxExecutionTaskFile::getParseTaskItemId, itemIds));
        Map<Long, TaxExecutionTaskFile> filesByItemId = sourceFiles.stream().collect(Collectors.toMap(
                TaxExecutionTaskFile::getParseTaskItemId,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException("任务项关联了多个本地文件: " + left.getParseTaskItemId());
                },
                LinkedHashMap::new));
        if (filesByItemId.size() != itemIds.size()) {
            throw new IllegalStateException("部分任务项未关联本地文件");
        }
        return filesByItemId;
    }

    /**
     * 将内部解析任务状态更新为失败。
     */
    private void markParseTaskFailed(Long parseTaskId) {
        DocumentTask task = documentTaskServer.getTaskById(parseTaskId);
        if (task != null) {
            task.setStatus(RunTaskStatusEnum.FAIL.getStatus());
            documentTaskServer.updateTask(task);
        }
    }
}
