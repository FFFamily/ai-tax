package com.rcszh.tax.server;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.dto.CreateDocumentTaskDto;
import com.rcszh.tax.dto.executiontask.ExecutionTaskResultItemResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskResultResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskRouteSummaryResponse;
import com.rcszh.tax.entity.task.DocumentTask;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.entity.task.TaxTask;
import com.rcszh.tax.entity.task.TaxTaskItem;
import com.rcszh.tax.entity.ReviewLearning;
import com.rcszh.tax.enums.RunTaskStatusEnum;
import com.rcszh.tax.mapper.ReviewLearningMapper;
import com.rcszh.tax.mapper.TaxTaskItemMapper;
import com.rcszh.tax.mapper.TaxTaskMapper;
import com.rcszh.tax.workflow.DocumentWorkflowRegistry;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DocumentTaskServer {
    public record CreatedTask(Long taskId, List<Long> itemIds) { }
    @Resource
    private TaxTaskMapper taxTaskMapper;
    @Resource
    private TaxTaskItemMapper taxTaskItemMapper;
    @Resource
    private ReviewLearningMapper reviewLearningMapper;
    @Resource
    private DocumentWorkflowRegistry workflowRegistry;

    @Transactional(rollbackFor = Exception.class)
    public Long createTask(CreateDocumentTaskDto dto) {
        return createTaskWithItems(dto).taskId();
    }

    @Transactional(rollbackFor = Exception.class)
    public CreatedTask createTaskWithItems(CreateDocumentTaskDto dto) {
        TaxTask task = new TaxTask();
        task.setStatus(RunTaskStatusEnum.RUNNING.getStatus());
        taxTaskMapper.insert(task);
        List<Long> itemIds = new java.util.ArrayList<>();
        if (dto.getItems() != null) {
            for (CreateDocumentTaskDto.Item source : dto.getItems()) {
                TaxTaskItem item = new TaxTaskItem();
                item.setTaskId(task.getId());
                item.setWorkflowCode(source.getWorkflowCode());
                item.setNeedHumanReview(Boolean.FALSE);
                item.setRemoteTaskId(source.getRemoteTaskId());
                item.setFileUrl(source.getFileUrl());
                taxTaskItemMapper.insert(item);
                itemIds.add(item.getId());
            }
        }
        return new CreatedTask(task.getId(), List.copyOf(itemIds));
    }

    /**
     * 删除指定内部解析任务及其任务项、解析结果和复核审计记录。
     *
     * <p>源文件由执行任务持有，不在这里删除。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTasks(Set<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        reviewLearningMapper.delete(new LambdaQueryWrapper<ReviewLearning>()
                .in(ReviewLearning::getTaskId, taskIds));
        taxTaskItemMapper.delete(new LambdaQueryWrapper<TaxTaskItem>()
                .in(TaxTaskItem::getTaskId, taskIds));
        taxTaskMapper.delete(new LambdaQueryWrapper<TaxTask>()
                .in(TaxTask::getId, taskIds));
    }



    @Transactional(rollbackFor = Exception.class)
    public DocumentTask getTaskAndItemById(Long id) {
        TaxTask task = taxTaskMapper.selectById(id);
        if (task == null) {
            return null;
        }
        List<TaxTaskItem> taskItems = taxTaskItemMapper.selectList(new LambdaQueryWrapper<TaxTaskItem>()
                .eq(TaxTaskItem::getTaskId, id));
        return toDocumentTask(task, taskItems);
    }

    @Transactional(rollbackFor = Exception.class)
    public ExecutionTaskResultResponse getExecutionTaskResultById(Long id) {
        TaxTask task = taxTaskMapper.selectById(id);
        if (task == null) {
            return null;
        }
        List<TaxTaskItem> taskItems = taxTaskItemMapper.selectList(new LambdaQueryWrapper<TaxTaskItem>()
                .eq(TaxTaskItem::getTaskId, id));
        ExecutionTaskResultResponse result = new ExecutionTaskResultResponse();
        result.setId(task.getId());
        result.setStatus(task.getStatus());
        result.setItems(taskItems.stream().map(this::toExecutionTaskResultItem).toList());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentTask getTaskById(Long id) {
        TaxTask task = taxTaskMapper.selectById(id);
        return task == null ? null : toDocumentTask(task, List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentTaskItem getTaskItemById(Long itemId) {
        TaxTaskItem item = taxTaskItemMapper.selectById(itemId);
        return item == null ? null : toDocumentTaskItem(item);
    }

    public void updateTaskItem(DocumentTaskItem taskItem) {
        taxTaskItemMapper.updateById(toTaxTaskItem(taskItem));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateTaskResults(Map<Long, String> taskResults) {
        for (Map.Entry<Long, String> entry : taskResults.entrySet()) {
            TaxTaskItem item = new TaxTaskItem();
            item.setId(entry.getKey());
            item.setTaskResult(entry.getValue());
            if (taxTaskItemMapper.updateById(item) != 1) {
                throw new IllegalStateException("任务项结果写入失败: " + entry.getKey());
            }
        }
    }

    public void updateTask(DocumentTask task) {
        TaxTask taxTask = new TaxTask();
        taxTask.setId(task.getId());
        taxTask.setStatus(task.getStatus());
        taxTaskMapper.updateById(taxTask);
    }

    private DocumentTask toDocumentTask(TaxTask task, List<TaxTaskItem> taskItems) {
        DocumentTask result = new DocumentTask();
        result.setId(task.getId());
        result.setStatus(task.getStatus());
        result.setItems(taskItems.stream().map(this::toDocumentTaskItem).toList());
        return result;
    }

    private DocumentTaskItem toDocumentTaskItem(TaxTaskItem item) {
        DocumentTaskItem result = new DocumentTaskItem();
        result.setId(item.getId());
        result.setTaskId(item.getTaskId());
        result.setWorkflowCode(item.getWorkflowCode());
        result.setRouteVariant(item.getRouteVariant());
        result.setRouteConfidence(item.getRouteConfidence());
        result.setRouteReason(item.getRouteReason());
        result.setNeedHumanReview(item.getNeedHumanReview());
        result.setRemoteTaskId(item.getRemoteTaskId());
        result.setTaskResult(item.getTaskResult());
        result.setFileUrl(item.getFileUrl());
        result.setParseStatus(item.getParseStatus());
        result.setChangeResult(item.getChangeResult());
        result.setTableResult(item.getTableResult());
        result.setReviewReasons(item.getReviewReasons());
        result.setRouteSummary(buildRouteSummary(item));
        applyLatestReview(result, latestReview(item.getId()));
        return result;
    }

    private TaxTaskItem toTaxTaskItem(DocumentTaskItem source) {
        TaxTaskItem item = new TaxTaskItem();
        item.setId(source.getId());
        item.setTaskId(source.getTaskId());
        item.setWorkflowCode(source.getWorkflowCode());
        item.setRouteVariant(source.getRouteVariant());
        item.setRouteConfidence(source.getRouteConfidence());
        item.setRouteReason(source.getRouteReason());
        item.setNeedHumanReview(source.getNeedHumanReview());
        item.setRemoteTaskId(source.getRemoteTaskId());
        item.setTaskResult(source.getTaskResult());
        item.setFileUrl(source.getFileUrl());
        item.setParseStatus(source.getParseStatus());
        item.setChangeResult(source.getChangeResult());
        item.setTableResult(source.getTableResult());
        item.setReviewReasons(source.getReviewReasons());
        return item;
    }

    private ExecutionTaskResultItemResponse toExecutionTaskResultItem(TaxTaskItem item) {
        ExecutionTaskResultItemResponse result = new ExecutionTaskResultItemResponse();
        result.setId(item.getId());
        result.setTaskId(item.getTaskId());
        result.setWorkflowCode(item.getWorkflowCode());
        result.setRouteVariant(item.getRouteVariant());
        result.setRouteConfidence(item.getRouteConfidence());
        result.setRouteReason(item.getRouteReason());
        result.setNeedHumanReview(item.getNeedHumanReview());
        result.setRemoteTaskId(item.getRemoteTaskId());
        result.setTaskResult(item.getTaskResult());
        result.setFileUrl(item.getFileUrl());
        result.setParseStatus(item.getParseStatus());
        result.setChangeResult(item.getChangeResult());
        result.setTableResult(item.getTableResult());
        result.setReviewReasons(item.getReviewReasons());
        result.setRouteSummary(buildRouteSummary(item));
        applyLatestReview(result, latestReview(item.getId()));
        return result;
    }

    private ReviewLearning latestReview(Long taskItemId) {
        if (taskItemId == null) {
            return null;
        }
        return reviewLearningMapper.selectOne(new LambdaQueryWrapper<ReviewLearning>()
                .eq(ReviewLearning::getTaskItemId, taskItemId)
                .orderByDesc(ReviewLearning::getId)
                .last("LIMIT 1"));
    }

    private void applyLatestReview(DocumentTaskItem item, ReviewLearning review) {
        if (review == null) {
            return;
        }
        item.setReviewer(review.getReviewer());
        item.setReviewComment(review.getComment());
    }

    private void applyLatestReview(ExecutionTaskResultItemResponse item, ReviewLearning review) {
        if (review == null) {
            return;
        }
        item.setReviewer(review.getReviewer());
        item.setReviewComment(review.getComment());
    }

    private ExecutionTaskRouteSummaryResponse buildRouteSummary(TaxTaskItem item) {
        ExecutionTaskRouteSummaryResponse result = new ExecutionTaskRouteSummaryResponse();
        result.setWorkflowCode(item.getWorkflowCode());
        result.setDocumentType(workflowRegistry.require(item.getWorkflowCode()).documentType());
        result.setVariant(item.getRouteVariant());
        result.setConfidence(item.getRouteConfidence());
        result.setNeedHumanReview(item.getNeedHumanReview());
        result.setRouteSource(inferRouteSource(item.getRouteReason()));
        result.setReasons(splitRouteReasons(item.getRouteReason()));
        return result;
    }

    private String inferRouteSource(String routeReason) {
        if (StrUtil.isBlank(routeReason)) {
            return "";
        }
        if (routeReason.startsWith("[fixed]")) {
            return "fixed";
        }
        if (routeReason.startsWith("[ai]")) {
            return "ai";
        }
        if (routeReason.startsWith("[rule]")) {
            return "rule";
        }
        if (routeReason.contains("显式指定")) {
            return "manual";
        }
        return "rule";
    }

    private List<String> splitRouteReasons(String routeReason) {
        if (StrUtil.isBlank(routeReason)) {
            return List.of();
        }
        String normalized = routeReason.replaceFirst("^\\[(ai|rule|fixed)]\\s*", "");
        return List.of(normalized.split("；")).stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }
}
