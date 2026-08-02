package com.rcszh.tax.parser;

import com.rcszh.tax.dto.executiontask.ExecutionTaskRouteSummaryResponse;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.workflow.DocumentWorkflow;
import com.rcszh.tax.workflow.DocumentWorkflowRegistry;

import java.math.BigDecimal;
import java.util.List;

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
     * 根据任务携带的材料类型取得固定流程，并回写可审计的流程摘要。
     */
    protected DocumentWorkflow resolveWorkflow(DocumentTaskItem info, DocumentWorkflowRegistry registry) {
        DocumentWorkflow workflow = registry.require(info.getWorkflowCode());
        String reason = "使用代码内固定流程: " + workflow.code();
        info.setRouteVariant("");
        info.setRouteConfidence(BigDecimal.ONE);
        info.setRouteReason("[fixed] " + reason);
        info.setNeedHumanReview(Boolean.FALSE);
        info.setRouteSummary(buildRouteSummary(
                workflow.code(),
                workflow.documentType(),
                BigDecimal.ONE,
                List.of(reason)
        ));
        return workflow;
    }

    private ExecutionTaskRouteSummaryResponse buildRouteSummary(String workflowCode,
                                                                 String documentType,
                                                                 BigDecimal confidence,
                                                                 List<String> reasons) {
        ExecutionTaskRouteSummaryResponse routeSummary = new ExecutionTaskRouteSummaryResponse();
        routeSummary.setWorkflowCode(workflowCode);
        routeSummary.setDocumentType(documentType == null ? "" : documentType);
        routeSummary.setVariant("");
        routeSummary.setConfidence(confidence == null ? BigDecimal.ZERO : confidence);
        routeSummary.setNeedHumanReview(false);
        routeSummary.setRouteSource("fixed");
        routeSummary.setReasons(reasons == null ? List.of() : reasons);
        return routeSummary;
    }
}
