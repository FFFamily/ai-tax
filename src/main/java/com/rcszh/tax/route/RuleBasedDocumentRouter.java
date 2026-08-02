package com.rcszh.tax.route;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.ir.DocumentFeatures;
import com.rcszh.tax.route.base.DocumentRouter;
import com.rcszh.tax.server.DocumentServer;
import com.rcszh.tax.service.ReviewLearningService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 默认的文档模板路由实现。
 *
 * <p>路由首先使用可解释规则对候选模板评分，达到阈值时直接通过；没有规则结果或规则结果低于阈值时，
 * 调用 AI 在同一候选集合中兜底。人工复核沉淀的关键词会参与后续规则和 AI 判断。</p>
 */
@Component
public class RuleBasedDocumentRouter implements DocumentRouter {
    /** 达到该分数的规则结果可直接使用，无需 AI 兜底或人工复核。 */
    private static final BigDecimal AUTO_PASS_THRESHOLD = new BigDecimal("0.60");

    @Resource
    private DocumentServer documentServer;
    @Resource
    private RouteAiFallbackService routeAiFallbackService;
    @Resource
    private ReviewLearningService reviewLearningService;

    /**
     * 从指定文档类型的候选模板中选择最高分结果，必要时使用 AI 兜底。
     *
     * @param context 文件类型、用户选择和文档特征组成的路由上下文
     * @return 最终路由结果；候选集合为空或所有候选均被硬规则淘汰时返回 {@code null}
     */
    @Override
    public DocumentRouteResult route(DocumentRouteContext context) {
        // 先走低成本的规则路由；只有分数不够稳定时才让 AI 兜底，控制成本同时保留弹性。
        List<Map<String, Object>> candidates = documentServer.listRouteCandidates(context.getRequestedDocumentType());
        DocumentRouteResult bestResult = null;
        BigDecimal bestScore = BigDecimal.ZERO;
        for (Map<String, Object> candidate : candidates) {
            CandidateScore candidateScore = scoreCandidate(candidate, context);
            if (!candidateScore.matched()) {
                continue;
            }
            if (candidateScore.score().compareTo(bestScore) > 0) {
                bestScore = candidateScore.score();
                bestResult = new DocumentRouteResult();
                bestResult.setDocumentId((Long) candidate.get(DocumentServer.ID_KEY));
                bestResult.setDocumentType((String) candidate.get(DocumentServer.TYPE));
                bestResult.setVariant((String) candidate.get(DocumentServer.VARIANT));
                bestResult.setConfidence(candidateScore.score());
                bestResult.setNeedHumanReview(candidateScore.score().compareTo(AUTO_PASS_THRESHOLD) < 0);
                bestResult.setRouteSource("rule");
                bestResult.setReasons(candidateScore.reasons());
            }
        }
        if ((bestResult == null || bestResult.getConfidence().compareTo(AUTO_PASS_THRESHOLD) < 0) && !candidates.isEmpty()) {
            DocumentRouteResult aiResult = buildAiFallbackResult(context, candidates, bestResult);
            if (aiResult != null) {
                return aiResult;
            }
        }
        return bestResult;
    }

