package com.rcszh.tax.postprocess;

import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 对 AI 解析产出的 records 进行二次加工（可插拔、可扩展）。
 *
 * 说明：
 * - 解析层（AI）负责“抽取结构化 records”
 * - 二次加工层负责“基于 records 做业务计算/整形，产出可申报/可导出的 records”
 */
@Component
public class RecordPostProcessService {
    @Resource
    private List<RecordPostProcessor> processors;

    /**
     * 对 parseResult.records 进行二次加工：
     * - 依次执行 supports=true 的处理器
     * - 将最终结果回写到 parseResult.records
     * - 将已执行的处理器名称写入 globalParam.postProcessApplied（便于追踪）
     */
    public void postProcess(AIParseResult parseResult, DocumentTaskItem taskItem, Map<String, Object> document) {
        if (parseResult == null) {
            return;
        }
        List<String> applied = new ArrayList<>();
        List<RecordPostProcessor> orderedProcessors = processors == null ? List.of() : processors.stream()
                .sorted(Comparator.comparingInt(RecordPostProcessor::order))
                .toList();
        for (RecordPostProcessor processor : orderedProcessors) {
            if (!processor.supports(parseResult, taskItem,document)) {
                continue;
            }
            try {
                processor.process(parseResult, taskItem, document);
                applied.add(processor.name());
            } catch (Exception e) {
                parseResult.getWarnings().add("postProcess[" + processor.name() + "]失败：" + e.getMessage());
            }
        }
        if (!applied.isEmpty()) {
            parseResult.getGlobalParam().put("postProcessApplied", applied);
        }
    }
}
