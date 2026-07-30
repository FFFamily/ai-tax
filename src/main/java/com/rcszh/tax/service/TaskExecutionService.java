package com.rcszh.tax.service;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.dto.CreateDocumentTaskDto;
import com.rcszh.tax.server.DocumentServer;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.server.ParseFileServer;
import com.rcszh.tax.parser.ExcelParser;
import com.rcszh.tax.parser.PDFParser;
import com.rcszh.tax.postprocess.RecordPostProcessService;
import com.rcszh.tax.threads.TaskRunnable;
import com.rcszh.tax.util.ExcelUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {
    private final DocumentTaskServer documentTaskServer;
    private final DocumentServer documentServer;
    private final PDFParser pdfParser;
    private final ExcelParser excelParser;
    private final RecordPostProcessService recordPostProcessService;
    private final ParseFileServer parseFileServer;

    public TaskExecutionService(DocumentTaskServer documentTaskServer,
                                DocumentServer documentServer,
                                PDFParser pdfParser,
                                ExcelParser excelParser,
                                RecordPostProcessService recordPostProcessService,
                                ParseFileServer parseFileServer) {
        this.documentTaskServer = documentTaskServer;
        this.documentServer = documentServer;
        this.pdfParser = pdfParser;
        this.excelParser = excelParser;
        this.recordPostProcessService = recordPostProcessService;
        this.parseFileServer = parseFileServer;
    }

    public String createAndStartTask(CreateDocumentTaskDto dto) {
        // PDF 类文件先下发到远端 OCR/版面解析服务，后续异步任务轮询结果后再进入本地 AI 抽取链路。
        if (dto.getItems() != null) {
            for (CreateDocumentTaskDto.Item item : dto.getItems()) {
                String fileUrl = item.getFileUrl();
                if (StrUtil.isBlank(fileUrl) || ExcelUtil.checkFileSuffix(fileUrl)) {
                    continue;
                }
                String remoteTaskId = parseFileServer.sendParseRequest(fileUrl);
                item.setRemoteTaskId(remoteTaskId);
            }
        }
        String taskId = documentTaskServer.createTask(dto);
        asyncRun(taskId);
        return taskId;
    }

    @Async("taxTaskExecutor")
    public void asyncRun(String taskId) {
        // 通过独立 Runnable 承载整条解析流水线，便于后续复用和单测隔离。
        new TaskRunnable(taskId, documentTaskServer, documentServer, pdfParser, excelParser, recordPostProcessService).run();
    }
}
