package com.rcszh.tax.dto.executiontask;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 解析任务项的文档路由摘要。 */
@Data
public class ExecutionTaskRouteSummaryResponse {
    /** 最终采用的固定流程编码。 */
    private String workflowCode;

    /** 请求的文档类型。 */
    private String documentType;

    /** 路由变体。 */
    private String variant;

    /** 路由置信度。 */
    private BigDecimal confidence;

    /** 是否需要人工复核。 */
    private Boolean needHumanReview;

    /** 路由来源，固定流程始终为 fixed。 */
    private String routeSource;

    /** 路由原因。 */
    private List<String> reasons;
}
