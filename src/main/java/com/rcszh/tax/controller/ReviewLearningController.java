package com.rcszh.tax.controller;

import com.rcszh.tax.common.ApiResponse;
import com.rcszh.tax.service.ReviewLearningService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/review-learning")
public class ReviewLearningController {
    private final ReviewLearningService reviewLearningService;

    public ReviewLearningController(ReviewLearningService reviewLearningService) {
        this.reviewLearningService = reviewLearningService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(value = "documentType", required = false) String documentType) {
        List<Map<String, Object>> items = reviewLearningService.listLearnings(documentType);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", items.size());
        result.put("items", items);
        return ApiResponse.success(result);
    }
}
