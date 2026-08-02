package com.rcszh.tax.validation;

import com.rcszh.tax.ir.DocumentFeatures;
import com.rcszh.tax.ir.TransactionLine;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ValidationSampleCase {
    private String id;
    private String name;
    private String workflowCode;
    private String fileType;
    private String expectedWorkflowCode;
    private Integer expectedDividendCandidateCount;
    private Integer expectedDividendExtractCount;
    private Boolean expectNeedHumanReview;
    private DocumentFeatures documentFeatures = new DocumentFeatures();
    private List<TransactionLine> transactionLines = new ArrayList<>();
}
