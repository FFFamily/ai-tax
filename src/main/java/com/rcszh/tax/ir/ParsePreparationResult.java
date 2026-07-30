package com.rcszh.tax.ir;

import com.rcszh.tax.dto.HtmlTable;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParsePreparationResult {
    private List<TransactionLine> transactionLines = new ArrayList<>();
    private DocumentFeatures documentFeatures = new DocumentFeatures();
    private List<HtmlTable> htmlTables = new ArrayList<>();
}
