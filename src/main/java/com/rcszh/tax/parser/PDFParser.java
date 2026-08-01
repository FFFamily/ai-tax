package com.rcszh.tax.parser;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.ai.DeepSeekAi;
import com.rcszh.tax.dto.HtmlTable;
import com.rcszh.tax.dto.MinerUFileParseResult;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.ir.ParsePreparationResult;
import com.rcszh.tax.ir.ParsePreparationService;
import com.rcszh.tax.route.DocumentRouter;
import com.rcszh.tax.server.DocumentServer;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PDF 解析器：
 * 先消费远端 OCR/表格识别结果，再完成模板路由、Prompt 组装和 AI 抽取。
 */
@Component
public class PDFParser extends BaseParser{
    private static final Logger logger = LoggerFactory.getLogger(PDFParser.class);
    @Resource
    private DocumentServer documentServer;
    @Resource
    private DeepSeekAi deepSeekAi;
    @Resource
    private ParsePreparationService parsePreparationService;
    @Resource
    private DocumentRouter documentRouter;

    @Override
    public boolean requiresRemoteParse() {
        return true;
    }
    @Override
    public boolean supports(DocumentTaskItem item) {
        return item.getFileUrl().contains("pdf");
    }

    @Override
    public AIParseResult doParse(DocumentTaskItem info) {
        String result = info.getTaskResult();
        if (StrUtil.isBlank(result)) {
            logger.info("缺失需要解析的信息");
            return null;
        }
        List<MinerUFileParseResult> parseResults = JSONUtil.parseArray(result).toList(MinerUFileParseResult.class);
        parseResults.forEach(i -> i.setPageIndex(i.getPage_idx()));
        // 预处理阶段把 OCR 结果转换成可路由、可后处理的标准化中间表示。
        ParsePreparationResult preparation = parsePreparationService.preparePdf(parseResults);
        Long documentId = resolveDocumentId(info, preparation, "pdf", documentRouter);
        logger.info("对应的文档Id：{}", documentId);
        Map<String, Object> document = documentServer.getDocument(documentId);
        if (document == null) {
            logger.error("文件获取失败：{}", documentId);
            return null;
        }
        Object filterType = document.get(DocumentServer.FILTER_TYPE);
        if (filterType != null && StrUtil.isNotBlank(filterType.toString()) && !filterType.toString().equals("all")) {
            // 某些模板只依赖 table / text 子集，先裁剪数据源可减少模型噪音。
            parseResults =  parseResults.stream()
                    .filter(i -> i.getType().equals(filterType))
                    .toList();
            preparation = parsePreparationService.preparePdf(parseResults);
        }
        List<HtmlTable> resultTables = preparation.getHtmlTables();
        info.setTableResult(JSONUtil.toJsonStr(resultTables));
        info.setPreparedTransactionLines(preparation.getTransactionLines());
        info.setPreparedDocumentFeatures(preparation.getDocumentFeatures());
        logger.info("开始执行AI解析");
        StringBuilder promptBuild = new StringBuilder();
        String globalPrompt = Optional.ofNullable(document.get(DocumentServer.GLOBAL_PROMPT)).orElse("").toString();
        if (StrUtil.isNotBlank(globalPrompt)) {
            // 全局参数规则用于提取币种、账户、期间等跨记录共享的信息。
            promptBuild.append("全局参数（globalParam）提取规则如下：");
            promptBuild.append("\n");
            promptBuild.append(globalPrompt);
        }

        String errorRecord = Optional.ofNullable(document.get(DocumentServer.ERROR_RECORD)).orElse("").toString();
        if (StrUtil.isNotBlank(errorRecord)) {
            promptBuild.append("解析失败数据结构（errorRecords）规则如下：");
            promptBuild.append("\n");
            promptBuild.append(errorRecord);
        }
        String prompt = document.get(DocumentServer.PROMPT).toString();
        List<Map<String, Object>> mapping = documentServer.getMapping(documentId);
        prompt = replacePrompt(prompt,mapping);
        if (prompt != null) {
            promptBuild.append(prompt);
            // 追加容错约束，避免 OCR 缺列或错位时模型直接丢弃候选数据。
            String agentCall = """
                     介于OCR识别的问题，甚至可能缺失某列或者某行数据，需要根据语义理解进行匹配,匹配程度大于50%也可以视为是需要的数据
                     如果匹配程度高于90%，可以手动改变表格结构并存入records数组中，同时也要在errorRecords数组中补充上不匹配原因
                     不能丢弃任何一个数据，如果不匹配，直接将原格式数据补充在errorRecords数组中并附带上不匹配原因
                    """;
            AIParseResult aiParseResult = deepSeekAi.chat(parseResults,promptBuild.toString(),agentCall, document);
            return attachTaskMetadata(attachPreparation(aiParseResult, preparation), info);
        }
        return null;
    }
}
