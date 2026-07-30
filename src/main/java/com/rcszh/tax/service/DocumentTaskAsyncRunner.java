package com.rcszh.tax.service;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.enums.RunTaskStatusEnum;
import com.rcszh.tax.parser.ExcelParser;
import com.rcszh.tax.parser.PDFParser;
import com.rcszh.tax.postprocess.RecordPostProcessService;
import com.rcszh.tax.server.DocumentServer;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.server.ParseFileServer;
import com.rcszh.tax.threads.TaskRunnable;
import com.rcszh.tax.util.ExcelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 内部解析任务异步执行器，并负责回写关联的用户执行任务状态。
 */
@Component
public class DocumentTaskAsyncRunner {
    private static final Logger logger = LoggerFactory.getLogger(DocumentTaskAsyncRunner.class);

    private final DocumentTaskServer documentTaskServer;
    private final DocumentServer documentServer;
    private final PDFParser pdfParser;
    private final ExcelParser excelParser;
    private final RecordPostProcessService recordPostProcessService;
    private final ParseFileServer parseFileServer;
    private final ExecutionTaskStateService executionTaskStateService;

    public DocumentTaskAsyncRunner(DocumentTaskServer documentTaskServer,
                                   DocumentServer documentServer,
                                   PDFParser pdfParser,
                                   ExcelParser excelParser,
                                   RecordPostProcessService recordPostProcessService,
                                   ParseFileServer parseFileServer,
                                   ExecutionTaskStateService executionTaskStateService) {
        this.documentTaskServer = documentTaskServer;
        this.documentServer = documentServer;
        this.pdfParser = pdfParser;
        this.excelParser = excelParser;
        this.recordPostProcessService = recordPostProcessService;
        this.parseFileServer = parseFileServer;
        this.executionTaskStateService = executionTaskStateService;
    }

    /**
     * 异步执行解析任务。成功时将用户任务标记为完成，异常时同步标记双方任务失败。
     *
     * @param parseTaskId 内部解析任务 ID
     * @param executionTaskId 用户执行任务 ID
     */
    @Async("taxTaskExecutor")
    public void start(String parseTaskId, String executionTaskId) {
        try {
            prepareRemoteTasks(parseTaskId);
            new TaskRunnable(parseTaskId, documentTaskServer, documentServer, pdfParser, excelParser,
                    recordPostProcessService).run();
            executionTaskStateService.markCompleted(executionTaskId);
        } catch (Exception exception) {
            logger.error("解析任务执行失败: {}", parseTaskId, exception);
            markParseTaskFailed(parseTaskId);
            executionTaskStateService.markFailed(executionTaskId, exception.getMessage());
        }
    }

    /**
     * 为非 Excel 文件创建远程解析任务，已有远程任务 ID 的文件不会重复提交。
     */
    @SuppressWarnings("unchecked")
    private void prepareRemoteTasks(String parseTaskId) {
        Map<String, Object> task = documentTaskServer.getTaskAndItemById(parseTaskId);
        if (task == null) {
            throw new IllegalStateException("内部解析任务不存在: " + parseTaskId);
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) task.get(DocumentTaskServer.DOCUMENT_TASK_ITEM_TABLE_NAME);
        for (Map<String, Object> item : items) {
            String fileUrl = (String) item.get(DocumentTaskServer.Item.FILE_URL);
            if (ExcelUtil.checkFileSuffix(fileUrl)
                    || StrUtil.isNotBlank((String) item.get(DocumentTaskServer.Item.FIELD_REMOTE_TASK_ID))) {
                continue;
            }
            String remoteTaskId = parseFileServer.sendParseRequest(fileUrl);
            item.put(DocumentTaskServer.Item.FIELD_REMOTE_TASK_ID, remoteTaskId);
            documentTaskServer.updateTaskItem(item);
        }
    }

    /**
     * 将内部解析任务状态更新为失败。
     */
    private void markParseTaskFailed(String parseTaskId) {
        Map<String, Object> task = documentTaskServer.getTaskById(parseTaskId);
        if (task != null) {
            task.put(DocumentTaskServer.STATUS, RunTaskStatusEnum.FAIL.getStatus());
            documentTaskServer.updateTask(task);
        }
    }
}
