package com.rcszh.tax.ir;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void splitsLargeTablesByRowsWithoutLossAndRepeatsHeaders() {
        DataTable table = new DataTable();
        table.setTableId("excel:Sheet1");
        table.setBlockIndex(0);
        table.setHeaders(List.of("Date", "Amount"));
        for (int index = 1; index <= 5; index++) {
            DataRow row = new DataRow();
            row.setRowIndex(index);
            row.setCells(new ArrayList<>(List.of("2026-01-0" + index, String.valueOf(index))));
            table.getRows().add(row);
        }
        ParsedDocument document = new ParsedDocument();
        document.getTables().add(table);

        List<DocumentChunk> chunks = chunker.chunk(document, 2);

        assertEquals(3, chunks.size());
        assertEquals(List.of(2, 2, 1), chunks.stream()
                .map(chunk -> chunk.getTables().getFirst().getRows().size())
                .toList());
        assertTrue(chunks.stream().allMatch(chunk ->
                chunk.getTables().getFirst().getHeaders().equals(List.of("Date", "Amount"))));
        assertEquals(List.of(1, 2, 3, 4, 5), chunks.stream()
                .flatMap(chunk -> chunk.getTables().getFirst().getRows().stream())
                .map(DataRow::getRowIndex)
                .toList());
    }

    @Test
    void keepsLongTextBySplittingItWithoutLoss() {
        String source = "x".repeat(2500);
        TextBlock block = new TextBlock();
        block.setBlockId("pdf:p1:b0");
        block.setBlockIndex(0);
        block.setText(source);
        ParsedDocument document = new ParsedDocument();
        document.getTextBlocks().add(block);

        String reconstructed = chunker.chunk(document, 2).stream()
                .flatMap(chunk -> chunk.getTextBlocks().stream())
                .map(TextBlock::getText)
                .reduce("", String::concat);

        assertEquals(source, reconstructed);
    }
}
