package com.rcszh.tax.dto;

import com.rcszh.tax.entity.WordArea;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WordAreaDto extends WordArea {
    private List<WordArea> children;
}
