package com.rcszh.tax.route;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.ir.DocumentFeatures;
import com.rcszh.tax.server.DocumentServer;
import com.rcszh.tax.service.ReviewLearningService;
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

@Component
public class RuleBasedDocumentRouter implements DocumentRouter {
    private static final BigDecimal AUTO_PASS_THRESHOLD = new BigDecimal("0.60");

    private final DocumentServer documentServer;
    private final RouteAiFallbackService routeAiFallbackService;
    private final ReviewLearningService reviewLearningService;

    public RuleBasedDocumentRouter(DocumentServer documentServer,
                                   RouteAiFallbackService routeAiFallbackService,
                                   ReviewLearningService reviewLearningService) {
        this.documentServer = documentServer;
        this.routeAiFallbackService = routeAiFallbackService;
        this.reviewLearningService = reviewLearningService;
    }

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
                bestResult.setDocumentId((String) candidate.get(DocumentServer.ID_KEY));
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

    private DocumentRouteResult buildAiFallbackResult(DocumentRouteContext context,
                                                      List<Map<String, Object>> candidates,
                                                      DocumentRouteResult currentRuleResult) {
        // AI 兜底不是替代规则，而是在低置信场景补充判断并保留规则命中原因。
        RouteAiDecision decision = routeAiFallbackService.decide(context, candidates);
        if (decision == null || StrUtil.isBlank(decision.getDocumentId())) {
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
                (String) candidate.get(DocumentServer.ID_KEY),
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

    private BigDecimal mustKeywordScore(DocumentMatchRule rule) {
        if (rule.getMustKeywords() == null || rule.getMustKeywords().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal("0.35");
    }

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

    private void addAllNormalized(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(StrUtil::isNotBlank)
                .map(this::normalize)
                .forEach(target::add);
    }

    private boolean containsKeyword(Set<String> keywords, String candidate) {
        if (StrUtil.isBlank(candidate)) {
            return false;
        }
        String normalized = normalize(candidate);
        return keywords.stream()
                .anyMatch(item -> item.equals(normalized) || item.contains(normalized) || normalized.contains(item));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\s_\\-:/\\\\()\\[\\]{}]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private record CandidateScore(BigDecimal score, List<String> reasons, boolean matched) {
        static CandidateScore unmatched() {
            return new CandidateScore(BigDecimal.ZERO, List.of(), false);
        }
    }
}
