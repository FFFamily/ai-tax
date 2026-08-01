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
import com.rcszh.tax.parser.ExcelParser;
import com.rcszh.tax.parser.PDFParser;
import com.rcszh.tax.postprocess.RecordPostProcessService;
import com.rcszh.tax.server.DocumentServer;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.util.ExcelUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

public class TaskRunnable implements Runnable {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(TaskRunnable.class);

    private final Long taskId;
    private final DocumentTaskServer documentTaskServer;
    private final DocumentServer documentServer;
    private final PDFParser pdfParser;
    private final ExcelParser excelParser;
    private final RecordPostProcessService recordPostProcessService;
    @Resource
    private DocumentParserRegistry documentParserRegistry;
    public TaskRunnable(Long taskId,
                        DocumentTaskServer documentTaskServer,
                        DocumentServer documentServer,
                        PDFParser pdfParser,
                        ExcelParser excelParser,
                        RecordPostProcessService recordPostProcessService) {
        this.taskId = taskId;
        this.documentTaskServer = documentTaskServer;
        this.documentServer = documentServer;
        this.pdfParser = pdfParser;
        this.excelParser = excelParser;
        this.recordPostProcessService = recordPostProcessService;
    }

    @Override
    public void run() {
        logger.info("开始执行异步任务");
        int tryCount = 0;
        DocumentTask task = documentTaskServer.getTaskAndItemById(taskId);
        if (task == null) {
            logger.warn("任务不存在: {}", taskId);
            return;
        }
        List<DocumentTaskItem> items = task.getItems();
        boolean isSuccess = items.stream().allMatch(i -> i.getTaskResult() != null);
        // 远程任务处理需要点时间
        while (tryCount < 5 && !isSuccess) {
            logger.info("第{}次执行远程任务", tryCount);
            // PDF 会先在远端完成 OCR / 表格识别，这里统一轮询结果；Excel 不依赖该结果，会天然跳过。
            documentTaskServer.getRemoteParseResult(items);
            isSuccess = items.stream().allMatch(i -> i.getTaskResult() != null);
            logger.info("远程任务执行结果：{}", isSuccess);
            tryCount++;
            if (!isSuccess) {
                try {
                    logger.info("远程任务执行失败，等待1分钟后重试");
                    Thread.sleep(1000L * 60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }

        logger.info("开始解析文档");
        logger.info("需要解析的文档数量：{}", items.size());

        for (DocumentTaskItem item : items) {
            String url = item.getFileUrl();
            // change_result 作为幂等标记，避免任务重跑时重复触发 AI 抽取和后处理。
            if (StrUtil.isNotBlank(item.getChangeResult())) {
                logger.info("当前文件已经进行过AI解析,无需再次解析");
                continue;
            }
            try {
                AIParseResult parserResult;
                // 依据文件类型进入不同预处理链路，但后续都收敛到统一的 AIParseResult。
                BaseParser baseParser = documentParserRegistry.resolve(item);
                parserResult = baseParser.doParse(item);
//                if (ExcelUtil.checkFileSuffix(url)) {
//                    parserResult = excelParser.doParse(item);
//                } else {
//                    parserResult = pdfParser.doParse(item);
//                }
                Long resolvedDocumentId = item.getResolvedDocumentId();
                if (resolvedDocumentId == null) {
                    resolvedDocumentId = item.getDocumentId();
                }
                logger.info("后处理使用的文档Id：{}", resolvedDocumentId);
                Map<String, Object> document = documentServer.getDocument(resolvedDocumentId);
                // AI 首次抽取只保证“识别出来”，业务可用性由后处理层补齐、归并和质检。
                recordPostProcessService.postProcess(parserResult, item, document);
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
