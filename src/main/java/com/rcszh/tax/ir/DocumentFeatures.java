package com.rcszh.tax.ir;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentFeatures {
    private String fileType;
    private List<String> topKeywords = new ArrayList<>();
    private List<List<String>> tableHeaders = new ArrayList<>();
    private boolean hasBalanceColumn;
    private boolean hasDebitCreditSplit;
    private boolean hasDateColumn;
    private boolean hasAmountColumn;
    private int lineCount;
    private int tableCount;
    private List<String> textSamples = new ArrayList<>();
    private List<String> candidateInstitutions = new ArrayList<>();
    private List<String> detectedCurrencies = new ArrayList<>();
}
