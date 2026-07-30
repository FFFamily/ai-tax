package com.rcszh.tax.dto;

import com.rcszh.tax.util.export.SheetConfig;
import lombok.Data;

import java.util.HashMap;
import java.util.List;

@Data
public class ExportRequestDTO {
    HashMap<String, Object> data;
    List<SheetConfig> dataConfig;
    String fileName;
}
