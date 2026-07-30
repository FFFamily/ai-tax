package com.rcszh.tax.parser;

import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.ir.ParsePreparationResult;
import com.rcszh.tax.route.DocumentRouteContext;
import com.rcszh.tax.route.DocumentRouteResult;
import com.rcszh.tax.route.DocumentRouter;
import com.rcszh.tax.server.DocumentServer;
import com.rcszh.tax.server.DocumentTaskServer;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseParser {

    /**
     * 执行解析
     * @param info 用户上传解析记录
     * @return 解析结果
     */
    public abstract AIParseResult doParse(Map<String, Object> info);

    /**
     * 将模板字段映射追加到 prompt 中，让模型明确输出 record 的目标结构。
     */
    public String replacePrompt(String prompt,List<Map<String, Object>> mapping) {
        if (mapping != null) {
            String mappingJsonStr = DocumentServer.toMappingJsonStr(mapping);
            prompt += "目标输出格式: "+mappingJsonStr;
        }
        return prompt;
    }

    protected AIParseResult attachPreparation(AIParseResult parseResult, ParsePreparationResult preparation) {
        if (parseResult == null || preparation == null) {
            return parseResult;
        }
        // 将预处理阶段得到的结构化线索挂到 globalParam，供后处理、排错和人工复核复用。
        parseResult.getGlobalParam().put("documentFeatures", preparation.getDocumentFeatures());
        parseResult.getGlobalParam().put("transactionLineCount", preparation.getTransactionLines().size());
        parseResult.getGlobalParam().put("transactionLineSample",
                preparation.getTransactionLines().stream().limit(20).toList());
        parseResult.getGlobalParam().put("tableCount", preparation.getHtmlTables().size());
        return parseResult;
    }

    protected AIParseResult attachTaskMetadata(AIParseResult parseResult, Map<String, Object> info) {
        if (parseResult == null || info == null) {
            return parseResult;
        }
        // 任务侧元数据统一回填到结果中，避免后续链路再次查询任务表做上下文拼装。
        if (info.get(DocumentTaskServer.Item.RESOLVED_DOCUMENT_ID) != null) {
            parseResult.getGlobalParam().put("resolvedDocumentId", info.get(DocumentTaskServer.Item.RESOLVED_DOCUMENT_ID));
        }
        if (info.get(DocumentTaskServer.Item.DOCUMENT_ID) != null) {
            parseResult.getGlobalParam().put("documentId", info.get(DocumentTaskServer.Item.DOCUMENT_ID));
        }
        if (info.get(DocumentTaskServer.Item.REQUESTED_DOCUMENT_TYPE) != null) {
            parseResult.getGlobalParam().put("requestedDocumentType", info.get(DocumentTaskServer.Item.REQUESTED_DOCUMENT_TYPE));
        }
        if (info.get(DocumentTaskServer.Item.NEED_HUMAN_REVIEW) != null) {
            parseResult.getGlobalParam().put("needHumanReview",
                    Boolean.valueOf(info.get(DocumentTaskServer.Item.NEED_HUMAN_REVIEW).toString()));
        }
        Object routeSummary = info.get(DocumentTaskServer.Item.ROUTE_SUMMARY);
        if (routeSummary instanceof Map<?, ?> routeInfo) {
            parseResult.getGlobalParam().put("routeSummary", routeInfo);
        }
        Object routeReason = info.get(DocumentTaskServer.Item.ROUTE_REASON);
        if (routeReason != null) {
            parseResult.getGlobalParam().put("routeReason", routeReason);
        }
        Object routeConfidence = info.get(DocumentTaskServer.Item.ROUTE_CONFIDENCE);
        if (routeConfidence != null) {
            parseResult.getGlobalParam().put("routeConfidence", routeConfidence);
        }
        Object routeVariant = info.get(DocumentTaskServer.Item.ROUTE_VARIANT);
        if (routeVariant != null) {
            parseResult.getGlobalParam().put("routeVariant", routeVariant);
        }
        return parseResult;
    }

    protected String resolveDocumentId(Map<String, Object> info,
                                       ParsePreparationResult preparation,
                                       String fileType,
                                       DocumentRouter documentRouter) {
        Object configuredDocumentId = info.get(DocumentTaskServer.Item.DOCUMENT_ID);
        if (configuredDocumentId != null && !configuredDocumentId.toString().isBlank()) {
            // 用户显式指定模板时直接走人工路由结果，并将路由说明写回任务项供审计使用。
            info.put(DocumentTaskServer.Item.RESOLVED_DOCUMENT_ID, configuredDocumentId.toString());
            info.put(DocumentTaskServer.Item.ROUTE_CONFIDENCE, BigDecimal.ONE);
            info.put(DocumentTaskServer.Item.ROUTE_REASON, "使用显式指定的文档模板");
            info.put(DocumentTaskServer.Item.NEED_HUMAN_REVIEW, Boolean.FALSE);
            info.put(DocumentTaskServer.Item.ROUTE_SUMMARY, buildRouteSummary(
                    configuredDocumentId.toString(),
                    (String) info.get(DocumentTaskServer.Item.REQUESTED_DOCUMENT_TYPE),
                    "",
                    BigDecimal.ONE,
                    false,
                    "manual",
                    List.of("使用显式指定的文档模板")
            ));
            return configuredDocumentId.toString();
        }
        DocumentRouteContext routeContext = new DocumentRouteContext();
        routeContext.setRequestedDocumentType((String) info.get(DocumentTaskServer.Item.REQUESTED_DOCUMENT_TYPE));
        routeContext.setFileUrl((String) info.get(DocumentTaskServer.Item.FILE_URL));
        routeContext.setFileType(fileType);
        routeContext.setDocumentFeatures(preparation.getDocumentFeatures());
        // 未显式指定模板时，依赖预处理提取到的关键词、表头、机构名等特征完成文档路由。
        DocumentRouteResult routeResult = documentRouter.route(routeContext);
        if (routeResult == null || routeResult.getDocumentId() == null || routeResult.getDocumentId().isBlank()) {
            throw new RuntimeException("未找到匹配的文档模板");
        }
        info.put(DocumentTaskServer.Item.DOCUMENT_ID, routeResult.getDocumentId());
        info.put(DocumentTaskServer.Item.RESOLVED_DOCUMENT_ID, routeResult.getDocumentId());
        info.put(DocumentTaskServer.Item.ROUTE_VARIANT, routeResult.getVariant());
        info.put(DocumentTaskServer.Item.ROUTE_CONFIDENCE, routeResult.getConfidence());
        String routeReason = String.join("；", routeResult.getReasons());
        if (routeResult.getRouteSource() != null) {
            routeReason = "[" + routeResult.getRouteSource() + "] " + routeReason;
        }
        info.put(DocumentTaskServer.Item.ROUTE_REASON, routeReason);
        info.put(DocumentTaskServer.Item.NEED_HUMAN_REVIEW, routeResult.isNeedHumanReview());
        info.put(DocumentTaskServer.Item.ROUTE_SUMMARY, buildRouteSummary(
                routeResult.getDocumentId(),
                routeResult.getDocumentType(),
                routeResult.getVariant(),
                routeResult.getConfidence(),
                routeResult.isNeedHumanReview(),
                routeResult.getRouteSource() == null ? "rule" : routeResult.getRouteSource(),
                routeResult.getReasons()
        ));
        return routeResult.getDocumentId();
    }

    private Map<String, Object> buildRouteSummary(String documentId,
                                                  String documentType,
                                                  String variant,
                                                  BigDecimal confidence,
                                                  boolean needHumanReview,
                                                  String routeSource,
                                                  List<String> reasons) {
        // 路由摘要是任务表、复核页和学习反馈共用的最小闭环信息。
        Map<String, Object> routeSummary = new LinkedHashMap<>();
        routeSummary.put("documentId", documentId);
        routeSummary.put("documentType", documentType == null ? "" : documentType);
        routeSummary.put("variant", variant == null ? "" : variant);
        routeSummary.put("confidence", confidence == null ? BigDecimal.ZERO : confidence);
        routeSummary.put("needHumanReview", needHumanReview);
        routeSummary.put("routeSource", routeSource == null ? "rule" : routeSource);
        routeSummary.put("reasons", reasons == null ? List.of() : reasons);
        return routeSummary;
    }
}
