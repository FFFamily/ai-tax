package com.rcszh.tax.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.entity.ReviewLearning;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.mapper.ReviewLearningMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工复核记录服务。
 *
 * <p>复核数据仅用于审计和问题分析，不再生成匹配规则或参与后续任务的流程选择。</p>
 */
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
        ReviewLearning learning = new ReviewLearning();
        learning.setTaskItemId(taskItem.getId());
        learning.setTaskId(taskItem.getTaskId());
        learning.setWorkflowCode(taskItem.getWorkflowCode());
        learning.setReviewReasons(taskItem.getReviewReasons());
        learning.setReviewedRecords(JSONUtil.toJsonStr(reviewedRecords));
        learning.setReviewer(reviewer);
        learning.setComment(comment);
        reviewLearningMapper.insert(learning);
    }

    public List<Map<String, Object>> listLearnings(String workflowCode) {
        LambdaQueryWrapper<ReviewLearning> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(workflowCode)) {
            wrapper.eq(ReviewLearning::getWorkflowCode, workflowCode);
        }
        wrapper.orderByDesc(ReviewLearning::getCreatedAt);
        return reviewLearningMapper.selectList(wrapper).stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(ReviewLearning learning) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", learning.getId());
        result.put("taskId", learning.getTaskId());
        result.put("taskItemId", learning.getTaskItemId());
        result.put("workflowCode", learning.getWorkflowCode());
        result.put("reviewReasons", parseJson(learning.getReviewReasons()));
        result.put("reviewedRecords", parseJson(learning.getReviewedRecords()));
        result.put("reviewer", learning.getReviewer());
        result.put("comment", learning.getComment());
        result.put("createdAt", learning.getCreatedAt());
        return result;
    }

    private Object parseJson(String value) {
        return StrUtil.isBlank(value) ? null : JSONUtil.parse(value);
    }
}
