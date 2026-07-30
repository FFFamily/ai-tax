package com.rcszh.tax.service;

import com.rcszh.tax.mapper.TaxExecutionTaskFileMapper;
import com.rcszh.tax.mapper.TaxExecutionTaskMapper;
import com.rcszh.tax.server.DocumentTaskServer;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExecutionTaskServiceDtoTest {
    @Test
    void shouldExposeTypedOptionsForAllIncomeTypes() {
        ExecutionTaskService service = new ExecutionTaskService(
                mock(TaxExecutionTaskMapper.class),
                mock(TaxExecutionTaskFileMapper.class),
                mock(StorageService.class),
                mock(DocumentTaskServer.class),
                mock(ApplicationEventPublisher.class)
        );

        var response = service.options();

        assertThat(response.getIncomeTypes()).hasSize(15);
        assertThat(response.getIncomeTypes().get(0).getCode()).isEqualTo("SALARY");
        assertThat(response.getIncomeTypes().get(14).getCode()).isEqualTo("OTHER_INCOME");
        assertThat(response.getIncomeTypes().get(11).getMaterials()).hasSize(5);
        assertThat(response.getMaxFileSize()).isEqualTo(50L * 1024 * 1024);
    }
}
