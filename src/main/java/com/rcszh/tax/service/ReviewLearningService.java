package com.rcszh.tax.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.entity.ReviewLearning;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.mapper.ReviewLearningMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewLearningService {
    @Resource
    private ReviewLearningMapper reviewLearningMapper;

    @Transactional(rollbackFor = Exception.class)
    public void saveLearningFromReview(DocumentTaskItem taskItem,
                                       List<Map<String, Object>> reviewedRecords,
                                       String reviewer,
                                       String comment) {
        if (taskItem == null || taskItem.getId() == null) {
            return;
        }
        // 每次人工复核都落成一条 learning 记录，把“纠错结果”转成后续可复用的机器线索。
        ReviewLearning learning = new ReviewLearning();
        learning.setTaskItemId(taskItem.getId());
        learning.setTaskId(taskItem.getTaskId());
        learning.setRequestedDocumentType(taskItem.getRequestedDocumentType());
        learning.setResolvedDocumentId(taskItem.getResolvedDocumentId());
        learning.setRouteSummary(JSONUtil.toJsonStr(taskItem.getRouteSummary()));
        learning.setReviewReasons(taskItem.getReviewReasons());
        learning.setReviewedRecords(JSONUtil.toJsonStr(reviewedRecords));
        learning.setReviewer(reviewer);
        learning.setComment(comment);
        learning.setSuggestedMatchRule(JSONUtil.toJsonStr(buildSuggestedMatchRule(taskItem, reviewedRecords)));
        learning.setFewShotExample(JSONUtil.toJsonStr(buildFewShotExample(taskItem, reviewedRecords)));
        reviewLearningMapper.insert(learning);
    }

    public List<Map<String, Object>> listLearnings(String requestedDocumentType) {
        if (reviewLearningMapper == null) {
            return List.of();
        }
        LambdaQueryWrapper<ReviewLearning> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(requestedDocumentType)) {
            wrapper.eq(ReviewLearning::getRequestedDocumentType, requestedDocumentType);
        }
        wrapper.orderByDesc(ReviewLearning::getCreatedAt);
        return reviewLearningMapper.selectList(wrapper).stream()
                .map(this::toMap)
                .toList();
    }

    public List<String> listSuggestedKeywords(Long resolvedDocumentId,
                                              String requestedDocumentType,
                                              int limit) {
        if (reviewLearningMapper == null) {
            return List.of();
        }
        // 从历史复核中抽取关键词，反哺规则路由，降低同类文档反复误判的概率。
        return queryLearnings(resolvedDocumentId, requestedDocumentType, limit).stream()
                .map(ReviewLearning::getSuggestedMatchRule)
                .filter(StrUtil::isNotBlank)
                .map(JSONUtil::parseObj)
                .map(object -> object.get("anyKeywords"))
                .filter(Collection.class::isInstance)
                .map(item -> (Collection<?>) item)
                .flatMap(Collection::stream)
                .map(item -> item == null ? "" : item.toString())
                .filter(StrUtil::isNotBlank)
                .distinct()
                .limit(limit)
                .toList();
    }

    public List<Map<String, Object>> listFewShotExamples(Long resolvedDocumentId,
                                                         String requestedDocumentType,
                                                         int limit) {
        if (reviewLearningMapper == null) {
            return List.of();
        }
        // few-shot 样本保留“路由上下文 + 人工确认结果”，供 AI 兜底判断时参考真实案例。
        List<Map<String, Object>> result = new ArrayList<>();
        for (ReviewLearning learning : queryLearnings(resolvedDocumentId, requestedDocumentType, limit)) {
            if (StrUtil.isBlank(learning.getFewShotExample())) {
                continue;
            }
            Map<String, Object> example = new LinkedHashMap<>();
            example.put("resolvedDocumentId", learning.getResolvedDocumentId());
            example.put("requestedDocumentType", learning.getRequestedDocumentType());
            example.put("reviewer", learning.getReviewer());
            example.put("comment", learning.getComment());
            example.put("fewShotExample", JSONUtil.parse(learning.getFewShotExample()));
            result.add(example);
        }
        return result;
    }

    private Map<String, Object> toMap(ReviewLearning learning) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", learning.getId());
        result.put("taskId", learning.getTaskId());
        result.put("taskItemId", learning.getTaskItemId());
        result.put("requestedDocumentType", learning.getRequestedDocumentType());
        result.put("resolvedDocumentId", learning.getResolvedDocumentId());
        result.put("routeSummary", parseJson(learning.getRouteSummary()));
        result.put("reviewReasons", parseJson(learning.getReviewReasons()));
        result.put("reviewedRecords", parseJson(learning.getReviewedRecords()));
        result.put("reviewer", learning.getReviewer());
        result.put("comment", learning.getComment());
        result.put("suggestedMatchRule", parseJson(learning.getSuggestedMatchRule()));
        result.put("fewShotExample", parseJson(learning.getFewShotExample()));
        result.put("createdAt", learning.getCreatedAt());
        return result;
    }

    private Object parseJson(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return JSONUtil.parse(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSuggestedMatchRule(DocumentTaskItem taskItem,
                                                        List<Map<String, Object>> reviewedRecords) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("requestedDocumentType", taskItem.getRequestedDocumentType());
        rule.put("resolvedDocumentId", taskItem.getResolvedDocumentId());
        rule.put("routeSummary", taskItem.getRouteSummary());

        Set<String> anyKeywords = new LinkedHashSet<>();
        // 当前先抽取摘要、付款方、币种等稳定字段，后续可以继续扩展到表头和机构名。
        for (Map<String, Object> record : reviewedRecords) {
            collectKeyword(anyKeywords, record.get("summary"));
            collectKeyword(anyKeywords, record.get("payer"));
            collectKeyword(anyKeywords, record.get("currency"));
        }
        rule.put("anyKeywords", new ArrayList<>(anyKeywords));
        return rule;
    }

    private Map<String, Object> buildFewShotExample(DocumentTaskItem taskItem,
                                                    List<Map<String, Object>> reviewedRecords) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("requestedDocumentType", taskItem.getRequestedDocumentType());
        example.put("resolvedDocumentId", taskItem.getResolvedDocumentId());
        example.put("routeSummary", taskItem.getRouteSummary());
        example.put("reviewedRecords", reviewedRecords);
        return example;
    }

    private List<ReviewLearning> queryLearnings(Long resolvedDocumentId,
                                                String requestedDocumentType,
                                                int limit) {
        LambdaQueryWrapper<ReviewLearning> wrapper = new LambdaQueryWrapper<>();
        if (resolvedDocumentId != null) {
            wrapper.eq(ReviewLearning::getResolvedDocumentId, resolvedDocumentId);
        }
        if (StrUtil.isNotBlank(requestedDocumentType)) {
            wrapper.eq(ReviewLearning::getRequestedDocumentType, requestedDocumentType);
        }
        wrapper.orderByDesc(ReviewLearning::getCreatedAt);
        List<ReviewLearning> learnings = reviewLearningMapper.selectList(wrapper);
        if (learnings == null || learnings.isEmpty()) {
            return List.of();
        }
        return learnings.stream().limit(Math.max(limit, 1)).toList();
    }

    private void collectKeyword(Collection<String> target, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString().trim();
        if (text.isBlank()) {
            return;
        }
        target.add(text);
    }
}
