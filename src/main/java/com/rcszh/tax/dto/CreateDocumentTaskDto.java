package com.rcszh.tax.dto;

import lombok.Data;

@Data
public class CreateDocumentTaskDto {
    private Item[] items;

    @Data
    public static class Item {
        private String workflowCode;
        private String fileUrl;
    }

}
