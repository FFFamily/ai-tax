package com.rcszh.tax.ir;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 按表格行数和文本长度切分统一文档，同时保留来源定位信息。 */
@Component
public class DocumentChunker {
    private static final int TEXT_CHARS_PER_UNIT = 1000;

    public List<DocumentChunk> chunk(ParsedDocument document, int maxUnits) {
        if (document == null) {
            return List.of();
        }
        int limit = maxUnits <= 0 ? 20 : maxUnits;
        List<Part> parts = new ArrayList<>();
        for (DataTable table : document.getTables()) {
            addTableParts(parts, table, limit);
        }
        for (TextBlock textBlock : document.getTextBlocks()) {
            addTextParts(parts, textBlock, limit);
        }
        parts.sort(Comparator.comparingInt(Part::blockIndex).thenComparingInt(Part::partIndex));

        List<DocumentChunk> result = new ArrayList<>();
        DocumentChunk current = new DocumentChunk();
        int currentUnits = 0;
        for (Part part : parts) {
            if (!current.isEmpty() && currentUnits + part.units() > limit) {
                addChunk(result, current);
                current = new DocumentChunk();
                currentUnits = 0;
            }
            if (part.table() != null) {
                current.getTables().add(part.table());
            } else {
                current.getTextBlocks().add(part.textBlock());
            }
            currentUnits += part.units();
        }
        if (!current.isEmpty()) {
            addChunk(result, current);
        }
        return result;
    }

    private void addTableParts(List<Part> parts, DataTable table, int limit) {
        if (table == null) {
            return;
        }
        List<DataRow> rows = table.getRows() == null ? List.of() : table.getRows();
        if (rows.isEmpty()) {
            parts.add(new Part(blockIndex(table.getBlockIndex()), 0, 1, copyTable(table, List.of()), null));
            return;
        }
        int partIndex = 0;
        for (int start = 0; start < rows.size(); start += limit) {
            int end = Math.min(start + limit, rows.size());
            List<DataRow> slice = new ArrayList<>(rows.subList(start, end));
            parts.add(new Part(blockIndex(table.getBlockIndex()), partIndex++, slice.size(), copyTable(table, slice), null));
        }
    }

    private void addTextParts(List<Part> parts, TextBlock block, int limit) {
        if (block == null || block.getText() == null || block.getText().isBlank()) {
            return;
        }
        int maxChars = limit * TEXT_CHARS_PER_UNIT;
        String text = block.getText();
        int partIndex = 0;
        for (int start = 0; start < text.length(); start += maxChars) {
            int end = Math.min(start + maxChars, text.length());
            TextBlock slice = copyTextBlock(block, text.substring(start, end));
            int units = Math.max(1, (slice.getText().length() + TEXT_CHARS_PER_UNIT - 1) / TEXT_CHARS_PER_UNIT);
            parts.add(new Part(blockIndex(block.getBlockIndex()), partIndex++, units, null, slice));
        }
    }

    private DataTable copyTable(DataTable source, List<DataRow> rows) {
        DataTable copy = new DataTable();
        copy.setTableId(source.getTableId());
        copy.setSourceType(source.getSourceType());
        copy.setTitle(source.getTitle());
        copy.setPageIndex(source.getPageIndex());
        copy.setTableIndex(source.getTableIndex());
        copy.setBlockIndex(source.getBlockIndex());
        copy.setHeaders(source.getHeaders() == null ? new ArrayList<>() : new ArrayList<>(source.getHeaders()));
        copy.setRows(new ArrayList<>(rows));
        copy.setMetadata(source.getMetadata() == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(source.getMetadata()));
        return copy;
    }

    private TextBlock copyTextBlock(TextBlock source, String text) {
        TextBlock copy = new TextBlock();
        copy.setBlockId(source.getBlockId());
        copy.setSourceType(source.getSourceType());
        copy.setType(source.getType());
        copy.setPageIndex(source.getPageIndex());
        copy.setBlockIndex(source.getBlockIndex());
        copy.setText(text);
        copy.setMetadata(source.getMetadata() == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(source.getMetadata()));
        return copy;
    }

    private int blockIndex(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private void addChunk(List<DocumentChunk> result, DocumentChunk chunk) {
        chunk.setChunkIndex(result.size());
        result.add(chunk);
    }

    private record Part(int blockIndex, int partIndex, int units, DataTable table, TextBlock textBlock) {
    }
}
