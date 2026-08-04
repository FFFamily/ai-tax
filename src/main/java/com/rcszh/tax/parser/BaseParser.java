package com.rcszh.tax.parser;

import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.workflow.DocumentWorkflow;
import com.rcszh.tax.workflow.DocumentWorkflowRegistry;

public abstract class BaseParser {
    /**
     * 当前解析器是否支持该任务项。
     */
    public abstract boolean supports(DocumentTaskItem item);
    /**
     * 执行解析
     * @param info 用户上传解析记录
     * @return 解析结果
     */
    public abstract AIParseResult doParse(DocumentTaskItem info);
    /**
     * 是否需要提前提交远程 OCR 任务。
     */
    public boolean requiresRemoteParse() {
        return false;
    }

    /**
     * 根据任务携带的材料类型取得固定流程。
     */
    protected DocumentWorkflow resolveWorkflow(DocumentTaskItem info, DocumentWorkflowRegistry registry) {
        DocumentWorkflow workflow = registry.require(info.getWorkflowCode());
        info.setNeedHumanReview(Boolean.FALSE);
        return workflow;
    }
}
