package com.rcszh.tax.postprocess.dividend;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.constant.ResultBaseFieldConstant;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.postprocess.RecordPostProcessor;
import com.rcszh.tax.workflow.DocumentWorkflow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 股息处理链的质量校验与人工复核判定阶段。
 *
 * <p>对专项抽取记录执行格式标准化、金额勾稽、必填项、重复记录、抽取置信度和路由置信度检查，
 * 并同步更新解析结果与任务项的人工复核状态。本处理器顺序为 70，是股息链路的最后一步。</p>
 */
@Component
public class DividendQualityPostProcessor implements RecordPostProcessor {
    /** @return 固定返回 70，确保质量检查在候选召回和专项抽取之后执行 */
    @Override
    public int order() {
        return 70;
    }

    /** @return 用于追踪的处理器名称 {@code dividend-quality} */
    @Override
    public String name() {
        return "dividend-quality";
    }

    /**
     * 判断专项抽取阶段是否已产生待质检记录。
     *
     * @param parseResult 包含专项抽取结果的解析结果
     * @param taskItem 当前文档任务项
     * @param workflow 固定文档流程
     * @return 存在非空股息抽取记录列表时返回 {@code true}
     */
    @Override
    public boolean supports(AIParseResult parseResult, DocumentTaskItem taskItem, DocumentWorkflow workflow) {
        Object records = parseResult.getGlobalParam().get(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS);
        return records instanceof List<?> list && !list.isEmpty();
    }

