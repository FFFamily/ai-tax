package com.rcszh.tax.workflow;

import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 随应用发布的固定文档解析流程。
 *
 * <p>流程定义替代原来的数据库模板，集中描述文档类型、AI 分片方式、提取规则和输出字段。
 * 所有属性均不可变，修改后必须经过代码评审、测试和发布才能生效。</p>
 */
public record DocumentWorkflow(
        String code,
        String documentType,
        Set<String> capabilities,
        int pageStep,
        String prompt,
        String errorRecord,
        List<DocumentOutputField> outputFields) {

    public DocumentWorkflow {
        pageStep = pageStep <= 0 ? 20 : pageStep;
        prompt = prompt == null ? "" : prompt;
        errorRecord = errorRecord == null ? "" : errorRecord;
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        outputFields = outputFields == null ? List.of() : List.copyOf(outputFields);
    }

    /**
     * 生成发送给抽取模型的完整业务提示词。
     */
    public String buildPrompt() {
        StringBuilder result = new StringBuilder();
        if (!errorRecord.isBlank()) {
            result.append("解析失败数据结构（errorRecords）规则如下：\n").append(errorRecord).append('\n');
        }
        result.append(prompt);
        if (!outputFields.isEmpty()) {
            StringJoiner fields = new StringJoiner(", ", "{", "}");
            for (DocumentOutputField field : outputFields) {
                fields.add("\"%s\": \"%s\"".formatted(field.code(), field.description()));
            }
            fields.add("\"record_id\": \"当前生成时间(yyyyMMdd)+随机6位数字或者大写字母\"");
            fields.add("\"confidence\": \"当前记录的置信度，范围0到1，保留一位小数\"");
            result.append("\n目标输出格式：").append(fields);
        }
        return result.toString();
    }

    /** 判断当前固定流程是否启用指定业务能力。 */
    public boolean supports(String capability) {
        return capability != null && capabilities.contains(capability);
    }
}
