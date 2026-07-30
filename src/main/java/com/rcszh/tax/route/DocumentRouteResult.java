package com.rcszh.tax.route;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentRouteResult {
    private String documentId;
    private String documentType;
    private String variant;
    private BigDecimal confidence;
    private boolean needHumanReview;
    private String routeSource;
    private List<String> reasons = new ArrayList<>();
}
