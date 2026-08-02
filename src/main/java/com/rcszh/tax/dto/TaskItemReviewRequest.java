package com.rcszh.tax.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class TaskItemReviewRequest {
    private Boolean needHumanReview;
    private List<String> reviewReasons = new ArrayList<>();
    private List<Map<String, Object>> records = new ArrayList<>();
    private String reviewer;
    private String comment;
}
