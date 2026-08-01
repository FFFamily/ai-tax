package com.rcszh.tax.postprocess.dividend.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 股息专项抽取阶段输出的业务记录。
 *
 * <p>一条记录通常由同一日期、付款方和币种下的股息收入行与预扣税行聚合而成，
 * 后续质量处理器会补充质量告警和人工复核标志。</p>
 */
@Data
public class DividendExtractRecord {
    /** 股息发生日期，标准格式为 {@code yyyy-MM-dd}。 */
    private String dividendDate;
    /** 股息付款方。 */
    private String payer;
    /** 股息及税费的币种。 */
    private String currency;
    /** 实际入账的股息净额。 */
    private BigDecimal netAmount;
    /** 境外预扣税或股息相关税费。 */
    private BigDecimal withholdingTax;
    /** 股息毛额，通常等于净额与预扣税之和。 */
    private BigDecimal grossAmount;
    /** 当前抽取结果的综合置信度，范围为 0 到 1。 */
    private BigDecimal confidence;
    /** 专项记录类别，通常为 {@code DIVIDEND}。 */
    private String category;
    /** 对股息业务的简要描述。 */
    private String summary;
    /** 参与聚合的原始交易流水行标识。 */
    private List<String> evidenceRowIds = new ArrayList<>();
    /** 规则或 AI 给出的抽取依据。 */
    private List<String> reasons = new ArrayList<>();
    /** 质量处理器发现的字段缺失、金额异常等问题。 */
    private List<String> qualityWarnings = new ArrayList<>();
    /** 当前记录是否需要人工复核。 */
    private Boolean needHumanReview;
    /** 以流水行标识组织的原始证据集合。 */
    private Map<String, Object> evidence = new LinkedHashMap<>();

    /**
     * 转换为可回写到解析结果的通用 Map。
     *
     * @return 按固定字段顺序生成的专项股息记录 Map
     */
    public Map<String, Object> toMap() {
        // LinkedHashMap 保持稳定字段顺序，便于导出、日志审阅和结果比对。
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dividendDate", dividendDate);
        map.put("payer", payer);
        map.put("currency", currency);
        map.put("netAmount", netAmount);
        map.put("withholdingTax", withholdingTax);
        map.put("grossAmount", grossAmount);
        map.put("confidence", confidence);
        map.put("category", category);
        map.put("summary", summary);
        map.put("evidenceRowIds", evidenceRowIds);
        map.put("reasons", reasons);
        map.put("qualityWarnings", qualityWarnings);
        map.put("needHumanReview", needHumanReview);
        map.put("evidence", evidence);
        return map;
    }
}
