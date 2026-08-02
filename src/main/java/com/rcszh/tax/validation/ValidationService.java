package com.rcszh.tax.validation;

import cn.hutool.json.JSONUtil;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.dto.executiontask.ExecutionTaskRouteSummaryResponse;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.postprocess.RecordPostProcessService;
import com.rcszh.tax.postprocess.dividend.model.DividendCandidateRecord;
import com.rcszh.tax.postprocess.dividend.service.DividendCandidateService;
import com.rcszh.tax.workflow.DocumentWorkflow;
import com.rcszh.tax.workflow.DocumentWorkflowRegistry;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Service
public class ValidationService {
    @Resource
    private DocumentWorkflowRegistry workflowRegistry;
    @Resource
    private DividendCandidateService dividendCandidateService;
    @Resource
    private RecordPostProcessService recordPostProcessService;

    public List<ValidationSampleReport> runSamples() {
        List<ValidationSampleReport> reports = new ArrayList<>();
        for (ValidationSampleCase sampleCase : loadCases()) {
            reports.add(runSample(sampleCase));
        }
        return reports;
    }

    private ValidationSampleReport runSample(ValidationSampleCase sampleCase) {
        ValidationSampleReport report = new ValidationSampleReport();
        report.setId(sampleCase.getId());
        report.setName(sampleCase.getName());

        DocumentWorkflow workflow = workflowRegistry.require(sampleCase.getWorkflowCode());

        Map<String, Object> routeSummary = new LinkedHashMap<>();
        routeSummary.put("workflowCode", workflow.code());
        routeSummary.put("documentType", workflow.documentType());
        routeSummary.put("variant", "");
        routeSummary.put("confidence", BigDecimal.ONE);
        routeSummary.put("needHumanReview", false);
        routeSummary.put("routeSource", "fixed");
        routeSummary.put("reasons", List.of("使用代码内固定流程: " + workflow.code()));
        report.setRouteResult(routeSummary);

        List<DividendCandidateRecord> candidates = dividendCandidateService.collectCandidates(sampleCase.getTransactionLines());
        report.setDividendCandidateCount(candidates.size());

        AIParseResult parseResult = new AIParseResult();

        DocumentTaskItem taskItem = new DocumentTaskItem();
        taskItem.setWorkflowCode(sampleCase.getWorkflowCode());
        taskItem.setRouteConfidence(BigDecimal.ONE);
        taskItem.setRouteReason("[fixed] 使用代码内固定流程: " + workflow.code());
        taskItem.setNeedHumanReview(false);
        taskItem.setRouteSummary(toRouteSummary(workflow));
        taskItem.setPreparedTransactionLines(sampleCase.getTransactionLines());
        recordPostProcessService.postProcess(parseResult, taskItem, workflow);

        report.setDividendExtractCount(parseResult.getRecords().size());
        report.setNeedHumanReview(Boolean.TRUE.equals(taskItem.getNeedHumanReview()));
        collectIssues(sampleCase, workflow, report);
        return report;
    }

    private ExecutionTaskRouteSummaryResponse toRouteSummary(DocumentWorkflow workflow) {
        ExecutionTaskRouteSummaryResponse summary = new ExecutionTaskRouteSummaryResponse();
        summary.setWorkflowCode(workflow.code());
        summary.setDocumentType(workflow.documentType());
        summary.setVariant("");
        summary.setConfidence(BigDecimal.ONE);
        summary.setNeedHumanReview(false);
        summary.setRouteSource("fixed");
        summary.setReasons(List.of("使用代码内固定流程: " + workflow.code()));
        return summary;
    }

    private void collectIssues(ValidationSampleCase sampleCase,
                               DocumentWorkflow workflow,
                               ValidationSampleReport report) {
        if (sampleCase.getExpectedWorkflowCode() != null) {
            if (!sampleCase.getExpectedWorkflowCode().equals(workflow.code())) {
                report.getIssues().add("固定流程不符合预期: expected="
                        + sampleCase.getExpectedWorkflowCode() + ", actual=" + workflow.code());
            }
        }
        if (sampleCase.getExpectedDividendCandidateCount() != null
                && sampleCase.getExpectedDividendCandidateCount() != report.getDividendCandidateCount()) {
            report.getIssues().add("股息候选数不符合预期: expected="
                    + sampleCase.getExpectedDividendCandidateCount() + ", actual=" + report.getDividendCandidateCount());
        }
        if (sampleCase.getExpectedDividendExtractCount() != null
                && sampleCase.getExpectedDividendExtractCount() != report.getDividendExtractCount()) {
            report.getIssues().add("股息输出数不符合预期: expected="
                    + sampleCase.getExpectedDividendExtractCount() + ", actual=" + report.getDividendExtractCount());
        }
        if (sampleCase.getExpectNeedHumanReview() != null
                && sampleCase.getExpectNeedHumanReview() != report.isNeedHumanReview()) {
            report.getIssues().add("复核标记不符合预期: expected="
                    + sampleCase.getExpectNeedHumanReview() + ", actual=" + report.isNeedHumanReview());
        }
        report.setSuccess(report.getIssues().isEmpty());
    }

    private List<ValidationSampleCase> loadCases() {
        ClassPathResource resource = new ClassPathResource("validation/dividend-validation-samples.json");
        try (InputStream inputStream = resource.getInputStream()) {
            return JSONUtil.parseArray(inputStream.readAllBytes()).toList(ValidationSampleCase.class);
        } catch (IOException e) {
            throw new RuntimeException("加载验证样本失败", e);
        }
    }
}
