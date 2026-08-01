package com.rcszh.tax.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.dto.TaskItemReviewRequest;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.server.DocumentTaskServer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskReviewService {
    @Resource
    private DocumentTaskServer documentTaskServer;
    @Resource
    private ReviewLearningService reviewLearningService;

    @Transactional(rollbackFor = Exception.class)
    public DocumentTaskItem reviewTaskItem(Long itemId, TaskItemReviewRequest request) {
        DocumentTaskItem item = documentTaskServer.getTaskItemById(itemId);
        if (item == null) {
            return null;
        }

        // 复核接口既要回写当前任务项，也要把修正后的结论同步到 change_result 的结果快照里。
        boolean needHumanReview = Boolean.TRUE.equals(request.getNeedHumanReview());
        List<String> reviewReasons = request.getReviewReasons() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getReviewReasons());
        if (request.getComment() != null && !request.getComment().isBlank()) {
            reviewReasons.add("reviewComment:" + request.getComment().trim());
        }
        if (request.getReviewer() != null && !request.getReviewer().isBlank()) {
            reviewReasons.add("reviewer:" + request.getReviewer().trim());
        }

        List<String> distinctReviewReasons = reviewReasons.stream().distinct().toList();
        item.setNeedHumanReview(needHumanReview);
        item.setReviewReasons(JSONUtil.toJsonStr(distinctReviewReasons));
        if (request.getResolvedDocumentId() != null) {
            item.setResolvedDocumentId(request.getResolvedDocumentId());
        }

        String changeResult = item.getChangeResult();
        JSONObject root = changeResult == null || changeResult.isBlank()
                ? new JSONObject()
                : JSONUtil.parseObj(changeResult);
        JSONObject globalParam = root.getJSONObject("globalParam");
        if (globalParam == null) {
            globalParam = new JSONObject();
            root.set("globalParam", globalParam);
        }

        // 若复核端提交了修正记录，以人工版本覆盖 AI 产物，保证导出和后续查询都读取同一份结果。
        List<Map<String, Object>> reviewedRecords = request.getRecords() == null
                ? List.of()
                : request.getRecords().stream()
                .<Map<String, Object>>map(LinkedHashMap::new)
                .toList();
        if (!reviewedRecords.isEmpty()) {
            root.set("records", reviewedRecords);
            globalParam.set(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS, reviewedRecords);
            globalParam.set("dividendExtractCount", reviewedRecords.size());
        }
        globalParam.set("needHumanReview", needHumanReview);
        globalParam.set("reviewReasons", distinctReviewReasons);
        globalParam.set("dividendReviewReasons", distinctReviewReasons);
        if (request.getReviewer() != null && !request.getReviewer().isBlank()) {
            globalParam.set("reviewer", request.getReviewer().trim());
        }
        if (request.getComment() != null && !request.getComment().isBlank()) {
            globalParam.set("reviewComment", request.getComment().trim());
        }
        if (request.getResolvedDocumentId() != null) {
            globalParam.set("resolvedDocumentId", request.getResolvedDocumentId());
        }

        item.setChangeResult(root.toString());
        documentTaskServer.updateTaskItem(item);
        DocumentTaskItem updated = documentTaskServer.getTaskItemById(itemId);
        // 复核完成后立即沉淀学习样本，为后续模板路由和 AI few-shot 提供反馈数据。
        reviewLearningService.saveLearningFromReview(updated, reviewedRecords, request.getReviewer(), request.getComment());
        return updated;
    }
}
