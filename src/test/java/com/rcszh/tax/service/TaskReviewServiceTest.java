package com.rcszh.tax.service;

import cn.hutool.json.JSONUtil;
import com.rcszh.tax.dto.TaskItemReviewRequest;
import com.rcszh.tax.server.DocumentTaskServer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskReviewServiceTest {

    @Test
    void shouldWriteReviewedRecordsBackToTaskItem() {
        StubDocumentTaskServer documentTaskServer = new StubDocumentTaskServer();
        StubReviewLearningService reviewLearningService = new StubReviewLearningService();
        TaskReviewService service = new TaskReviewService(documentTaskServer, reviewLearningService);

        TaskItemReviewRequest request = new TaskItemReviewRequest();
        request.setNeedHumanReview(false);
        request.setReviewer("tester");
        request.setComment("checked");
        request.setResolvedDocumentId("DOC_DIVIDEND_BANK_V1");
        request.setReviewReasons(List.of("人工确认正确"));
        request.setRecords(List.of(Map.of(
                "dividendDate", "2025-01-18",
                "payer", "Apple Inc",
                "currency", "USD",
                "netAmount", "80.00"
        )));

        Map<String, Object> result = service.reviewTaskItem("item-1", request);

        assertNotNull(result);
        assertEquals(false, result.get(DocumentTaskServer.Item.NEED_HUMAN_REVIEW));
        assertTrue(result.get(DocumentTaskServer.Item.CHANGE_RESULT).toString().contains("Apple Inc"));
        assertTrue(reviewLearningService.called);
    }

    private static class StubDocumentTaskServer extends DocumentTaskServer {
        private final Map<String, Object> item = new LinkedHashMap<>();

        private StubDocumentTaskServer() {
            item.put(Item.FIELD_ID, "item-1");
            item.put(Item.TASK_ID, "task-1");
            item.put(Item.REQUESTED_DOCUMENT_TYPE, "DIVIDEND");
            item.put(Item.RESOLVED_DOCUMENT_ID, "DOC_DIVIDEND_BANK_V1");
            item.put(Item.ROUTE_SUMMARY, Map.of("documentId", "DOC_DIVIDEND_BANK_V1", "confidence", 1));
            item.put(Item.CHANGE_RESULT, JSONUtil.toJsonStr(Map.of(
                    "records", List.of(),
                    "globalParam", Map.of()
            )));
        }

        @Override
        public Map<String, Object> getTaskItemById(String itemId) {
            return new LinkedHashMap<>(item);
        }

        @Override
        public void updateTaskItem(Map<String, Object> taskItem) {
            item.clear();
            item.putAll(taskItem);
        }
    }

    private static class StubReviewLearningService extends ReviewLearningService {
        private boolean called;

        private StubReviewLearningService() {
            super(null);
        }

        @Override
        public void saveLearningFromReview(Map<String, Object> taskItem,
                                           List<Map<String, Object>> reviewedRecords,
                                           String reviewer,
                                           String comment) {
            called = true;
            assertEquals("tester", reviewer);
            assertEquals("checked", comment);
            assertEquals(1, reviewedRecords.size());
        }
    }
}
