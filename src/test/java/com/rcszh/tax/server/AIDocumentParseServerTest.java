package com.rcszh.tax.server;

import com.rcszh.tax.entity.AIParseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AIDocumentParseServerTest {

    @Test
    void shouldParseGlobalParamFromAiResponse() {
        String response = """
                {
                  "warnings": ["ok"],
                  "records": [{"summary":"Dividend"}],
                  "errorRecords": [],
                  "globalParam": {
                    "routeSummary": {"documentId":"DOC_DIVIDEND_BANK_V1"},
                    "needHumanReview": true
                  }
                }
                """;

        AIParseResult result = AIDocumentParseServer.parseAIResponse(response);

        assertNotNull(result.getGlobalParam());
        assertEquals(true, result.getGlobalParam().get("needHumanReview"));
        assertNotNull(result.getGlobalParam().get("routeSummary"));
        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getWarnings().size());
    }
}
