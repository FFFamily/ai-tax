package com.rcszh.tax.service;

import com.rcszh.tax.dto.CreateDocumentTaskDto;
import com.rcszh.tax.server.DocumentTaskServer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {
    @Resource
    private DocumentTaskServer documentTaskServer;
    @Resource
    private DocumentTaskAsyncRunner asyncRunner;

    public Long createAndStartTask(CreateDocumentTaskDto dto) {
        Long taskId = documentTaskServer.createTask(dto);
        asyncRunner.start(taskId, null);
        return taskId;
    }
}
