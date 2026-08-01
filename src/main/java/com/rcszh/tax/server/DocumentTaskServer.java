package com.rcszh.tax.server;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.dto.CreateDocumentTaskDto;
import com.rcszh.tax.dto.executiontask.ExecutionTaskResultItemResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskResultResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskRouteSummaryResponse;
import com.rcszh.tax.entity.task.DocumentTask;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.entity.task.TaxTask;
import com.rcszh.tax.entity.task.TaxTaskItem;
import com.rcszh.tax.enums.RunTaskStatusEnum;
import com.rcszh.tax.mapper.TaxTaskItemMapper;
import com.rcszh.tax.mapper.TaxTaskMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DocumentTaskServer {
    private static final Logger logger = LoggerFactory.getLogger(DocumentTaskServer.class);
    public record CreatedTask(Long taskId, List<Long> itemIds) { }
    @Resource
    private TaxTaskMapper taxTaskMapper;
    @Resource
    private TaxTaskItemMapper taxTaskItemMapper;
    @Resource
    private ParseFileServer parseFileServer;

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
                item.setDocumentId(source.getDocumentId());
                item.setRequestedDocumentType(source.getDocumentType());
                item.setResolvedDocumentId(source.getDocumentId());
                item.setNeedHumanReview(Boolean.FALSE);
                item.setRemoteTaskId(source.getRemoteTaskId());
                item.setFileUrl(source.getFileUrl());
                taxTaskItemMapper.insert(item);
                itemIds.add(item.getId());
            }
        }
        return new CreatedTask(task.getId(), List.copyOf(itemIds));
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

    public void updateTask(DocumentTask task) {
        TaxTask taxTask = new TaxTask();
        taxTask.setId(task.getId());
        taxTask.setStatus(task.getStatus());
        taxTaskMapper.updateById(taxTask);
    }

    @Transactional(rollbackFor = Exception.class)
    public void getRemoteParseResult(List<DocumentTaskItem> taskItems) {
        for (DocumentTaskItem taskItem : taskItems) {
            if (StrUtil.isNotBlank(taskItem.getTaskResult())) {
                continue;
            }
            String remoteTaskId = taskItem.getRemoteTaskId();
            if (StrUtil.isBlank(remoteTaskId)) {
                logger.info("任务项{} remote_task_id 为空，无需远程解析", taskItem.getId());
                taskItem.setTaskResult("");
                continue;
            }
            JSONArray parseResult = parseFileServer.getParseResult(remoteTaskId);
            if (parseResult == null) {
                logger.info("任务项{} 解析失败，文档返回为空", taskItem.getId());
                continue;
            }
            taskItem.setTaskResult(parseResult.toString());
            updateTaskItem(taskItem);
        }
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
        result.setDocumentId(item.getDocumentId());
        result.setRequestedDocumentType(item.getRequestedDocumentType());
        result.setResolvedDocumentId(item.getResolvedDocumentId());
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
        result.setFileRule(item.getFileRule());
        result.setReviewReasons(item.getReviewReasons());
        result.setRouteSummary(buildRouteSummary(item));
        return result;
    }

    private TaxTaskItem toTaxTaskItem(DocumentTaskItem source) {
        TaxTaskItem item = new TaxTaskItem();
        item.setId(source.getId());
        item.setTaskId(source.getTaskId());
        item.setDocumentId(source.getDocumentId());
        item.setRequestedDocumentType(source.getRequestedDocumentType());
        item.setResolvedDocumentId(source.getResolvedDocumentId());
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
        item.setFileRule(source.getFileRule());
        item.setReviewReasons(source.getReviewReasons());
        return item;
    }

    private ExecutionTaskResultItemResponse toExecutionTaskResultItem(TaxTaskItem item) {
        ExecutionTaskResultItemResponse result = new ExecutionTaskResultItemResponse();
        result.setId(item.getId());
        result.setTaskId(item.getTaskId());
        result.setDocumentId(item.getDocumentId());
        result.setRequestedDocumentType(item.getRequestedDocumentType());
        result.setResolvedDocumentId(item.getResolvedDocumentId());
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
        result.setFileRule(item.getFileRule());
        result.setReviewReasons(item.getReviewReasons());
        result.setRouteSummary(buildRouteSummary(item));
        return result;
    }

    private ExecutionTaskRouteSummaryResponse buildRouteSummary(TaxTaskItem item) {
        ExecutionTaskRouteSummaryResponse result = new ExecutionTaskRouteSummaryResponse();
        result.setDocumentId(item.getResolvedDocumentId());
        result.setDocumentType(item.getRequestedDocumentType());
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
        String normalized = routeReason.replaceFirst("^\\[(ai|rule)]\\s*", "");
        return List.of(normalized.split("；")).stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }
}
