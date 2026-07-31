package com.rcszh.tax.controller;

import com.rcszh.tax.common.ApiResponse;
import com.rcszh.tax.dto.CreateDocumentTaskDto;
import com.rcszh.tax.dto.TaskItemReviewRequest;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.service.TaskExecutionService;
import com.rcszh.tax.service.TaskReviewService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/tasks")
public class TaskController {
    private final TaskExecutionService taskExecutionService;
    private final DocumentTaskServer documentTaskServer;
    private final TaskReviewService taskReviewService;

    public TaskController(TaskExecutionService taskExecutionService,
                          DocumentTaskServer documentTaskServer,
                          TaskReviewService taskReviewService) {
        this.taskExecutionService = taskExecutionService;
        this.documentTaskServer = documentTaskServer;
        this.taskReviewService = taskReviewService;
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> createTask(@RequestBody CreateDocumentTaskDto request) {
        Long taskId = taskExecutionService.createAndStartTask(request);
        return ApiResponse.success(Map.of("taskId", taskId));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable Long taskId) {
        Map<String, Object> task = documentTaskServer.getTaskAndItemById(taskId);
        if (task == null) {
            return ApiResponse.error("任务不存在");
        }
        return ApiResponse.success(task);
    }

    @PutMapping("/items/{itemId}/review")
    public ApiResponse<Map<String, Object>> reviewTaskItem(@PathVariable Long itemId,
                                                           @RequestBody TaskItemReviewRequest request) {
        Map<String, Object> item = taskReviewService.reviewTaskItem(itemId, request);
        if (item == null) {
            return ApiResponse.error("任务项不存在");
        }
        return ApiResponse.success(item);
    }
}
