package com.rcszh.tax.postprocess;

import com.rcszh.tax.entity.AIParseResult;

import java.util.List;
import java.util.Map;

/**
 * records 二次加工处理器（可扩展）。
 *
 * 约定：
 * - supports 只做快速判断（避免全量扫描）
 * - process 返回新的 records 列表（可过滤、可补充、可替换）
 */
public interface RecordPostProcessor {
    /**
     * 处理器执行顺序，越小越先执行。
     */
    default int order() {
        return 1000;
    }

    /**
     * 处理器名称（用于日志/追踪）。
     */
    String name();

    /**
     * 判断当前解析结果/任务项/records 是否需要该处理器介入（建议只做快速嗅探，避免全量扫描）。
     */
    boolean supports(AIParseResult parseResult, Map<String, Object> taskItem, Map<String, Object> document);

    /**
     * 对 records 进行二次加工
     */
    void process(AIParseResult parseResult, Map<String, Object> taskItem, Map<String, Object> document);
}
