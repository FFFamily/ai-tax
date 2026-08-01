package com.rcszh.tax.parser;

import com.rcszh.tax.entity.task.DocumentTaskItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentParserRegistry {

    private final List<BaseParser> parsers;

    public DocumentParserRegistry(List<BaseParser> parsers) {
        this.parsers = parsers;
    }


    public BaseParser resolve(DocumentTaskItem item) {
        List<BaseParser> matched = parsers.stream()
                .filter(parser -> parser.supports(item))
                .toList();

        if (matched.isEmpty()) {
            throw new IllegalArgumentException("不支持的文件类型: " + item.getFileUrl());
        }

        if (matched.size() > 1) {
            throw new IllegalStateException("存在多个匹配的解析器: " + item.getFileUrl());
        }

        return matched.getFirst();
    }
}
