package com.rcszh.tax.dto.executiontask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionTaskResultResponseTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepLegacyResultFieldNamesForFrontendCompatibility() {
        ExecutionTaskRouteSummaryResponse routeSummary = new ExecutionTaskRouteSummaryResponse();
        routeSummary.setVariant("GENERIC");

        ExecutionTaskResultItemResponse item = new ExecutionTaskResultItemResponse();
        item.setId(2L);
        item.setTaskId(1L);
        item.setChangeResult("{\"records\":[]}");
        item.setNeedHumanReview(Boolean.TRUE);
        item.setRouteSummary(routeSummary);

        ExecutionTaskResultResponse response = new ExecutionTaskResultResponse();
        response.setId(1L);
        response.setStatus("SUCCESS");
        response.setItems(List.of(item));

        JsonNode json = objectMapper.valueToTree(response);
        JsonNode resultItem = json.path("items").path(0);

        assertThat(resultItem.path("task_id").asLong()).isEqualTo(1L);
        assertThat(resultItem.path("change_result").asText()).isEqualTo("{\"records\":[]}");
        assertThat(resultItem.path("need_human_review").asBoolean()).isTrue();
        assertThat(resultItem.path("route_summary").path("variant").asText()).isEqualTo("GENERIC");
        assertThat(resultItem.has("taskId")).isFalse();
    }
}
