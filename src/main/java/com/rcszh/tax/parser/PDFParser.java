package com.rcszh.tax.parser;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.ai.DeepSeekAi;
import com.rcszh.tax.dto.MinerUFileParseResult;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.ir.ParsedDocument;
import com.rcszh.tax.ir.ParsePreparationService;
import com.rcszh.tax.workflow.DocumentWorkflow;
import com.rcszh.tax.workflow.DocumentWorkflowRegistry;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * PDF 解析器：
 * 先消费远端 OCR/表格识别结果，再按固定流程完成 Prompt 组装和 AI 抽取。
 */
@Component
public class PDFParser extends BaseParser{
    private static final Logger logger = LoggerFactory.getLogger(PDFParser.class);
    @Resource
    private DocumentWorkflowRegistry workflowRegistry;
    @Resource
    private DeepSeekAi deepSeekAi;
    @Resource
    private ParsePreparationService parsePreparationService;

    @Override
    public boolean requiresRemoteParse() {
        return true;
    }
    @Override
    public boolean supports(DocumentTaskItem item) {
        return item.getFileUrl() != null && item.getFileUrl().toLowerCase(Locale.ROOT).contains(".pdf");
    }

    @Override
    public AIParseResult doParse(DocumentTaskItem info) {
        String result = info.getTaskResult();
        if (StrUtil.isBlank(result)) {
            logger.info("缺失需要解析的信息");
            return null;
        }
        List<MinerUFileParseResult> parseResults = JSONUtil.parseArray(result).toList(MinerUFileParseResult.class);
        // 来源适配只保留无损文档结构，业务字段解释延迟到专项处理阶段。
        ParsedDocument document = parsePreparationService.preparePdf(parseResults);
        DocumentWorkflow workflow = resolveWorkflow(info, workflowRegistry);
        logger.info("使用固定文档流程：{}", workflow.code());
        info.setPreparedDocument(document);
        logger.info("开始执行AI解析");
        String prompt = workflow.buildPrompt();
        // 追加容错约束，避免 OCR 缺列或错位时模型直接丢弃候选数据。
        String agentCall = """
                     介于OCR识别的问题，甚至可能缺失某列或者某行数据，需要根据语义理解进行匹配,匹配程度大于50%也可以视为是需要的数据
                     如果匹配程度高于90%，可以手动改变表格结构并存入records数组中，同时也要在errorRecords数组中补充上不匹配原因
                     不能丢弃任何一个数据，如果不匹配，直接将原格式数据补充在errorRecords数组中并附带上不匹配原因
                """;
        return deepSeekAi.chat(document, prompt, agentCall, workflow);
    }
}
