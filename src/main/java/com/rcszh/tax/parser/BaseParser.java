package com.rcszh.tax.parser;

import com.rcszh.tax.dto.executiontask.ExecutionTaskRouteSummaryResponse;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.ir.ParsePreparationResult;
import com.rcszh.tax.route.DocumentRouteContext;
import com.rcszh.tax.route.DocumentRouteResult;
import com.rcszh.tax.route.base.DocumentRouter;
import com.rcszh.tax.server.DocumentServer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public abstract class BaseParser {
    /**
     * 当前解析器是否支持该任务项。
     */
    public abstract boolean supports(DocumentTaskItem item);
    /**
     * 执行解析
     * @param info 用户上传解析记录
     * @return 解析结果
     */
    public abstract AIParseResult doParse(DocumentTaskItem info);
    /**
     * 是否需要提前提交远程 OCR 任务。
     */
    public boolean requiresRemoteParse() {
        return false;
    }

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

    protected AIParseResult attachTaskMetadata(AIParseResult parseResult, DocumentTaskItem info) {
        if (parseResult == null || info == null) {
            return parseResult;
        }
        // 任务侧元数据统一回填到结果中，避免后续链路再次查询任务表做上下文拼装。
        if (info.getResolvedDocumentId() != null) {
            parseResult.getGlobalParam().put("resolvedDocumentId", info.getResolvedDocumentId());
        }
        if (info.getDocumentId() != null) {
            parseResult.getGlobalParam().put("documentId", info.getDocumentId());
        }
        if (info.getRequestedDocumentType() != null) {
            parseResult.getGlobalParam().put("requestedDocumentType", info.getRequestedDocumentType());
        }
        if (info.getNeedHumanReview() != null) {
            parseResult.getGlobalParam().put("needHumanReview", info.getNeedHumanReview());
        }
        if (info.getRouteSummary() != null) {
            parseResult.getGlobalParam().put("routeSummary", info.getRouteSummary());
        }
        if (info.getRouteReason() != null) {
            parseResult.getGlobalParam().put("routeReason", info.getRouteReason());
        }
        if (info.getRouteConfidence() != null) {
            parseResult.getGlobalParam().put("routeConfidence", info.getRouteConfidence());
        }
        if (info.getRouteVariant() != null) {
            parseResult.getGlobalParam().put("routeVariant", info.getRouteVariant());
        }
        return parseResult;
    }

    /**
     * 解析任务项最终使用的文档模板。
     *
     * <p>任务项已显式指定模板时直接采用并记录为人工来源；否则根据预处理特征构建
     * {@link DocumentRouteContext} 调用 {@link DocumentRouter}。路由成功后把模板、置信度、
     * 来源和原因同步回任务项，供后处理、结果展示和人工复核使用。</p>
     *
     * @param info 当前解析任务项，也是路由结果的回写载体
     * @param preparation 文档预处理结果，提供关键词、表头等路由特征
     * @param fileType 归一化后的文件类型
     * @param documentRouter 文档模板路由器
     * @return 最终采用的文档模板 ID
     * @throws RuntimeException 自动路由未找到有效模板时抛出
     */
    protected Long resolveDocumentId(DocumentTaskItem info,
                                       ParsePreparationResult preparation,
                                       String fileType,
                                       DocumentRouter documentRouter) {
        Long configuredDocumentId = info.getDocumentId();
        if (configuredDocumentId != null) {
            // 用户显式指定模板时直接走人工路由结果，并将路由说明写回任务项供审计使用。
            Long documentId = configuredDocumentId;
            info.setResolvedDocumentId(documentId);
            info.setRouteConfidence(BigDecimal.ONE);
            info.setRouteReason("使用显式指定的文档模板");
            info.setNeedHumanReview(Boolean.FALSE);
            info.setRouteSummary(buildRouteSummary(
                    documentId,
                    info.getRequestedDocumentType(),
                    "",
                    BigDecimal.ONE,
                    false,
                    "manual",
                    List.of("使用显式指定的文档模板")
            ));
            return documentId;
        }
        DocumentRouteContext routeContext = new DocumentRouteContext();
        routeContext.setRequestedDocumentType(info.getRequestedDocumentType());
        routeContext.setFileUrl(info.getFileUrl());
        routeContext.setFileType(fileType);
        routeContext.setDocumentFeatures(preparation.getDocumentFeatures());
        // 未显式指定模板时，依赖预处理提取到的关键词、表头、机构名等特征完成文档路由。
        DocumentRouteResult routeResult = documentRouter.route(routeContext);
        if (routeResult == null || routeResult.getDocumentId() == null) {
            throw new RuntimeException("未找到匹配的文档模板");
        }
        info.setDocumentId(routeResult.getDocumentId());
        info.setResolvedDocumentId(routeResult.getDocumentId());
        info.setRouteVariant(routeResult.getVariant());
        info.setRouteConfidence(routeResult.getConfidence());
        String routeReason = String.join("；", routeResult.getReasons());
        if (routeResult.getRouteSource() != null) {
            routeReason = "[" + routeResult.getRouteSource() + "] " + routeReason;
        }
        info.setRouteReason(routeReason);
        info.setNeedHumanReview(routeResult.isNeedHumanReview());
        info.setRouteSummary(buildRouteSummary(
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

    private ExecutionTaskRouteSummaryResponse buildRouteSummary(Long documentId,
                                                  String documentType,
                                                  String variant,
                                                  BigDecimal confidence,
                                                  boolean needHumanReview,
                                                  String routeSource,
                                                  List<String> reasons) {
        // 路由摘要是任务表、复核页和学习反馈共用的最小闭环信息。
        ExecutionTaskRouteSummaryResponse routeSummary = new ExecutionTaskRouteSummaryResponse();
        routeSummary.setDocumentId(documentId);
        routeSummary.setDocumentType(documentType == null ? "" : documentType);
        routeSummary.setVariant(variant == null ? "" : variant);
        routeSummary.setConfidence(confidence == null ? BigDecimal.ZERO : confidence);
        routeSummary.setNeedHumanReview(needHumanReview);
        routeSummary.setRouteSource(routeSource == null ? "rule" : routeSource);
        routeSummary.setReasons(reasons == null ? List.of() : reasons);
        return routeSummary;
    }
}
