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
    private String requestedDocumentType;
    private String fileType;
    private Long expectedDocumentId;
    private Integer expectedDividendCandidateCount;
    private Integer expectedDividendExtractCount;
    private Boolean expectNeedHumanReview;
    private DocumentFeatures documentFeatures = new DocumentFeatures();
    private List<TransactionLine> transactionLines = new ArrayList<>();
}
