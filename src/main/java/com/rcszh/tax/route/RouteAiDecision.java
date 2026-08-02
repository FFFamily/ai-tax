package com.rcszh.tax.route;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 路由兜底返回的内部决策对象，尚未补齐候选模板的类型和变体信息。
 */
@Data
public class RouteAiDecision {
    /** AI 从候选集合中选择的模板主键。 */
    private Long documentId;
    /** AI 输出的 0 到 1 置信度。 */
    private BigDecimal confidence;
    /** AI 是否建议交由人工复核。 */
    private boolean needHumanReview;
    /** AI 给出的分类依据。 */
    private List<String> reasons = new ArrayList<>();
}
