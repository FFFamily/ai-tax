package com.rcszh.tax.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RunTaskStatusEnum {
    SUCCESS("success"),
    FAIL("fail"),
    RUNNING("running")
    ;
    ;
    private String status;
}
