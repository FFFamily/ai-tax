package com.rcszh.tax.service;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.entity.task.DocumentTask;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.enums.RunTaskStatusEnum;
import com.rcszh.tax.parser.ExcelParser;
import com.rcszh.tax.parser.PDFParser;
import com.rcszh.tax.postprocess.RecordPostProcessService;
import com.rcszh.tax.server.DocumentServer;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.server.ParseFileServer;
import com.rcszh.tax.threads.TaskRunnable;
import com.rcszh.tax.util.ExcelUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 内部解析任务异步执行器，并负责回写关联的用户执行任务状态。
 */
@Component
public class DocumentTaskAsyncRunner {
    private static final Logger logger = LoggerFactory.getLogger(DocumentTaskAsyncRunner.class);

    @Resource
    private DocumentTaskServer documentTaskServer;
    @Resource
    private DocumentServer documentServer;
    @Resource
    private PDFParser pdfParser;
    @Resource
    private ExcelParser excelParser;
    @Resource
    private RecordPostProcessService recordPostProcessService;
    @Resource
    private ParseFileServer parseFileServer;
    @Resource
    private ExecutionTaskStateService executionTaskStateService;

    /**
     * 异步执行解析任务。成功时将用户任务标记为完成，异常时同步标记双方任务失败。
     *
     * @param parseTaskId 内部解析任务 ID
     * @param executionTaskId 用户执行任务 ID
     */
//    @Async("taxTaskExecutor")
    public void start(Long parseTaskId, Long executionTaskId) {
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
    private void prepareRemoteTasks(Long parseTaskId) {
        DocumentTask task = documentTaskServer.getTaskAndItemById(parseTaskId);
        if (task == null) {
            throw new IllegalStateException("内部解析任务不存在: " + parseTaskId);
        }
        for (DocumentTaskItem item : task.getItems()) {
            String fileUrl = item.getFileUrl();
            if (ExcelUtil.checkFileSuffix(fileUrl)
                    || StrUtil.isNotBlank(item.getRemoteTaskId())) {
                continue;
            }
            String remoteTaskId = parseFileServer.sendParseRequest(fileUrl);
            item.setRemoteTaskId(remoteTaskId);
            documentTaskServer.updateTaskItem(item);
        }
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
