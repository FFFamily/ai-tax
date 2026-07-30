package com.rcszh.tax.parser;

import cn.hutool.json.JSONUtil;
import com.rcszh.tax.ai.DeepSeekAi;
import com.rcszh.tax.dto.ExcelParseResult;
import com.rcszh.tax.entity.AIParseResult;
import com.rcszh.tax.entity.ExcelFileRule;
import com.rcszh.tax.ir.ParsePreparationResult;
import com.rcszh.tax.ir.ParsePreparationService;
import com.rcszh.tax.route.DocumentRouter;
import com.rcszh.tax.server.DocumentServer;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.util.ExcelUtil;
import com.rcszh.tax.util.FileUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class ExcelParser extends BaseParser{
    private static final Logger logger = LoggerFactory.getLogger(ExcelParser.class);
    @Resource
    private DocumentServer documentServer;
    @Resource
    private DeepSeekAi deepSeekAi;
    @Resource
    private ParsePreparationService parsePreparationService;
    @Resource
    private DocumentRouter documentRouter;
    @Override
    public AIParseResult  doParse(Map<String, Object> info) {
        String fileRule = (String) info.get(DocumentTaskServer.Item.FILE_RULE);
        String fileUrl = (String) info.get(DocumentTaskServer.Item.FILE_URL);
        ExcelFileRule excelFileRule;
        if (fileRule == null || fileRule.isBlank()){
            // 未配置时默认读取第一个 sheet，保证普通银行流水也能直接跑通。
            excelFileRule = new ExcelFileRule();
            excelFileRule.setSheetNum(0);
        }else {
            excelFileRule = JSONUtil.toBean(fileRule, ExcelFileRule.class);
        }
        File file = null;
        try {
            file = FileUtil.downloadFile(fileUrl);
            List<ExcelParseResult> results = ExcelUtil.readExcel(file, excelFileRule);
            // Excel 无需 OCR，直接把行列数据标准化后参与模板路由与后处理。
            ParsePreparationResult preparation = parsePreparationService.prepareExcel(results);
            String documentId = resolveDocumentId(info, preparation, "excel", documentRouter);
            logger.info("对应的文档Id：{}", documentId);
            Map<String, Object> document = documentServer.getDocument(documentId);
            if (document == null) {
                logger.error("文件获取失败：{}", documentId);
                return null;
            }
            info.put(DocumentTaskServer.Item.PREPARED_TRANSACTION_LINES, preparation.getTransactionLines());
            info.put(DocumentTaskServer.Item.PREPARED_DOCUMENT_FEATURES, preparation.getDocumentFeatures());
            String prompt = document.get(DocumentServer.PROMPT).toString();
            List<Map<String, Object>> mapping = documentServer.getMapping(documentId);
            prompt = replacePrompt(prompt,mapping);
            if (prompt != null) {
                AIParseResult aiParseResult = deepSeekAi.chat(results,prompt,null, document);
                return attachTaskMetadata(attachPreparation(aiParseResult, preparation), info);
            }
            FileUtil.deleteTempFile(file);
            return null;
        } catch (IOException e) {
            throw new RuntimeException("文件下载失败："+ e.getMessage());
        }finally {
            if (file != null) {
                FileUtil.deleteTempFile(file);
            }
        }
    }
}
