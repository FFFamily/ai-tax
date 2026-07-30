package com.rcszh.tax.route;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class RouteAiDecision {
    private String documentId;
    private BigDecimal confidence;
    private boolean needHumanReview;
    private List<String> reasons = new ArrayList<>();
}
