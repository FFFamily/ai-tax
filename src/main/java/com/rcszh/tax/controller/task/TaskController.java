package com.rcszh.tax.controller.task;

import com.rcszh.tax.common.ApiResponse;
import com.rcszh.tax.dto.TaskItemReviewRequest;
import com.rcszh.tax.entity.task.DocumentTask;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.service.TaskReviewService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/tasks")
public class TaskController {
    @Resource
    private DocumentTaskServer documentTaskServer;
    @Resource
    private TaskReviewService taskReviewService;

    @GetMapping("/{taskId}")
    public ApiResponse<DocumentTask> getTask(@PathVariable Long taskId) {
        DocumentTask task = documentTaskServer.getTaskAndItemById(taskId);
        if (task == null) {
            return ApiResponse.error("任务不存在");
        }
        return ApiResponse.success(task);
    }

    @PutMapping("/items/{itemId}/review")
    public ApiResponse<DocumentTaskItem> reviewTaskItem(@PathVariable Long itemId,
                                                        @RequestBody TaskItemReviewRequest request) {
        DocumentTaskItem item = taskReviewService.reviewTaskItem(itemId, request);
        if (item == null) {
            return ApiResponse.error("任务项不存在");
        }
        return ApiResponse.success(item);
    }
}
