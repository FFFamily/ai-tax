package com.rcszh.tax.dto;

import lombok.Data;

import java.util.List;
@Data
public class ExportDataDTO {
    List<DetailRecord> records;
}
