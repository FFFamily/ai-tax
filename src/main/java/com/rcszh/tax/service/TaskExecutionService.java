package com.rcszh.tax.service;

import com.rcszh.tax.dto.CreateDocumentTaskDto;
import com.rcszh.tax.server.DocumentTaskServer;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {
    private final DocumentTaskServer documentTaskServer;
    private final DocumentTaskAsyncRunner asyncRunner;

    public TaskExecutionService(DocumentTaskServer documentTaskServer,
                                DocumentTaskAsyncRunner asyncRunner) {
        this.documentTaskServer = documentTaskServer;
        this.asyncRunner = asyncRunner;
    }

    public String createAndStartTask(CreateDocumentTaskDto dto) {
        String taskId = documentTaskServer.createTask(dto);
        asyncRunner.start(taskId, null);
        return taskId;
    }
}
