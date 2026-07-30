package com.rcszh.tax.dto.executiontask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "解析任务项的文档路由摘要")
public class ExecutionTaskRouteSummaryResponse {
    @Schema(description = "最终采用的文档模板 ID")
    private String documentId;

    @Schema(description = "请求的文档类型")
    private String documentType;

    @Schema(description = "路由变体")
    private String variant;

    @Schema(description = "路由置信度")
    private BigDecimal confidence;

    @Schema(description = "是否需要人工复核")
    private Boolean needHumanReview;

    @Schema(description = "路由来源", allowableValues = {"manual", "rule", "ai", ""})
    private String routeSource;

    @Schema(description = "路由原因")
    private List<String> reasons;
}
