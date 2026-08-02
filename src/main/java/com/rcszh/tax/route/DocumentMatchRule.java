package com.rcszh.tax.route;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个文档模板的规则路由配置，对应模板记录中的 {@code matchRule} JSON。
 *
 * <p>文件类型、必选关键词和排除关键词属于硬过滤条件；候选关键词与表头同义词参与置信度打分。</p>
 */
@Data
public class DocumentMatchRule {
    /** 模板支持的文件类型；为空表示不限制。 */
    private List<String> fileTypes = new ArrayList<>();
    /** 必须全部命中的关键词，缺少任意一个即淘汰候选模板。 */
    private List<String> mustKeywords = new ArrayList<>();
    /** 按命中比例计分的候选关键词。 */
    private List<String> anyKeywords = new ArrayList<>();
    /** 命中任意一个即淘汰候选模板的排除关键词。 */
    private List<String> forbiddenKeywords = new ArrayList<>();
    /** 业务字段名到可接受表头名称的映射，用于跨机构表头兼容。 */
    private Map<String, List<String>> headerSynonyms = new LinkedHashMap<>();
}
