package com.rcszh.tax.server;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rcszh.tax.entity.AIParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;


public class AIDocumentParseServer {

    /**
     * 生成AI解析的Prompt
     *
     * @return 完整的Prompt字符串
     */
    public static String generateParsePrompt() {
        StringBuilder prompt = new StringBuilder();

        // 系统角色定义
        // 你是一个专业的文档解析专家，擅长从PDF文档中提取结构化数据。
        prompt.append("""
                你是一个专业的数据解析专家，擅长从文档数据中提取结构化数据。
                用户会给你提供对应的数据解析规则，从数据解析结果中识别表格，并将表格数据映射到业务对象。
                同时用户会给你提供其需要的解析的 record JSON表格字段格式，若没提供，则按照你的理解封装对应的record对象
                输入统一为文档分片：tables[].headers 是有序表头，tables[].rows[].cells 是与表头按下标对应的原始单元格，
                tables[].rows[].rowIndex 是来源行号；textBlocks[].text 保存同一文档中的非表格正文。不得因输入分片而合并或丢弃原始行。
                """);
        prompt.append("\n");
        // 任务要求
        prompt.append("""
                ## 任务要求
                2. 字段映射：将表格列映射到目标字段
                3. 数据转换：一旦识别到表格复合条件，需要将表格中的数据转化成 record 对象
                5. 处理多语言：支持中文、英文、繁体中文等多种语言
                6. 错误处理：识别并处理可能的解析错误，如表格结构不匹配、数据缺失等，将数据存入到errorRecords中，根据用户指定的数据结构进行返回，没有指定则不需要返回errorRecords。
                """);
        // 输出格式要求
        prompt.append("## 输出格式要求\n");
        prompt.append("请返回JSON格式，包含以下字段：\n");
        prompt.append("```json\n\n");
        /**
         * "errorRecords":[
         *                     {
         *                         "oldRecord": {
         *                             "head":["表头1","表头2","表头3"],
         *                             "items":[["1","2","3"],["4","5","6"]],
         *                         },
         *                         "reason":""
         *                     }
         *                   ]
         */
        prompt.append("""
                {
                  "warnings": ["表格1的表头格式不标准，可能存在解析错误"],
                  "records": [],
                  "errorRecords":[]
                }
                """);
        return prompt.toString();
    }

    /**
     * 解析AI返回的JSON结果
     *
     * @param aiResponse AI返回的JSON字符串
     * @return 解析结果对象
     */
    public static AIParseResult parseAIResponse(String aiResponse) {
        try {
            // 先把 markdown 包裹、解释性文本剥掉，只保留真正可反序列化的 JSON。
            String jsonStr = extractJsonFromResponse(aiResponse);
            JSONObject json = JSONUtil.parseObj(jsonStr);

            AIParseResult result = new AIParseResult();

            // errorRecords 保留无法稳定映射的原始数据，便于后续人工核对或重新学习。
            JSONArray selectedTables = json.getJSONArray("errorRecords");
            if (selectedTables != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tables = (List<Map<String, Object>>) (List<?>) selectedTables.toList(Map.class);
                result.setErrorRecords(tables);
            }

            // records 是主结果集，后处理链路会继续基于这些结构化记录做二次计算。
            JSONArray fieldMappings = json.getJSONArray("records");
            if (fieldMappings != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> mappings = (List<Map<String, Object>>) (List<?>) fieldMappings.toList(Map.class);
                result.setRecords(mappings);
            }
            // warnings/errors 不阻断主流程，但会进入结果集供质检和复核页展示。
            JSONArray warnings = json.getJSONArray("warnings");
            if (warnings != null) {
                result.setWarnings(warnings.toList(String.class));
            }
            JSONArray errors = json.getJSONArray("errors");
            if (errors != null) {
                result.setErrors(errors.toList(String.class));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("解析AI响应失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从AI响应中提取JSON部分
     * AI可能返回包含markdown代码块或其他格式的文本
     */
    private static String extractJsonFromResponse(String response) {
        // 优先处理 ```json 代码块，因为这是大模型最常见的稳定输出形式。
        if (response.contains("```json")) {
            int start = response.indexOf("```json") + 7;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }

        // 兼容模型未标注 json 语言类型、但仍用代码块包裹结果的情况。
        if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.indexOf("```", start);
            if (end > start) {
                String extracted = response.substring(start, end).trim();
                if (extracted.startsWith("{")) {
                    return extracted;
                }
            }
        }

        // 最后兜底直接截取首尾大括号，尽量提升容错率。
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}") + 1;
        if (start >= 0 && end > start) {
            return response.substring(start, end);
        }

        // 如果仍无法定位 JSON，则交给上层解析报错，保留原始响应便于排查。
        return response.trim();
    }

}
