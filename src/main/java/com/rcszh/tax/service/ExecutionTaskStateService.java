package com.rcszh.tax.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rcszh.tax.entity.task.TaxExecutionTask;
import com.rcszh.tax.enums.ExecutionTaskStatusEnum;
import com.rcszh.tax.mapper.TaxExecutionTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 负责将内部解析任务的最终状态同步到用户执行任务。
 */
@Service
public class ExecutionTaskStateService {
    @Resource
    private TaxExecutionTaskMapper taskMapper;

    /**
     * 将执行任务标记为处理完成，并清除历史错误信息。
     *
     * @param executionTaskId 执行任务 ID，为空时忽略
     */
    public void markCompleted(Long executionTaskId) {
        if (executionTaskId == null) {
            return;
        }
        taskMapper.update(null, new LambdaUpdateWrapper<TaxExecutionTask>()
                .eq(TaxExecutionTask::getId, executionTaskId)
                .set(TaxExecutionTask::getStatus, ExecutionTaskStatusEnum.COMPLETED.name())
                .set(TaxExecutionTask::getErrorMessage, null));
    }

    /**
     * 将执行任务标记为失败，错误信息最长保留 1000 个字符。
     *
     * @param executionTaskId 执行任务 ID，为空时忽略
     * @param message 解析失败原因
     */
    public void markFailed(Long executionTaskId, String message) {
        if (executionTaskId == null) {
            return;
        }
        String normalized = message == null ? "解析任务执行失败" : message;
        if (normalized.length() > 1000) {
            normalized = normalized.substring(0, 1000);
        }
        taskMapper.update(null, new LambdaUpdateWrapper<TaxExecutionTask>()
                .eq(TaxExecutionTask::getId, executionTaskId)
                .set(TaxExecutionTask::getStatus, ExecutionTaskStatusEnum.FAILED.name())
                .set(TaxExecutionTask::getErrorMessage, normalized));
    }
}
