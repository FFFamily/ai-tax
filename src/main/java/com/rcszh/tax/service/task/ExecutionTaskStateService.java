package com.rcszh.tax.service.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rcszh.tax.entity.task.TaxExecutionTask;
import com.rcszh.tax.entity.task.TaxExecutionTaskAttempt;
import com.rcszh.tax.enums.ExecutionTaskStatusEnum;
import com.rcszh.tax.mapper.TaxExecutionTaskAttemptMapper;
import com.rcszh.tax.mapper.TaxExecutionTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 负责将内部解析任务的最终状态同步到用户执行任务。
 */
@Service
public class ExecutionTaskStateService {
    @Resource
    private TaxExecutionTaskMapper taskMapper;
    @Resource
    private TaxExecutionTaskAttemptMapper attemptMapper;

    /**
     * 将执行任务标记为处理完成，并清除历史错误信息。
     *
     * @param executionTaskId 执行任务 ID，为空时忽略
     * @param parseTaskId 本次完成的内部解析任务 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void markCompleted(Long executionTaskId, Long parseTaskId) {
        if (executionTaskId == null || parseTaskId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        attemptMapper.update(null, new LambdaUpdateWrapper<TaxExecutionTaskAttempt>()
                .eq(TaxExecutionTaskAttempt::getExecutionTaskId, executionTaskId)
                .eq(TaxExecutionTaskAttempt::getParseTaskId, parseTaskId)
                .set(TaxExecutionTaskAttempt::getStatus, ExecutionTaskStatusEnum.COMPLETED.name())
                .set(TaxExecutionTaskAttempt::getErrorMessage, null)
                .set(TaxExecutionTaskAttempt::getFinishedAt, now)
                .set(TaxExecutionTaskAttempt::getUpdatedAt, now));
        taskMapper.update(null, new LambdaUpdateWrapper<TaxExecutionTask>()
                .eq(TaxExecutionTask::getId, executionTaskId)
                .eq(TaxExecutionTask::getParseTaskId, parseTaskId)
                .set(TaxExecutionTask::getStatus, ExecutionTaskStatusEnum.COMPLETED.name())
                .set(TaxExecutionTask::getErrorMessage, null)
                .set(TaxExecutionTask::getUpdatedAt, now));
    }

    /**
     * 将执行任务标记为失败，错误信息最长保留 1000 个字符。
     *
     * @param executionTaskId 执行任务 ID，为空时忽略
     * @param parseTaskId 本次失败的内部解析任务 ID
     * @param message 解析失败原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long executionTaskId, Long parseTaskId, String message) {
        if (executionTaskId == null || parseTaskId == null) {
            return;
        }
        String normalized = message == null ? "解析任务执行失败" : message;
        if (normalized.length() > 1000) {
            normalized = normalized.substring(0, 1000);
        }
        LocalDateTime now = LocalDateTime.now();
        attemptMapper.update(null, new LambdaUpdateWrapper<TaxExecutionTaskAttempt>()
                .eq(TaxExecutionTaskAttempt::getExecutionTaskId, executionTaskId)
                .eq(TaxExecutionTaskAttempt::getParseTaskId, parseTaskId)
                .set(TaxExecutionTaskAttempt::getStatus, ExecutionTaskStatusEnum.FAILED.name())
                .set(TaxExecutionTaskAttempt::getErrorMessage, normalized)
                .set(TaxExecutionTaskAttempt::getFinishedAt, now)
                .set(TaxExecutionTaskAttempt::getUpdatedAt, now));
        taskMapper.update(null, new LambdaUpdateWrapper<TaxExecutionTask>()
                .eq(TaxExecutionTask::getId, executionTaskId)
                .eq(TaxExecutionTask::getParseTaskId, parseTaskId)
                .set(TaxExecutionTask::getStatus, ExecutionTaskStatusEnum.FAILED.name())
                .set(TaxExecutionTask::getErrorMessage, normalized)
                .set(TaxExecutionTask::getUpdatedAt, now));
    }
}