    /**
     * 把 AI 选择补齐为标准路由结果，并合并已有规则判断原因。
     *
     * @param context 当前路由上下文
     * @param candidates AI 只能从中选择的候选模板
     * @param currentRuleResult 当前最佳规则结果，可为空
     * @return 有效的 AI 路由结果；AI 无结论或选择越界时回退到规则结果
     */
    private DocumentRouteResult buildAiFallbackResult(DocumentRouteContext context,
                                                      List<Map<String, Object>> candidates,
                                                      DocumentRouteResult currentRuleResult) {
        // AI 兜底不是替代规则，而是在低置信场景补充判断并保留规则命中原因。
        RouteAiDecision decision = routeAiFallbackService.decide(context, candidates);
        if (decision == null || decision.getDocumentId() == null) {
            return currentRuleResult;
        }
        Map<String, Object> matchedCandidate = candidates.stream()
                .filter(candidate -> decision.getDocumentId().equals(candidate.get(DocumentServer.ID_KEY)))
                .findFirst()
                .orElse(null);
        if (matchedCandidate == null) {
            return currentRuleResult;
        }
        DocumentRouteResult result = new DocumentRouteResult();
        result.setDocumentId(decision.getDocumentId());
        result.setDocumentType((String) matchedCandidate.get(DocumentServer.TYPE));
        result.setVariant((String) matchedCandidate.get(DocumentServer.VARIANT));
        result.setConfidence(decision.getConfidence());
        result.setNeedHumanReview(decision.isNeedHumanReview());
        result.setRouteSource("ai");
        result.setReasons(decision.getReasons());
        if (currentRuleResult != null && currentRuleResult.getReasons() != null && !currentRuleResult.getReasons().isEmpty()) {
            List<String> reasons = new ArrayList<>(currentRuleResult.getReasons());
            reasons.addAll(result.getReasons());
            result.setReasons(reasons.stream().distinct().toList());
        }
        return result;
    }

    /**
     * 对单个候选模板执行硬过滤和加权评分。
     *
     * <p>文档类型、文件类型、必选关键词和排除关键词负责淘汰；候选关键词、复核关键词和表头规则负责加分。
     * 最终分数限制在 0 到 1，并保留每个命中原因。</p>
     */
    private CandidateScore scoreCandidate(Map<String, Object> candidate, DocumentRouteContext context) {
        List<String> reasons = new ArrayList<>();
        DocumentMatchRule rule = parseRule((String) candidate.get(DocumentServer.MATCH_RULE));
        String requestedType = context.getRequestedDocumentType();
        String candidateType = (String) candidate.get(DocumentServer.TYPE);
        if (StrUtil.isNotBlank(requestedType) && StrUtil.isNotBlank(candidateType)
                && !requestedType.equalsIgnoreCase(candidateType)) {
            return CandidateScore.unmatched();
        }
        if (!matchesFileType(rule, context.getFileType())) {
            return CandidateScore.unmatched();
        }
        Set<String> keywords = collectKeywords(context.getDocumentFeatures());
        List<String> headers = flattenHeaders(context.getDocumentFeatures());
        // 复核沉淀出来的关键词会进入路由打分，实现“人工纠正一次，后续自动受益”。
        List<String> learnedKeywords = reviewLearningService.listSuggestedKeywords(
                (Long) candidate.get(DocumentServer.ID_KEY),
                candidateType,
                12
        );
        BigDecimal score = BigDecimal.ZERO;
        if (StrUtil.isNotBlank(requestedType) && requestedType.equalsIgnoreCase(candidateType)) {
            score = score.add(new BigDecimal("0.20"));
            reasons.add("匹配用户指定的文档类型");
        }
        if (!matchesMustKeywords(rule, keywords, reasons)) {
            return CandidateScore.unmatched();
        }
        score = score.add(mustKeywordScore(rule));
        score = score.add(anyKeywordScore(rule, keywords, reasons));
        score = score.add(learnedKeywordScore(learnedKeywords, keywords, reasons));
        if (containsForbiddenKeyword(rule, keywords, reasons)) {
            return CandidateScore.unmatched();
        }
        score = score.add(headerScore(rule, headers, reasons));
        if (score.compareTo(BigDecimal.ONE) > 0) {
            score = BigDecimal.ONE;
        }
        if (score.compareTo(BigDecimal.ZERO) <= 0) {
            return CandidateScore.unmatched();
        }
        return new CandidateScore(score.setScale(4, RoundingMode.HALF_UP), reasons, true);
    }