    /**
     * 标准化并校验股息记录，汇总复核原因并回写任务状态。
     *
     * @param parseResult 承载股息记录、告警和全局复核标志的解析结果
     * @param taskItem 用于同步人工复核状态和路由信息的任务项
     * @param workflow 固定文档流程，本方法当前不直接使用
     */
    @Override
    @SuppressWarnings("unchecked")
    public void process(AIParseResult parseResult, DocumentTaskItem taskItem, DocumentWorkflow workflow) {
        Object records = parseResult.getGlobalParam().get(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS);
        if (!(records instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        // 质量校验负责把“可抽取”进一步提升为“可复核、可导出、可追责”的记录。
        // reviewed 保存标准化并附加逐条质检结果后的记录副本。
        List<Map<String, Object>> reviewed = new ArrayList<>();
        // reviewReasons 汇总文档级复核原因，最终去重后同步到 parseResult 和 taskItem。
        List<String> reviewReasons = new ArrayList<>();
        // 文档级人工复核标志，只要任一记录或路由存在风险即置为 true。
        boolean needHumanReview = false;
        // duplicateGroups 保存确认重复的业务键，seenGroups 用于首次出现检测。
        Set<String> duplicateGroups = new HashSet<>();
        Set<String> seenGroups = new HashSet<>();
        for (Object item : list) {
            Map<String, Object> record = new LinkedHashMap<>((Map<String, Object>) item);
            // 先做标准化，再校验字段一致性，避免格式差异把本来正确的记录误判为异常。
            normalize(record);
            String groupKey = buildDuplicateKey(record);
            if (StrUtil.isNotBlank(groupKey) && !seenGroups.add(groupKey)) {
                duplicateGroups.add(groupKey);
            }
            List<String> qualityWarnings = evaluate(record);
            boolean recordNeedReview = !qualityWarnings.isEmpty();
            Object confidence = record.get("confidence");
            if (confidence != null && new BigDecimal(confidence.toString()).compareTo(new BigDecimal("0.75")) < 0) {
                qualityWarnings.add("置信度低于0.75");
                recordNeedReview = true;
            }
            if (recordNeedReview) {
                needHumanReview = true;
                reviewReasons.addAll(qualityWarnings);
            }
            record.put("qualityWarnings", qualityWarnings.stream().distinct().toList());
            record.put("needHumanReview", recordNeedReview);
            reviewed.add(record);
        }
        if (!duplicateGroups.isEmpty()) {
            // 重复分红记录是高风险问题，直接升级为人工复核。
            needHumanReview = true;
            reviewReasons.add("存在疑似重复股息记录");
            for (Map<String, Object> record : reviewed) {
                if (duplicateGroups.contains(buildDuplicateKey(record))) {
                    appendWarning(record, "存在疑似重复记录");
                    record.put("needHumanReview", true);
                }
            }
        }
        parseResult.getGlobalParam().put(ResultBaseFieldConstant.DIVIDEND_EXTRACT_RECORDS, reviewed);
        if (taskItem.getRouteSummary() != null) {
            parseResult.getGlobalParam().put("routeSummary", taskItem.getRouteSummary());
            // 模板路由本身置信度过低时，即使字段看起来完整，也要提醒人工确认模板是否选对。
            BigDecimal routeConfidence = taskItem.getRouteSummary().getConfidence();
            if (routeConfidence != null && routeConfidence.compareTo(new BigDecimal("0.60")) < 0) {
                needHumanReview = true;
                taskItem.setNeedHumanReview(true);
                reviewReasons.add("路由置信度低于0.60");
                parseResult.getWarnings().add("文档路由置信度较低，建议人工确认模板。");
            }
        }
        List<String> finalReviewReasons = reviewReasons.stream().distinct().toList();
        parseResult.getGlobalParam().put("dividendReviewReasons", finalReviewReasons);
        parseResult.getGlobalParam().put("reviewReasons", finalReviewReasons);
        parseResult.getGlobalParam().put("needHumanReview", needHumanReview);
        if (needHumanReview) {
            taskItem.setNeedHumanReview(true);
            taskItem.setReviewReasons(JSONUtil.toJsonStr(finalReviewReasons));
            parseResult.getWarnings().add("股息专项记录存在低置信或字段缺失，建议人工复核。");
        } else {
            taskItem.setNeedHumanReview(false);
            taskItem.setReviewReasons(JSONUtil.toJsonStr(finalReviewReasons));
        }
        if (parseResult.getRecords() != null && !parseResult.getRecords().isEmpty()) {
            parseResult.setRecords(reviewed);
        }
    }

    /**
     * 统一一条股息记录的日期、币种和金额，并根据已知金额补齐第三项。
     *
     * @param record 待原地标准化的股息记录 Map
     */
    private void normalize(Map<String, Object> record) {
        // 统一日期、币种和金额格式，并尽量在净额/税额/毛额之间做自动补全。
        normalizeDate(record);
        normalizeCurrency(record);
        BigDecimal net = normalizeAmount(record, "netAmount");
        BigDecimal tax = normalizeAmount(record, "withholdingTax");
        BigDecimal gross = normalizeAmount(record, "grossAmount");
        if (gross == null && net != null && tax != null) {
            gross = net.add(tax);
            record.put("grossAmount", gross);
        }
        if (net == null && gross != null && tax != null && gross.compareTo(tax) >= 0) {
            net = gross.subtract(tax);
            record.put("netAmount", net);
        }
        if (tax == null && gross != null && net != null && gross.compareTo(net) >= 0) {
            tax = gross.subtract(net);
            record.put("withholdingTax", tax);
        }
    }

    /**
     * 检查记录的完整性、金额勾稽关系和证据行质量。
     *
     * @param record 已标准化的股息记录
     * @return 当前记录的质量告警列表；空列表表示未发现问题
     */
    private List<String> evaluate(Map<String, Object> record) {
        List<String> warnings = new ArrayList<>();
        // 这里关注的是“结果是否可申报/可解释”，而不是单纯字段是否非空。
        if (isBlank(record.get("dividendDate"))) {
            warnings.add("缺少股息日期");
        }
        if (isBlank(record.get("payer"))) {
            warnings.add("缺少付款方");
        }
        if (isBlank(record.get("currency"))) {
            warnings.add("缺少币种");
        }
        if (record.get("netAmount") == null && record.get("grossAmount") == null) {
            warnings.add("缺少金额信息");
        }
        if (record.get("grossAmount") != null && record.get("netAmount") != null && record.get("withholdingTax") != null) {
            BigDecimal gross = new BigDecimal(record.get("grossAmount").toString());
            BigDecimal net = new BigDecimal(record.get("netAmount").toString());
            BigDecimal tax = new BigDecimal(record.get("withholdingTax").toString());
            if (gross.compareTo(net.add(tax)) != 0) {
                warnings.add("毛额不等于净额加税额");
            }
            if (tax.compareTo(gross) > 0) {
                warnings.add("税额大于毛额");
            }
        }
        if (record.get("netAmount") != null && new BigDecimal(record.get("netAmount").toString()).compareTo(BigDecimal.ZERO) < 0) {
            warnings.add("净额为负数");
        }
        if (record.get("grossAmount") != null && new BigDecimal(record.get("grossAmount").toString()).compareTo(BigDecimal.ZERO) < 0) {
            warnings.add("毛额为负数");
        }
        Object evidenceRowIds = record.get("evidenceRowIds");
        if (!(evidenceRowIds instanceof List<?> list) || list.isEmpty()) {
            warnings.add("缺少证据行");
        } else {
            long distinctCount = list.stream().distinct().count();
            if (distinctCount != list.size()) {
                warnings.add("存在重复证据行");
            }
        }
        return warnings;
    }

    /**
     * 将股息日期转换为 {@code yyyy-MM-dd}；无法解析时保留原值供后续质检。
     *
     * @param record 待处理记录
     */
    private void normalizeDate(Map<String, Object> record) {
        Object value = record.get("dividendDate");
        if (value == null || value.toString().isBlank()) {
            return;
        }
        try {
            record.put("dividendDate", DateUtil.parse(value.toString()).toDateStr());
        } catch (Exception ignored) {
        }
    }

    /**
     * 去除币种首尾空白并统一为大写。
     *
     * @param record 待处理记录
     */
    private void normalizeCurrency(Map<String, Object> record) {
        Object value = record.get("currency");
        if (value == null || value.toString().isBlank()) {
            return;
        }
        record.put("currency", value.toString().trim().toUpperCase());
    }

    /**
     * 将指定金额字段转换为非负 {@link BigDecimal} 并写回记录。
     *
     * @param record 待处理记录
     * @param key 金额字段名
     * @return 标准化金额；字段为空时返回 {@code null}
     */
    private BigDecimal normalizeAmount(Map<String, Object> record, String key) {
        Object value = record.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        BigDecimal normalized = new BigDecimal(value.toString()).abs();
        record.put(key, normalized);
        return normalized;
    }

    /**
     * 由日期、付款方、币种和毛额构造重复记录识别键。
     *
     * @param record 股息记录
     * @return 重复识别键；所有组成字段均为空时返回空字符串
     */
    private String buildDuplicateKey(Map<String, Object> record) {
        String date = stringValue(record.get("dividendDate"));
        String payer = stringValue(record.get("payer"));
        String currency = stringValue(record.get("currency"));
        String gross = stringValue(record.get("grossAmount"));
        if (date.isBlank() && payer.isBlank() && currency.isBlank() && gross.isBlank()) {
            return "";
        }
        return String.join("|", date, payer, currency, gross);
    }

    /**
     * 向记录的质量告警集合追加一项，并保持结果去重。
     *
     * @param record 待更新记录
     * @param warning 新增告警内容
     */
    @SuppressWarnings("unchecked")
    private void appendWarning(Map<String, Object> record, String warning) {
        Object value = record.get("qualityWarnings");
        List<String> warnings = value instanceof List<?> list
                ? new ArrayList<>(list.stream().map(String::valueOf).toList())
                : new ArrayList<>();
        warnings.add(warning);
        record.put("qualityWarnings", warnings.stream().distinct().toList());
    }

    /**
     * 将任意字段值转换为去除首尾空白的字符串。
     *
     * @param value 字段值
     * @return 字符串值，{@code null} 转为空字符串
     */
    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * 判断字段值是否为空。
     *
     * @param value 字段值
     * @return 值为 {@code null} 或字符串为空白时返回 {@code true}
     */
    private boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }
}
