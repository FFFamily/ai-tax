package com.rcszh.tax.server;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.dto.CreateDocumentTaskDto;
import com.rcszh.tax.dto.executiontask.ExecutionTaskResultItemResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskResultResponse;
import com.rcszh.tax.dto.executiontask.ExecutionTaskRouteSummaryResponse;
import com.rcszh.tax.entity.TaxTask;
import com.rcszh.tax.entity.TaxTaskItem;
import com.rcszh.tax.enums.RunTaskStatusEnum;
import com.rcszh.tax.mapper.TaxTaskItemMapper;
import com.rcszh.tax.mapper.TaxTaskMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DocumentTaskServer {
    private static final Logger logger = LoggerFactory.getLogger(DocumentTaskServer.class);

    public static final String STATUS = "status";
    public static final String ID = "id";
    public static final String DOCUMENT_TASK_ITEM_TABLE_NAME = "items";

    @Resource
    private TaxTaskMapper taxTaskMapper;
    @Resource
    private TaxTaskItemMapper taxTaskItemMapper;
    @Resource
    private ParseFileServer parseFileServer;

    public static class Item {
        public static final String FIELD_ID = "id";
        public static final String TASK_ID = "task_id";
        public static final String DOCUMENT_ID = "document_id";
        public static final String REQUESTED_DOCUMENT_TYPE = "requested_document_type";
        public static final String RESOLVED_DOCUMENT_ID = "resolved_document_id";
        public static final String ROUTE_VARIANT = "route_variant";
        public static final String ROUTE_CONFIDENCE = "route_confidence";
        public static final String ROUTE_REASON = "route_reason";
        public static final String NEED_HUMAN_REVIEW = "need_human_review";
        public static final String FIELD_REMOTE_TASK_ID = "remote_task_id";
        public static final String FIELD_TASK_RESULT = "task_result";
        public static final String FILE_URL = "file_url";
        public static final String PARSE_STATUS = "parse_status";
        public static final String CHANGE_RESULT = "change_result";
        public static final String TABLE_RESULT = "table_result";
        public static final String FILE_RULE = "file_rule";
        public static final String PREPARED_TRANSACTION_LINES = "prepared_transaction_lines";
        public static final String PREPARED_DOCUMENT_FEATURES = "prepared_document_features";
        public static final String ROUTE_SUMMARY = "route_summary";
        public static final String REVIEW_REASONS = "review_reasons";
    }

    @Transactional(rollbackFor = Exception.class)
    public String createTask(CreateDocumentTaskDto dto) {
        return createTaskWithItems(dto).taskId();
    }

    @Transactional(rollbackFor = Exception.class)
    public CreatedTask createTaskWithItems(CreateDocumentTaskDto dto) {
        TaxTask task = new TaxTask();
        task.setStatus(RunTaskStatusEnum.RUNNING.getStatus());
        taxTaskMapper.insert(task);
        List<String> itemIds = new java.util.ArrayList<>();
        if (dto.getItems() != null) {
            for (CreateDocumentTaskDto.Item source : dto.getItems()) {
                TaxTaskItem item = new TaxTaskItem();
                item.setTaskId(task.getId());
                item.setDocumentId(source.getDocumentId());
                item.setRequestedDocumentType(source.getDocumentType());
                item.setResolvedDocumentId(source.getDocumentId());
                item.setNeedHumanReview(Boolean.FALSE);
                item.setFileUrl(source.getFileUrl());
                taxTaskItemMapper.insert(item);
                itemIds.add(item.getId());
            }
        }
        return new CreatedTask(task.getId(), List.copyOf(itemIds));
    }

    public record CreatedTask(String taskId, List<String> itemIds) {
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> getTaskAndItemById(String id) {
        TaxTask task = taxTaskMapper.selectById(id);
        if (task == null) {
            return null;
        }
        List<TaxTaskItem> taskItems = taxTaskItemMapper.selectList(new LambdaQueryWrapper<TaxTaskItem>()
                .eq(TaxTaskItem::getTaskId, id));
        Map<String, Object> result = toTaskMap(task);
        result.put(DOCUMENT_TASK_ITEM_TABLE_NAME, taskItems.stream().map(this::toTaskItemMap).toList());
        return result;
    }

    /**
     * 以强类型 DTO 查询用户执行任务需要展示的内部解析结果。
     * 旧任务接口继续使用 Map 返回值，该方法专供用户执行任务接口调用。
     *
     * @param id 内部解析任务 ID
     * @return 内部解析任务结果，不存在时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecutionTaskResultResponse getExecutionTaskResultById(String id) {
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
    public Map<String, Object> getTaskById(String id) {
        TaxTask task = taxTaskMapper.selectById(id);
        return task == null ? null : toTaskMap(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> getTaskItemById(String itemId) {
        TaxTaskItem item = taxTaskItemMapper.selectById(itemId);
        return item == null ? null : toTaskItemMap(item);
    }

    public void updateTaskItem(Map<String, Object> taskItem) {
        TaxTaskItem item = fromTaskItemMap(taskItem);
        taxTaskItemMapper.updateById(item);
    }

    public void updateTask(Map<String, Object> task) {
        TaxTask taxTask = new TaxTask();
        taxTask.setId((String) task.get(ID));
        taxTask.setStatus((String) task.get(STATUS));
        taxTaskMapper.updateById(taxTask);
    }

    @Transactional(rollbackFor = Exception.class)
    public void getRemoteParseResult(List<Map<String, Object>> taskItems) {
        for (Map<String, Object> taskItem : taskItems) {
            String taskResult = (String) taskItem.get(Item.FIELD_TASK_RESULT);
            if (StrUtil.isBlank(taskResult)) {
                String remoteTaskId = (String) taskItem.get(Item.FIELD_REMOTE_TASK_ID);
                if (remoteTaskId == null || remoteTaskId.isEmpty()) {
                    logger.info("任务项{}的remote_task_id为空，无需远程解析", taskItem.get(Item.FIELD_ID));
                    taskItem.put(Item.FIELD_TASK_RESULT, "");
                    continue;
                }
                JSONArray parseResult = parseFileServer.getParseResult(remoteTaskId);
                if (parseResult == null) {
                    logger.info("任务项{}的解析失败，文档返回为空", taskItem.get(Item.FIELD_ID));
                    continue;
                }
                taskItem.put(Item.FIELD_TASK_RESULT, parseResult.toString());
                updateTaskItem(taskItem);
            }
        }
    }

    private Map<String, Object> toTaskMap(TaxTask task) {
        Map<String, Object> map = new HashMap<>();
        map.put(ID, task.getId());
        map.put(STATUS, task.getStatus());
        return map;
    }

    private Map<String, Object> toTaskItemMap(TaxTaskItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put(Item.FIELD_ID, item.getId());
        map.put(Item.TASK_ID, item.getTaskId());
        map.put(Item.DOCUMENT_ID, item.getDocumentId());
        map.put(Item.REQUESTED_DOCUMENT_TYPE, item.getRequestedDocumentType());
        map.put(Item.RESOLVED_DOCUMENT_ID, item.getResolvedDocumentId());
        map.put(Item.ROUTE_VARIANT, item.getRouteVariant());
        map.put(Item.ROUTE_CONFIDENCE, item.getRouteConfidence());
        map.put(Item.ROUTE_REASON, item.getRouteReason());
        map.put(Item.NEED_HUMAN_REVIEW, item.getNeedHumanReview());
        map.put(Item.FIELD_REMOTE_TASK_ID, item.getRemoteTaskId());
        map.put(Item.FIELD_TASK_RESULT, item.getTaskResult());
        map.put(Item.FILE_URL, item.getFileUrl());
        map.put(Item.PARSE_STATUS, item.getParseStatus());
        map.put(Item.CHANGE_RESULT, item.getChangeResult());
        map.put(Item.TABLE_RESULT, item.getTableResult());
        map.put(Item.FILE_RULE, item.getFileRule());
        map.put(Item.REVIEW_REASONS, item.getReviewReasons());
        map.put(Item.ROUTE_SUMMARY, buildRouteSummaryMap(item));
        return map;
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
        result.setRouteSummary(buildRouteSummaryResponse(item));
        return result;
    }

    private Map<String, Object> buildRouteSummaryMap(TaxTaskItem item) {
        Map<String, Object> routeSummary = new LinkedHashMap<>();
        routeSummary.put("documentId", item.getResolvedDocumentId());
        routeSummary.put("documentType", item.getRequestedDocumentType());
        routeSummary.put("variant", item.getRouteVariant());
        routeSummary.put("confidence", item.getRouteConfidence());
        routeSummary.put("needHumanReview", item.getNeedHumanReview());
        routeSummary.put("routeSource", inferRouteSource(item.getRouteReason()));
        routeSummary.put("reasons", splitRouteReasons(item.getRouteReason()));
        return routeSummary;
    }

    private ExecutionTaskRouteSummaryResponse buildRouteSummaryResponse(TaxTaskItem item) {
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

    private TaxTaskItem fromTaskItemMap(Map<String, Object> taskItem) {
        TaxTaskItem item = new TaxTaskItem();
        item.setId((String) taskItem.get(Item.FIELD_ID));
        item.setTaskId((String) taskItem.get(Item.TASK_ID));
        item.setDocumentId((String) taskItem.get(Item.DOCUMENT_ID));
        item.setRequestedDocumentType((String) taskItem.get(Item.REQUESTED_DOCUMENT_TYPE));
        item.setResolvedDocumentId((String) taskItem.get(Item.RESOLVED_DOCUMENT_ID));
        item.setRouteVariant((String) taskItem.get(Item.ROUTE_VARIANT));
        Object routeConfidence = taskItem.get(Item.ROUTE_CONFIDENCE);
        if (routeConfidence != null) {
            item.setRouteConfidence(new java.math.BigDecimal(routeConfidence.toString()));
        }
        item.setRouteReason((String) taskItem.get(Item.ROUTE_REASON));
        Object humanReview = taskItem.get(Item.NEED_HUMAN_REVIEW);
        if (humanReview != null) {
            item.setNeedHumanReview(Boolean.valueOf(humanReview.toString()));
        }
        item.setRemoteTaskId((String) taskItem.get(Item.FIELD_REMOTE_TASK_ID));
        item.setTaskResult((String) taskItem.get(Item.FIELD_TASK_RESULT));
        item.setFileUrl((String) taskItem.get(Item.FILE_URL));
        item.setParseStatus((String) taskItem.get(Item.PARSE_STATUS));
        item.setChangeResult((String) taskItem.get(Item.CHANGE_RESULT));
        item.setTableResult((String) taskItem.get(Item.TABLE_RESULT));
        item.setFileRule((String) taskItem.get(Item.FILE_RULE));
        Object reviewReasons = taskItem.get(Item.REVIEW_REASONS);
        if (reviewReasons instanceof String str) {
            item.setReviewReasons(str);
        } else if (reviewReasons != null) {
            item.setReviewReasons(JSONUtil.toJsonStr(reviewReasons));
        }
        return item;
    }
}
