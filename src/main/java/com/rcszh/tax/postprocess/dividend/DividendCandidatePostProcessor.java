package com.rcszh.tax.postprocess.dividend;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.ir.TransactionLine;
import com.rcszh.tax.postprocess.RecordPostProcessor;
import com.rcszh.tax.server.DocumentServer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DividendCandidatePostProcessor implements RecordPostProcessor {
    @Resource
    private DividendCandidateService dividendCandidateService;

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String name() {
        return "dividend-candidate-recall";
    }

    @Override
    public boolean supports(AIParseResult parseResult, DocumentTaskItem taskItem, Map<String, Object> document) {
        if (taskItem == null) {
            return false;
        }
        if (taskItem.getPreparedTransactionLines() == null || taskItem.getPreparedTransactionLines().isEmpty()) {
            return false;
        }
        String documentType = document == null ? null : (String) document.get(DocumentServer.TYPE);
        String requestedDocumentType = taskItem.getRequestedDocumentType();
        return containsDividendHint(documentType) || containsDividendHint(requestedDocumentType) || parseResult != null;
    }

    @Override
    public void process(AIParseResult parseResult, DocumentTaskItem taskItem, Map<String, Object> document) {
        List<TransactionLine> transactionLines = taskItem.getPreparedTransactionLines();
        if (transactionLines == null || transactionLines.isEmpty()) {
            return;
        }
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
