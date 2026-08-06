package com.rcszh.tax.parser;

import com.rcszh.tax.ai.DeepSeekAi;
import com.rcszh.tax.dto.ExcelParseResult;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.ir.ParsedDocument;
import com.rcszh.tax.ir.ParsePreparationService;
import com.rcszh.tax.util.ExcelUtil;
import com.rcszh.tax.workflow.DocumentWorkflow;
import com.rcszh.tax.workflow.DocumentWorkflowRegistry;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class ExcelParser extends BaseParser{
    private static final Logger logger = LoggerFactory.getLogger(ExcelParser.class);
    @Resource
    private DocumentWorkflowRegistry workflowRegistry;
    @Resource
    private DeepSeekAi deepSeekAi;
    @Resource
    private ParsePreparationService parsePreparationService;

    @Override
    public boolean supports(DocumentTaskItem item) {
        return ExcelUtil.checkFileSuffix(item.getFileUrl());
    }

    @Override
    public AIParseResult doParse(DocumentTaskItem info) {
        String fileUrl = info.getFileUrl();
        Path localFilePath = info.getLocalFilePath();
        if (localFilePath == null || !Files.isRegularFile(localFilePath)) {
            throw new IllegalStateException("Excel 本地文件不存在: " + fileUrl);
        }
        List<ExcelParseResult> results = ExcelUtil.readExcel(localFilePath.toFile());
        // Excel 与 PDF 在此收敛为相同的无损文档结构。
        ParsedDocument document = parsePreparationService.prepareExcel(results);
        DocumentWorkflow workflow = resolveWorkflow(info, workflowRegistry);
        logger.info("使用固定文档流程：{}", workflow.code());
        info.setPreparedDocument(document);
        String prompt = workflow.buildPrompt();
        return deepSeekAi.chat(document, prompt, "", workflow);
    }
}
