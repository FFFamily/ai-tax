package com.rcszh.tax.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.rcszh.tax.dto.CreateDocumentTaskDto;
import com.rcszh.tax.dto.executiontask.ExecutionTaskDetailResponse;
import com.rcszh.tax.entity.task.TaxExecutionTask;
import com.rcszh.tax.entity.task.TaxExecutionTaskAttempt;
import com.rcszh.tax.entity.task.TaxExecutionTaskFile;
import com.rcszh.tax.enums.ExecutionTaskStatusEnum;
import com.rcszh.tax.mapper.TaxExecutionTaskAttemptMapper;
import com.rcszh.tax.mapper.TaxExecutionTaskFileMapper;
import com.rcszh.tax.mapper.TaxExecutionTaskMapper;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.service.task.ExecutionTaskService;
import com.rcszh.tax.workflow.DocumentWorkflowRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionTaskServiceTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rerunsCompletedTaskAfterDeletingPreviousDerivedData() {
        TaxExecutionTaskMapper taskMapper = mock(TaxExecutionTaskMapper.class);
        TaxExecutionTaskAttemptMapper attemptMapper = mock(TaxExecutionTaskAttemptMapper.class);
        TaxExecutionTaskFileMapper fileMapper = mock(TaxExecutionTaskFileMapper.class);
        DocumentTaskServer documentTaskServer = mock(DocumentTaskServer.class);
        StorageService storageService = mock(StorageService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        TaxExecutionTask task = completedTask();
        TaxExecutionTaskFile file = sourceFile();
        TaxExecutionTaskAttempt previousAttempt = new TaxExecutionTaskAttempt();
        previousAttempt.setExecutionTaskId(task.getId());
        previousAttempt.setParseTaskId(task.getParseTaskId());
        previousAttempt.setAttemptNo(1);
        previousAttempt.setStatus(ExecutionTaskStatusEnum.COMPLETED.name());
        previousAttempt.setStartedAt(task.getSubmittedAt());

        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(fileMapper.selectList(any(Wrapper.class))).thenReturn(List.of(file));
        when(attemptMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(previousAttempt))
                .thenReturn(List.of());
        when(documentTaskServer.createTaskWithItems(any(CreateDocumentTaskDto.class)))
                .thenReturn(new DocumentTaskServer.CreatedTask(100L, List.of(200L)));
        when(storageService.buildExecutionFileUrl(anyLong(), anyLong(), any(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn("/execution-tasks/1/files/20");

        ExecutionTaskService service = new ExecutionTaskService();
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "attemptMapper", attemptMapper);
        ReflectionTestUtils.setField(service, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(service, "documentTaskServer", documentTaskServer);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "workflowRegistry", new DocumentWorkflowRegistry());
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);

        ExecutionTaskDetailResponse result = service.retry(task.getId());

        assertThat(result.getStatus()).isEqualTo(ExecutionTaskStatusEnum.PROCESSING.name());
        assertThat(result.getParseTaskId()).isEqualTo(100L);
        verify(fileMapper).update(isNull(), any(Wrapper.class));
        verify(documentTaskServer).deleteTasks(Set.of(10L));
        verify(attemptMapper).delete(any(Wrapper.class));
        verify(eventPublisher).publishEvent(new DocumentParseTaskCreatedEvent(100L, task.getId()));
        assertThat(file.getParseTaskItemId()).isEqualTo(200L);
    }

    private TaxExecutionTask completedTask() {
        LocalDateTime now = LocalDateTime.now();
        TaxExecutionTask task = new TaxExecutionTask();
        task.setId(1L);
        task.setIncomeType("INTEREST_INCOME");
        task.setStatus(ExecutionTaskStatusEnum.COMPLETED.name());
        task.setParseTaskId(10L);
        task.setSubmittedAt(now.minusMinutes(2));
        task.setCreatedAt(now.minusHours(1));
        task.setUpdatedAt(now);
        return task;
    }

    private TaxExecutionTaskFile sourceFile() {
        TaxExecutionTaskFile file = new TaxExecutionTaskFile();
        file.setId(20L);
        file.setExecutionTaskId(1L);
        file.setMaterialType("BANK_STATEMENT");
        file.setOriginalFileName("statement.xlsx");
        file.setStoragePath("execution-tasks/1/BANK_STATEMENT/20.xlsx");
        file.setExtension("xlsx");
        file.setSizeBytes(100L);
        file.setParseTaskItemId(11L);
        file.setCreatedAt(LocalDateTime.now());
        file.setUpdatedAt(file.getCreatedAt());
        return file;
    }
}
