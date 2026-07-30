package com.rcszh.tax.service;

import com.rcszh.tax.entity.ReviewLearning;
import com.rcszh.tax.mapper.ReviewLearningMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewLearningServiceTest {

    @Test
    void shouldExtractSuggestedKeywordsAndFewShotExamples() {
        ReviewLearningMapper mapper = mock(ReviewLearningMapper.class);
        ReviewLearning learning = new ReviewLearning();
        learning.setResolvedDocumentId("DOC_DIVIDEND_BANK_V1");
        learning.setRequestedDocumentType("DIVIDEND");
        learning.setSuggestedMatchRule("""
                {"anyKeywords":["Apple Inc","Dividend","USD"]}
                """);
        learning.setFewShotExample("""
                {"reviewedRecords":[{"payer":"Apple Inc","summary":"Dividend"}]}
                """);
        when(mapper.selectList(any())).thenReturn(List.of(learning));

        ReviewLearningService service = new ReviewLearningService(mapper);

        List<String> keywords = service.listSuggestedKeywords("DOC_DIVIDEND_BANK_V1", "DIVIDEND", 10);
        List<Map<String, Object>> examples = service.listFewShotExamples("DOC_DIVIDEND_BANK_V1", "DIVIDEND", 5);

        assertEquals(List.of("Apple Inc", "Dividend", "USD"), keywords);
        assertFalse(examples.isEmpty());
    }
}
