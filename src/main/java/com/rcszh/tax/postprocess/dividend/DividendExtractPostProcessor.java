package com.rcszh.tax.postprocess.dividend;

import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.postprocess.RecordPostProcessor;
import com.rcszh.tax.postprocess.RecordPostProcessContext;
import com.rcszh.tax.postprocess.dividend.model.DividendCandidateRecord;
import com.rcszh.tax.postprocess.dividend.model.DividendExtractRecord;
import com.rcszh.tax.postprocess.dividend.service.DividendExtractService;
import com.rcszh.tax.workflow.DocumentWorkflow;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 股息处理链的专项抽取阶段。
 *
 * <p>读取临时上下文中的候选记录，聚合后直接写入最终 records。本处理器顺序为 60。</p>
 */
@Component
public class DividendExtractPostProcessor implements RecordPostProcessor {
    /** 负责规则聚合及可选 AI 增强的股息抽取服务。 */
    @Resource
    private DividendExtractService dividendExtractService;

    /** @return 固定返回 60，确保在候选召回之后、质量校验之前执行 */
    @Override
    public int order() {
        return 60;
    }

    /** @return 用于追踪的处理器名称 {@code dividend-extract} */
    @Override
    public String name() {
        return "dividend-extract";
    }

    /**
     * 判断是否已产生股息候选，且文档类型明确指向股息业务。
     *
     * @param parseResult 包含候选中间结果的解析结果
     * @param taskItem 当前文档任务项
     * @param workflow 固定文档流程
     * @return 存在候选且文档类型包含股息提示时返回 {@code true}
     */
    @Override
    public boolean supports(AIParseResult parseResult, DocumentTaskItem taskItem,
                            DocumentWorkflow workflow, RecordPostProcessContext context) {
        if (context.getDividendCandidates().isEmpty()) {
            return false;
        }
        return workflow != null && workflow.supports("DIVIDEND");
    }

    /**
     * 对候选记录执行专项抽取，并将结果写入最终 records。
     *
     * @param parseResult 承载最终抽取结果的解析结果
     * @param taskItem 当前文档任务项
     * @param workflow 固定文档流程，本方法当前不直接使用
     */
    @Override
    public void process(AIParseResult parseResult, DocumentTaskItem taskItem,
                        DocumentWorkflow workflow, RecordPostProcessContext context) {
        List<DividendCandidateRecord> candidates = context.getDividendCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        List<DividendExtractRecord> extracted = dividendExtractService.extract(candidates);
        if (extracted.isEmpty()) {
            return;
        }
        List<Map<String, Object>> extractedMaps = extracted.stream()
                .map(DividendExtractRecord::toMap)
                .toList();
        parseResult.setRecords(extractedMaps);
        context.setDividendRecordsPrepared(true);
        parseResult.getWarnings().add("已生成股息专项记录 " + extractedMaps.size() + " 条。");
    }

}
