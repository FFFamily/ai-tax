package com.rcszh.tax.entity.task;

import lombok.Data;

import java.util.List;

@Data
public class DocumentTask {
    private Long id;
    private String status;
    private List<DocumentTaskItem> items = List.of();
}
