package com.rcszh.tax.validation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ValidationSampleReport {
    private String id;
    private String name;
    private boolean success;
    private int dividendCandidateCount;
    private int dividendExtractCount;
    private boolean needHumanReview;
    private List<String> issues = new ArrayList<>();
}
