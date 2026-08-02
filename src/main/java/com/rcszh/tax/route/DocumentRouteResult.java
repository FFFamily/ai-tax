package com.rcszh.tax.route;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档路由的最终决策结果，由解析任务、人工复核和结果展示共同消费。
 */
@Data
public class DocumentRouteResult {
    /** 最终选中的文档模板主键。 */
    private Long documentId;
    /** 模板所属的材料/文档类型。 */
    private String documentType;
    /** 同类模板下的机构、版式或来源变体。 */
    private String variant;
    /** 0 到 1 的决策置信度。 */
    private BigDecimal confidence;
    /** 当前结果是否需要人工确认。 */
    private boolean needHumanReview;
    /** 决策来源，例如 {@code rule}、{@code ai} 或 {@code manual}。 */
    private String routeSource;
    /** 支撑本次选择的可审计原因。 */
    private List<String> reasons = new ArrayList<>();
}
