package com.rcszh.tax.postprocess.dividend;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.ir.TransactionLine;
import com.rcszh.tax.postprocess.RecordPostProcessor;
import com.rcszh.tax.server.DocumentServer;
import com.rcszh.tax.server.DocumentTaskServer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DividendCandidatePostProcessor implements RecordPostProcessor {
    private final DividendCandidateService dividendCandidateService;

    public DividendCandidatePostProcessor(DividendCandidateService dividendCandidateService) {
        this.dividendCandidateService = dividendCandidateService;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String name() {
        return "dividend-candidate-recall";
    }

    @Override
    public boolean supports(AIParseResult parseResult, Map<String, Object> taskItem, Map<String, Object> document) {
        if (taskItem == null) {
            return false;
        }
        Object preparedLines = taskItem.get(DocumentTaskServer.Item.PREPARED_TRANSACTION_LINES);
        if (!(preparedLines instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        String documentType = document == null ? null : (String) document.get(DocumentServer.TYPE);
        String requestedDocumentType = (String) taskItem.get(DocumentTaskServer.Item.REQUESTED_DOCUMENT_TYPE);
        return containsDividendHint(documentType) || containsDividendHint(requestedDocumentType) || parseResult != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(AIParseResult parseResult, Map<String, Object> taskItem, Map<String, Object> document) {
        Object preparedLines = taskItem.get(DocumentTaskServer.Item.PREPARED_TRANSACTION_LINES);
        if (!(preparedLines instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<TransactionLine> transactionLines = (List<TransactionLine>) list;
        List<DividendCandidateRecord> candidates = dividendCandidateService.collectCandidates(transactionLines);
        if (candidates.isEmpty()) {
            return;
        }
        parseResult.getGlobalParam().put(ResultBaseFieldConstant.DIVIDEND_CANDIDATES,
                candidates.stream().map(DividendCandidateRecord::toMap).toList());
        parseResult.getGlobalParam().put("dividendCandidateCount", candidates.size());
        parseResult.getWarnings().add("已召回疑似股息相关流水 " + candidates.size() + " 条，待进入专项抽取。");
    }

    private boolean containsDividendHint(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        return value.contains("股息") || value.contains("红利") || value.toLowerCase().contains("dividend");
    }
}
