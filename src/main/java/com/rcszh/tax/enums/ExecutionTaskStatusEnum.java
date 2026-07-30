package com.rcszh.tax.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ExecutionTaskStatusEnum {
    COLLECTING("待上传材料"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    FAILED("处理失败");

    private final String label;
}
