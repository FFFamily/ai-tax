package com.rcszh.tax.route;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DocumentMatchRule {
    private List<String> fileTypes = new ArrayList<>();
    private List<String> mustKeywords = new ArrayList<>();
    private List<String> anyKeywords = new ArrayList<>();
    private List<String> forbiddenKeywords = new ArrayList<>();
    private Map<String, List<String>> headerSynonyms = new LinkedHashMap<>();
}
