package com.rcszh.tax.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DocumentPageTypeEnum {
    PAGE("page"),
    DATA("data")
    ;
    private final String code;
}
