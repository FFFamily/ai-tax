package com.rcszh.tax.postprocess;

import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.workflow.DocumentWorkflow;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * {@link RecordPostProcessor} 的统一编排服务。
 *
 * <p>解析层负责抽取结构化 records，本服务负责按优先级调用可插拔处理器，完成业务计算、
 * 数据整形和质量校验。单个处理器失败不会中断后续处理，失败信息会写入解析结果 warnings。</p>
 */
@Component
public class RecordPostProcessService {
    /**
     * Spring 容器中注册的全部记录后处理器；执行前会按 {@link RecordPostProcessor#order()} 排序。
     */
    @Resource
    private List<RecordPostProcessor> processors;

    /**
     * 对解析结果执行完整的后处理链。
     *
     * <p>仅执行 {@code supports=true} 的处理器；处理器之间的临时数据不会进入最终解析结果。</p>
     *
     * @param parseResult 待加工的 AI 解析结果；为 {@code null} 时直接返回
     * @param taskItem 当前文档任务项，透传给各处理器
     * @param workflow 固定文档流程，透传给各处理器
     */
    public void postProcess(AIParseResult parseResult,
                            DocumentTaskItem taskItem,
                            DocumentWorkflow workflow) {
        if (parseResult == null) {
            return;
        }
        RecordPostProcessContext context = new RecordPostProcessContext();
        // 创建有序快照，避免依赖 Spring 注入集合的原始顺序。
        List<RecordPostProcessor> orderedProcessors = processors == null ? List.of() : processors.stream()
                .sorted(Comparator.comparingInt(RecordPostProcessor::order))
                .toList();
        for (RecordPostProcessor processor : orderedProcessors) {
            if (!processor.supports(parseResult, taskItem, workflow, context)) {
                continue;
            }
            try {
                processor.process(parseResult, taskItem, workflow, context);
            } catch (Exception e) {
                parseResult.getWarnings().add("postProcess[" + processor.name() + "]失败：" + e.getMessage());
            }
        }
    }
}
