package com.rcszh.tax.postprocess.dividend;

import cn.hutool.core.util.StrUtil;
import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.postprocess.RecordPostProcessor;
import com.rcszh.tax.postprocess.dividend.model.DividendCandidateRecord;
import com.rcszh.tax.postprocess.dividend.model.DividendExtractRecord;
import com.rcszh.tax.postprocess.dividend.service.DividendExtractService;
import com.rcszh.tax.server.DocumentServer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 股息处理链的专项抽取阶段。
 *
 * <p>读取候选召回阶段写入的 {@code globalParam[DIVIDEND_CANDIDATES]}，聚合为股息业务记录，
 * 再写入 {@code globalParam[DIVIDEND_EXTRACT_RECORDS]}。本处理器顺序为 60。</p>
 */
@Component
public class DividendExtractPostProcessor implements RecordPostProcessor {
    /** 负责规则聚合及可选 AI 增强的股息抽取服务。 */
    @Resource
    private DividendExtractService dividendExtractService;

    /** @return 固定返回 60，确保在候选召回之后、质量校验之前执行 */
    @Override
    public int order() {
        return 60;
    }

    /** @return 用于追踪的处理器名称 {@code dividend-extract} */
    @Override
    public String name() {
        return "dividend-extract";
    }

    /**
     * 判断是否已产生股息候选，且文档类型明确指向股息业务。
     *
     * @param parseResult 包含候选中间结果的解析结果
     * @param taskItem 当前文档任务项
     * @param document 原始文档元数据
     * @return 存在候选且文档类型包含股息提示时返回 {@code true}
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean supports(AIParseResult parseResult, DocumentTaskItem taskItem, Map<String, Object> document) {
        Object candidates = parseResult.getGlobalParam().get(ResultBaseFieldConstant.DIVIDEND_CANDIDATES);
        if (!(candidates instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        String documentType = document == null ? null : (String) document.get(DocumentServer.TYPE);
        String requestedDocumentType = taskItem == null ? null : taskItem.getRequestedDocumentType();
        return containsDividendHint(documentType) || containsDividendHint(requestedDocumentType);
    }

    /**
     * 将候选 Map 还原为领域对象并执行专项抽取。
     *
     * <p>抽取结果始终写入 globalParam；仅当原始 records 为空时才直接回填 records，
     * 避免提前覆盖其他解析结果。</p>
     *
     * @param parseResult 承载候选数据和抽取结果的解析结果
     * @param taskItem 当前文档任务项
     * @param document 原始文档元数据，本方法当前不直接使用
     */
    @Override
    @SuppressWarnings("unchecked")
    public void process(AIParseResult parseResult, DocumentTaskItem taskItem, Map<String, Object> document) {
        Object candidateObject = parseResult.getGlobalParam().get(ResultBaseFieldConstant.DIVIDEND_CANDIDATES);
        if (!(candidateObject instanceof List<?> candidateMaps) || candidateMaps.isEmpty()) {
            return;
        }
        // globalParam 使用 Map 传递数据，此处恢复为强类型对象供领域服务处理。
        List<DividendCandidateRecord> candidates = candidateMaps.stream()
                .map(this::toCandidateRecord)
                .toList();
        List<DividendExtractRecord> extracted = dividendExtractService.extract(candidates);
        if (extracted.isEmpty()) {
            return;
        }
        // 重新转成 Map，保持 AIParseResult 的通用数据结构契约。
        List<Map<String, Object>> extractedMaps = extracted.stream()
                .map(DividendExtractRecord::toMap)
                .toList();
        parseResult.getGlobalParam().put(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS, extractedMaps);
        parseResult.getGlobalParam().put("dividendExtractCount", extractedMaps.size());
        if (parseResult.getRecords() == null || parseResult.getRecords().isEmpty()) {
            parseResult.setRecords(extractedMaps);
        }
        parseResult.getWarnings().add("已生成股息专项记录 " + extractedMaps.size() + " 条。");
    }

    /**
     * 将 globalParam 中的通用对象转换为股息候选记录。
     *
     * @param source 已是 {@link DividendCandidateRecord} 的对象或候选字段 Map
     * @return 可供抽取服务使用的强类型候选记录
     */
    @SuppressWarnings("unchecked")
    private DividendCandidateRecord toCandidateRecord(Object source) {
        if (source instanceof DividendCandidateRecord record) {
            return record;
        }
        Map<String, Object> map = (Map<String, Object>) source;
        DividendCandidateRecord record = new DividendCandidateRecord();
        record.setRowId((String) map.get("rowId"));
        record.setDividendDate((String) map.get("dividendDate"));
        record.setPayer((String) map.get("payer"));
        record.setSummary((String) map.get("summary"));
        record.setCurrency((String) map.get("currency"));
        record.setDirection((String) map.get("direction"));
        Object amount = map.get("amount");
        if (amount != null) {
            record.setAmount(new java.math.BigDecimal(amount.toString()));
        }
        Object balance = map.get("balance");
        if (balance != null) {
            record.setBalance(new java.math.BigDecimal(balance.toString()));
        }
        Object confidence = map.get("confidence");
        if (confidence != null) {
            record.setConfidence(new java.math.BigDecimal(confidence.toString()));
        }
        record.setCategory((String) map.get("category"));
        Object reasons = map.get("reasons");
        if (reasons instanceof List<?> list) {
            record.setReasons(list.stream().map(String::valueOf).toList());
        }
        Object evidence = map.get("evidence");
        if (evidence instanceof Map<?, ?> evidenceMap) {
            record.getEvidence().putAll((Map<String, Object>) evidenceMap);
        }
        Object rawData = map.get("rawData");
        if (rawData instanceof Map<?, ?> rawMap) {
            record.getRawData().putAll((Map<String, Object>) rawMap);
        }
        return record;
    }

    /**
     * 判断文档类型文本是否包含股息业务提示词。
     *
     * @param value 文档类型文本
     * @return 命中股息提示词时返回 {@code true}
     */
    private boolean containsDividendHint(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        return value.contains("股息") || value.contains("红利") || value.toLowerCase().contains("dividend");
    }
}
