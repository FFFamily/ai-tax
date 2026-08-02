package com.rcszh.tax.parser;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.rcszh.tax.ai.DeepSeekAi;
import com.rcszh.tax.dto.ExcelParseResult;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.ir.ParsePreparationResult;
import com.rcszh.tax.ir.ParsePreparationService;
import com.rcszh.tax.util.ExcelUtil;
import com.rcszh.tax.workflow.DocumentWorkflow;
import com.rcszh.tax.workflow.DocumentWorkflowRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExcelParserTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesExcelDirectlyFromLocalPath() {
        Path excelPath = tempDir.resolve("statement.xlsx");
        EasyExcel.write(excelPath.toFile())
                .head(List.of(List.of("date"), List.of("amount")))
                .sheet("transactions")
                .doWrite(List.of(List.of("2026-01-01", "100.00")));

        ParsePreparationService preparationService = mock(ParsePreparationService.class);
        ParsePreparationResult preparation = new ParsePreparationResult();
        when(preparationService.prepareExcel(anyList())).thenReturn(preparation);

        AIParseResult expected = new AIParseResult();
        DeepSeekAi deepSeekAi = mock(DeepSeekAi.class);
        when(deepSeekAi.chat(anyList(), anyString(), isNull(),
                org.mockito.ArgumentMatchers.any(DocumentWorkflow.class))).thenReturn(expected);

        ExcelParser parser = new ExcelParser();
        ReflectionTestUtils.setField(parser, "parsePreparationService", preparationService);
        ReflectionTestUtils.setField(parser, "workflowRegistry", new DocumentWorkflowRegistry());
        ReflectionTestUtils.setField(parser, "deepSeekAi", deepSeekAi);

        DocumentTaskItem item = new DocumentTaskItem();
        item.setId(7L);
        item.setWorkflowCode("INTEREST_INCOME__BANK_STATEMENT");
        item.setFileUrl("http://127.0.0.1/files/7?fileName=statement.xlsx");
        item.setLocalFilePath(excelPath);

        AIParseResult actual = parser.doParse(item);

        assertThat(actual).isSameAs(expected);
        verify(preparationService).prepareExcel(org.mockito.ArgumentMatchers.<List<ExcelParseResult>>any());
        verify(deepSeekAi).chat(anyList(), anyString(), isNull(),
                org.mockito.ArgumentMatchers.any(DocumentWorkflow.class));
    }

    @Test
    void readsAllSheets() {
        Path excelPath = tempDir.resolve("multi-sheet.xlsx");
        List<List<String>> head = List.of(List.of("date"), List.of("amount"));
        try (ExcelWriter writer = EasyExcel.write(excelPath.toFile()).build()) {
            WriteSheet transactions = EasyExcel.writerSheet(0, "transactions").head(head).build();
            WriteSheet summary = EasyExcel.writerSheet(1, "summary").head(head).build();
            writer.write(List.of(List.of("2026-01-01", "100.00")), transactions);
            writer.write(List.of(List.of("2026-01-31", "250.00")), summary);
        }

        List<ExcelParseResult> results = ExcelUtil.readExcel(excelPath.toFile());

        assertThat(results).extracting(ExcelParseResult::getSheetName)
                .containsExactly("transactions", "summary");
        assertThat(results).extracting(result -> result.getExcelData().get("amount"))
                .containsExactly("100.00", "250.00");
    }

    @Test
    void rejectsMissingLocalExcelFile() {
        ExcelParser parser = new ExcelParser();
        DocumentTaskItem item = new DocumentTaskItem();
        item.setFileUrl("http://127.0.0.1/files/8?fileName=missing.xlsx");
        item.setLocalFilePath(tempDir.resolve("missing.xlsx"));

        assertThatThrownBy(() -> parser.doParse(item))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Excel 本地文件不存在");
    }
}
