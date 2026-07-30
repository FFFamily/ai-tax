package com.rcszh.tax.controller;

import com.rcszh.tax.common.ApiResponse;
import com.rcszh.tax.validation.ValidationSampleReport;
import com.rcszh.tax.validation.ValidationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/validation")
public class ValidationController {
    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @GetMapping("/dividend-samples")
    public ApiResponse<Map<String, Object>> runDividendSamples() {
        List<ValidationSampleReport> reports = validationService.runSamples();
        long passed = reports.stream().filter(ValidationSampleReport::isSuccess).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", reports.size());
        result.put("passed", passed);
        result.put("failed", reports.size() - passed);
        result.put("reports", reports);
        return ApiResponse.success(result);
    }
}
