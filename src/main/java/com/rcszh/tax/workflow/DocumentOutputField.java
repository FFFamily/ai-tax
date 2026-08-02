package com.rcszh.tax.workflow;

/**
 * 固定文档流程要求 AI 输出的单个业务字段。
 *
 * @param code 稳定字段编码
 * @param label 展示名称
 * @param description 提取口径
 */
public record DocumentOutputField(String code, String label, String description) {
}
