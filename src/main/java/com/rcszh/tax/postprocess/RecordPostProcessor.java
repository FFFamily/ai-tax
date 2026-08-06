package com.rcszh.tax.postprocess;

import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.workflow.DocumentWorkflow;

/**
 * AI 解析记录的二次加工扩展点。
 *
 * <p>所有 Spring 容器中的实现类会由 {@link RecordPostProcessService} 自动收集，按
 * {@link #order()} 从小到大依次执行。处理器可读取解析结果、任务上下文和文档元数据，
 * 并直接修改 {@link AIParseResult} 中的 records 或 warnings。</p>
 *
 * <p>实现约定：</p>
 * <ul>
 *     <li>{@link #supports(AIParseResult, DocumentTaskItem, DocumentWorkflow, RecordPostProcessContext)} 只做低成本判断。</li>
 *     <li>{@link #process(AIParseResult, DocumentTaskItem, DocumentWorkflow, RecordPostProcessContext)} 负责实际加工。</li>
 *     <li>处理器之间通过 {@link RecordPostProcessContext} 传递仅在本次调用有效的中间结果。</li>
 * </ul>
 */
public interface RecordPostProcessor {
    /**
     * 获取处理器执行优先级，数值越小越先执行。
     *
     * @return 执行优先级，默认值为 1000
     */
    default int order() {
        return 1000;
    }

    /**
     * 获取处理器的稳定名称，用于执行记录、日志和告警定位。
     *
     * @return 处理器唯一名称
     */
    String name();

    /**
     * 判断当前上下文是否需要执行此处理器。
     *
     * @param parseResult AI 解析结果，包含 records 和 warnings
     * @param taskItem 当前文档任务项，包含路由结果和预处理文档等上下文
     * @param workflow 固定文档流程
     * @param context 本次后处理调用的临时上下文
     * @return {@code true} 表示执行当前处理器
     */
    boolean supports(AIParseResult parseResult,
                     DocumentTaskItem taskItem,
                     DocumentWorkflow workflow,
                     RecordPostProcessContext context);

    /**
     * 对解析记录执行二次加工，并将结果写回 {@code parseResult}。
     *
     * @param parseResult 待加工的 AI 解析结果
     * @param taskItem 当前文档任务项
     * @param workflow 固定文档流程
     * @param context 本次后处理调用的临时上下文
     */
    void process(AIParseResult parseResult,
                 DocumentTaskItem taskItem,
                 DocumentWorkflow workflow,
                 RecordPostProcessContext context);
}