    /**
     * 将模板的 JSON 匹配规则转换为强类型对象。
     * 配置为空或格式错误时使用空规则，避免单个模板配置错误中断整个路由流程。
     */
    private DocumentMatchRule parseRule(String json) {
        if (StrUtil.isBlank(json)) {
            return new DocumentMatchRule();
        }
        try {
            return JSONUtil.toBean(json, DocumentMatchRule.class);
        } catch (Exception e) {
            return new DocumentMatchRule();
        }
    }

    /**
     * 检查输入文件类型是否属于模板允许范围；规则未配置文件类型时视为匹配。
     */
    private boolean matchesFileType(DocumentMatchRule rule, String fileType) {
        if (rule.getFileTypes() == null || rule.getFileTypes().isEmpty()) {
            return true;
        }
        if (StrUtil.isBlank(fileType)) {
            return false;
        }
        return rule.getFileTypes().stream()
                .filter(StrUtil::isNotBlank)
                .map(this::normalize)
                .anyMatch(item -> item.equals(normalize(fileType)));
    }

    /**
     * 检查所有必选关键词是否命中，并把命中项追加到审计原因中。
     */
    private boolean matchesMustKeywords(DocumentMatchRule rule, Set<String> keywords, List<String> reasons) {
        if (rule.getMustKeywords() == null || rule.getMustKeywords().isEmpty()) {
            return true;
        }
        for (String mustKeyword : rule.getMustKeywords()) {
            if (!containsKeyword(keywords, mustKeyword)) {
                return false;
            }
            reasons.add("命中必选关键词:" + mustKeyword);
        }
        return true;
    }

