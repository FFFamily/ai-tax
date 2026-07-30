package com.rcszh.tax.postprocess.dividend;

import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.server.DocumentTaskServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DividendQualityPostProcessorTest {

    private final DividendQualityPostProcessor processor = new DividendQualityPostProcessor();

    @Test
    void shouldNormalizeAmountsAndMarkDuplicateReview() {
        AIParseResult parseResult = new AIParseResult();
        Map<String, Object> record1 = new LinkedHashMap<>();
        record1.put("dividendDate", "2025/02/20");
        record1.put("payer", "Microsoft");
        record1.put("currency", "usd");
        record1.put("netAmount", new BigDecimal("-66.66"));
        record1.put("grossAmount", new BigDecimal("-66.66"));
        record1.put("confidence", new BigDecimal("0.90"));
        record1.put("evidenceRowIds", List.of("r1"));

        Map<String, Object> record2 = new LinkedHashMap<>(record1);
        record2.put("evidenceRowIds", List.of("r2"));

        parseResult.getGlobalParam().put(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS, List.of(record1, record2));
        parseResult.setRecords(List.of(record1, record2));

        Map<String, Object> taskItem = new LinkedHashMap<>();
        taskItem.put(DocumentTaskServer.Item.ROUTE_SUMMARY, Map.of(
                "confidence", new BigDecimal("0.90")
        ));

        processor.process(parseResult, taskItem, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reviewed = (List<Map<String, Object>>) parseResult.getGlobalParam()
                .get(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS);
        assertEquals(new BigDecimal("66.66"), reviewed.getFirst().get("netAmount"));
        assertEquals("USD", reviewed.getFirst().get("currency"));
        assertEquals("2025-02-20", reviewed.getFirst().get("dividendDate"));
        assertTrue(Boolean.TRUE.equals(parseResult.getGlobalParam().get("needHumanReview")));
        assertTrue(parseResult.getWarnings().stream().anyMatch(item -> item.contains("人工复核")));
    }
}
