package com.rcszh.tax.ir;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParsePreparationResult {
    private List<TransactionLine> transactionLines = new ArrayList<>();
}