    /**
     * 必选关键词全部命中后的固定得分；没有配置必选关键词时不加分。
     */
    private BigDecimal mustKeywordScore(DocumentMatchRule rule) {
        if (rule.getMustKeywords() == null || rule.getMustKeywords().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal("0.35");
    }

    /**
     * 根据普通候选关键词的命中比例计算分数，最高贡献 0.20。
     */
    private BigDecimal anyKeywordScore(DocumentMatchRule rule, Set<String> keywords, List<String> reasons) {
        if (rule.getAnyKeywords() == null || rule.getAnyKeywords().isEmpty()) {
            return BigDecimal.ZERO;
        }
        // 候选关键词采用比例得分，避免单个热词把模板误路由到高置信。
        long hitCount = rule.getAnyKeywords().stream().filter(keyword -> containsKeyword(keywords, keyword)).count();
        if (hitCount == 0) {
            return BigDecimal.ZERO;
        }
        rule.getAnyKeywords().stream()
                .filter(keyword -> containsKeyword(keywords, keyword))
                .forEach(keyword -> reasons.add("命中候选关键词:" + keyword));
        BigDecimal ratio = BigDecimal.valueOf(hitCount)
                .divide(BigDecimal.valueOf(rule.getAnyKeywords().size()), 4, RoundingMode.HALF_UP);
        return ratio.multiply(new BigDecimal("0.20"));
    }

    /**
     * 根据人工复核沉淀关键词的命中比例计算分数，最高贡献 0.15。
     */
    private BigDecimal learnedKeywordScore(List<String> learnedKeywords, Set<String> keywords, List<String> reasons) {
        if (learnedKeywords == null || learnedKeywords.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long hitCount = learnedKeywords.stream().filter(keyword -> containsKeyword(keywords, keyword)).count();
        if (hitCount == 0) {
            return BigDecimal.ZERO;
        }
        learnedKeywords.stream()
                .filter(keyword -> containsKeyword(keywords, keyword))
                .forEach(keyword -> reasons.add("命中复核沉淀关键词:" + keyword));
        BigDecimal ratio = BigDecimal.valueOf(hitCount)
                .divide(BigDecimal.valueOf(learnedKeywords.size()), 4, RoundingMode.HALF_UP);
        return ratio.multiply(new BigDecimal("0.15"));
    }

    /**
     * 判断是否命中排除关键词；命中后候选模板会被立即淘汰。
     */
    private boolean containsForbiddenKeyword(DocumentMatchRule rule, Set<String> keywords, List<String> reasons) {
        if (rule.getForbiddenKeywords() == null || rule.getForbiddenKeywords().isEmpty()) {
            return false;
        }
        for (String forbiddenKeyword : rule.getForbiddenKeywords()) {
            if (containsKeyword(keywords, forbiddenKeyword)) {
                reasons.add("命中排除关键词:" + forbiddenKeyword);
                return true;
            }
        }
        return false;
    }

    /**
     * 按业务表头规则的命中比例计算分数，最高贡献 0.25。
     */
    private BigDecimal headerScore(DocumentMatchRule rule, List<String> headers, List<String> reasons) {
        if (rule.getHeaderSynonyms() == null || rule.getHeaderSynonyms().isEmpty()) {
            return BigDecimal.ZERO;
        }
        int total = 0;
        int matched = 0;
        for (Map.Entry<String, List<String>> entry : rule.getHeaderSynonyms().entrySet()) {
            total++;
            if (matchesAnyHeader(headers, entry.getValue())) {
                matched++;
                reasons.add("命中表头规则:" + entry.getKey());
            }
        }
        if (matched == 0 || total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(matched)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("0.25"));
    }

    /**
     * 判断任一实际表头是否与同义词完全匹配或存在包含关系。
     */
    private boolean matchesAnyHeader(List<String> headers, List<String> synonyms) {
        if (headers == null || headers.isEmpty() || synonyms == null || synonyms.isEmpty()) {
            return false;
        }
        for (String header : headers) {
            String normalizedHeader = normalize(header);
            for (String synonym : synonyms) {
                String normalizedSynonym = normalize(synonym);
                if (normalizedHeader.equals(normalizedSynonym)
                        || normalizedHeader.contains(normalizedSynonym)
                        || normalizedSynonym.contains(normalizedHeader)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 将文档特征中的多张表头展平为单一列表，供模板规则统一匹配。
     */
    private List<String> flattenHeaders(DocumentFeatures features) {
        if (features == null || features.getTableHeaders() == null) {
            return List.of();
        }
        return features.getTableHeaders().stream()
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    /**
     * 合并高频词、文本样本、表头和候选机构，并统一归一化为路由关键词集合。
     */
    private Set<String> collectKeywords(DocumentFeatures features) {
        Set<String> keywords = new LinkedHashSet<>();
        if (features == null) {
            return keywords;
        }
        // 文本样本、表头、机构名和高频词被统一归一化，形成面向路由的轻量关键词画像。
        addAllNormalized(keywords, features.getTopKeywords());
        addAllNormalized(keywords, features.getTextSamples());
        addAllNormalized(keywords, flattenHeaders(features));
        addAllNormalized(keywords, features.getCandidateInstitutions());
        return keywords;
    }

    /**
     * 将非空字符串归一化后加入目标集合。
     */
    private void addAllNormalized(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(StrUtil::isNotBlank)
                .map(this::normalize)
                .forEach(target::add);
    }

    /**
     * 使用归一化后的相等或双向包含关系判断关键词是否命中。
     */
    private boolean containsKeyword(Set<String> keywords, String candidate) {
        if (StrUtil.isBlank(candidate)) {
            return false;
        }
        String normalized = normalize(candidate);
        return keywords.stream()
                .anyMatch(item -> item.equals(normalized) || item.contains(normalized) || normalized.contains(item));
    }

    /**
     * 去除常见分隔符并转为小写，降低版式和大小写差异对路由的影响。
     */
    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\s_\\-:/\\\\()\\[\\]{}]+", "")
                .toLowerCase(Locale.ROOT);
    }

    /** 单个候选模板的内部评分结果。 */
    private record CandidateScore(BigDecimal score, List<String> reasons, boolean matched) {
        /** 创建一个被硬规则淘汰的候选结果。 */
        static CandidateScore unmatched() {
            return new CandidateScore(BigDecimal.ZERO, List.of(), false);
        }
    }
}
