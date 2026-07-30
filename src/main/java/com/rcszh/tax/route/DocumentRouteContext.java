package com.rcszh.tax.route;

import com.rcszh.tax.ir.DocumentFeatures;
import lombok.Data;

@Data
public class DocumentRouteContext {
    private String requestedDocumentType;
    private String fileUrl;
    private String fileType;
    private DocumentFeatures documentFeatures;
}
