package com.rcszh.tax.validation;

import cn.hutool.json.JSONUtil;
import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.dto.executiontask.ExecutionTaskRouteSummaryResponse;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.postprocess.RecordPostProcessService;
import com.rcszh.tax.postprocess.dividend.model.DividendCandidateRecord;
import com.rcszh.tax.postprocess.dividend.service.DividendCandidateService;
import com.rcszh.tax.postprocess.dividend.model.DividendExtractRecord;
import com.rcszh.tax.postprocess.dividend.service.DividendExtractService;
import com.rcszh.tax.route.DocumentRouteContext;
import com.rcszh.tax.route.DocumentRouteResult;
import com.rcszh.tax.route.base.DocumentRouter;
import com.rcszh.tax.server.DocumentServer;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ValidationService {
    @Resource
    private DocumentRouter documentRouter;
    @Resource
    private DocumentServer documentServer;
    @Resource
    private DividendCandidateService dividendCandidateService;
    @Resource
    private DividendExtractService dividendExtractService;
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

        DocumentRouteContext routeContext = new DocumentRouteContext();
        routeContext.setRequestedDocumentType(sampleCase.getRequestedDocumentType());
        routeContext.setFileType(sampleCase.getFileType());
        routeContext.setDocumentFeatures(sampleCase.getDocumentFeatures());
        DocumentRouteResult routeResult = documentRouter.route(routeContext);

        Map<String, Object> routeSummary = new LinkedHashMap<>();
        routeSummary.put("documentId", routeResult == null ? "" : routeResult.getDocumentId());
        routeSummary.put("documentType", routeResult == null ? "" : routeResult.getDocumentType());
        routeSummary.put("variant", routeResult == null ? "" : routeResult.getVariant());
        routeSummary.put("confidence", routeResult == null ? null : routeResult.getConfidence());
        routeSummary.put("needHumanReview", routeResult != null && routeResult.isNeedHumanReview());
        routeSummary.put("routeSource", routeResult == null ? "" : routeResult.getRouteSource());
        routeSummary.put("reasons", routeResult == null ? List.of() : routeResult.getReasons());
        report.setRouteResult(routeSummary);

        List<DividendCandidateRecord> candidates = dividendCandidateService.collectCandidates(sampleCase.getTransactionLines());
        report.setDividendCandidateCount(candidates.size());

        List<DividendExtractRecord> extracted = dividendExtractService.extract(candidates);
        AIParseResult parseResult = new AIParseResult();
        parseResult.getGlobalParam().put("routeSummary", routeSummary);
        parseResult.getGlobalParam().put(ResultBaseFieldConstant.DIVIDEND_CANDIDATES,
                candidates.stream().map(DividendCandidateRecord::toMap).toList());
        parseResult.getGlobalParam().put(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS,
                extracted.stream().map(DividendExtractRecord::toMap).toList());
        parseResult.setRecords(extracted.stream().map(DividendExtractRecord::toMap).toList());

        DocumentTaskItem taskItem = new DocumentTaskItem();
        taskItem.setRequestedDocumentType(sampleCase.getRequestedDocumentType());
        if (routeResult != null) {
            taskItem.setRouteConfidence(routeResult.getConfidence());
            taskItem.setRouteReason(String.join("；", routeResult.getReasons()));
            taskItem.setNeedHumanReview(routeResult.isNeedHumanReview());
            taskItem.setResolvedDocumentId(routeResult.getDocumentId());
            taskItem.setRouteSummary(toRouteSummary(routeResult));
        }
        Map<String, Object> document = routeResult == null ? null : documentServer.getDocument(routeResult.getDocumentId());
        recordPostProcessService.postProcess(parseResult, taskItem, document);

        Object finalRecords = parseResult.getGlobalParam().get(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS);
        report.setDividendExtractCount(finalRecords instanceof List<?> list ? list.size() : 0);
        report.setNeedHumanReview(Boolean.TRUE.equals(parseResult.getGlobalParam().get("needHumanReview")));
        collectIssues(sampleCase, routeResult, report);
        return report;
    }

    private ExecutionTaskRouteSummaryResponse toRouteSummary(DocumentRouteResult routeResult) {
        ExecutionTaskRouteSummaryResponse summary = new ExecutionTaskRouteSummaryResponse();
        summary.setDocumentId(routeResult.getDocumentId());
        summary.setDocumentType(routeResult.getDocumentType());
        summary.setVariant(routeResult.getVariant());
        summary.setConfidence(routeResult.getConfidence());
        summary.setNeedHumanReview(routeResult.isNeedHumanReview());
        summary.setRouteSource(routeResult.getRouteSource());
        summary.setReasons(routeResult.getReasons());
        return summary;
    }

    private void collectIssues(ValidationSampleCase sampleCase,
                               DocumentRouteResult routeResult,
                               ValidationSampleReport report) {
        if (sampleCase.getExpectedDocumentId() != null) {
            Long actual = routeResult == null ? null : routeResult.getDocumentId();
            if (!sampleCase.getExpectedDocumentId().equals(actual)) {
                report.getIssues().add("路由结果不符合预期: expected=" + sampleCase.getExpectedDocumentId() + ", actual=" + actual);
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
