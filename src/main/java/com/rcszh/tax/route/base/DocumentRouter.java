package com.rcszh.tax.route.base;

import com.rcszh.tax.route.DocumentRouteContext;
import com.rcszh.tax.route.DocumentRouteResult;

/**
 * 文档模板路由抽象。
 *
 * <p>解析器在完成 PDF/Excel 基础预处理后，通过该接口把文件类型、文本特征和用户指定的
 * 材料类型映射到一个具体的文档模板。
 * 实现可以使用规则、模型或组合策略，但不得修改输入上下文。</p>
 */
public interface DocumentRouter {

    /**
     * 根据文档上下文选择最匹配的模板。
     *
     * @param context 路由所需的文件元数据和预处理特征
     * @return 匹配结果；没有候选模板或无法形成有效判断时返回 {@code null}
     */
    DocumentRouteResult route(DocumentRouteContext context);
}
