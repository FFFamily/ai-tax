package com.rcszh.tax.postprocess.dividend;

import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.ir.TransactionLine;
import com.rcszh.tax.postprocess.RecordPostProcessor;
import com.rcszh.tax.postprocess.RecordPostProcessContext;
import com.rcszh.tax.postprocess.dividend.model.DividendCandidateRecord;
import com.rcszh.tax.postprocess.dividend.service.DividendCandidateService;
import com.rcszh.tax.workflow.DocumentWorkflow;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 股息处理链的候选召回阶段。
 *
 * <p>从任务项已标准化的交易流水中识别疑似股息收入和预扣税记录，并写入临时处理上下文。
 * 本处理器顺序为 50，
 * 位于专项抽取（60）和质量校验（70）之前。</p>
 */
@Component
public class DividendCandidatePostProcessor implements RecordPostProcessor {
    /** 股息候选识别与评分服务。 */
    @Resource
    private DividendCandidateService dividendCandidateService;

    /**
     * {@inheritDoc}
     *
     * @return 固定返回 50，确保候选召回先于抽取和质检
     */
    @Override
    public int order() {
        return 50;
    }

    /**
     * {@inheritDoc}
     *
     * @return 用于追踪的处理器名称 {@code dividend-candidate-recall}
     */
    @Override
    public String name() {
        return "dividend-candidate-recall";
    }

    /**
     * 在任务项已准备交易流水时启用候选召回。
     *
     * @param parseResult AI 解析结果
     * @param taskItem 包含标准化交易流水的任务项
     * @param workflow 固定文档流程
     * @return 有可分析流水且具备股息提示或有效解析结果时返回 {@code true}
     */
    @Override
    public boolean supports(AIParseResult parseResult, DocumentTaskItem taskItem,
                            DocumentWorkflow workflow, RecordPostProcessContext context) {
        if (taskItem == null) {
            return false;
        }
        if (taskItem.getPreparedTransactionLines() == null || taskItem.getPreparedTransactionLines().isEmpty()) {
            return false;
        }
        return workflow != null && workflow.supports("DIVIDEND");
    }

    /**
     * 召回股息候选，并将候选明细写入本次后处理的临时上下文。
     *
     * @param parseResult 用于承载处理提示信息的解析结果
     * @param taskItem 提供预处理交易流水的任务项
     * @param workflow 固定文档流程，本方法当前不直接使用
     */
    @Override
    public void process(AIParseResult parseResult, DocumentTaskItem taskItem,
                        DocumentWorkflow workflow, RecordPostProcessContext context) {
        // 上游预处理后的统一流水，是候选识别的唯一输入。
        List<TransactionLine> transactionLines = taskItem.getPreparedTransactionLines();
        if (transactionLines == null || transactionLines.isEmpty()) {
            return;
        }
        List<DividendCandidateRecord> candidates = dividendCandidateService.collectCandidates(transactionLines);
        if (candidates.isEmpty()) {
            return;
        }
        context.setDividendCandidates(candidates);
        parseResult.getWarnings().add("已召回疑似股息相关流水 " + candidates.size() + " 条，待进入专项抽取。");
    }

}
