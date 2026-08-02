package com.rcszh.tax.route;

import com.rcszh.tax.ir.DocumentFeatures;
import lombok.Data;

/**
 * 一次文档路由请求的只读语义上下文。
 *
 * <p>上下文由解析器根据上传信息和预处理结果组装，不包含完整文件内容，避免路由阶段重复读取文件。</p>
 */
@Data
public class DocumentRouteContext {
    /** 用户选择的材料/文档类型，用于收窄候选模板范围。 */
    private String requestedDocumentType;
    /** 文件的业务访问标识，主要用于审计和辅助识别，不要求是公网 URL。 */
    private String fileUrl;
    /** 归一化后的输入类型，例如 {@code pdf} 或 {@code excel}。 */
    private String fileType;
    /** 从文档中提取的关键词、表头、机构等轻量特征。 */
    private DocumentFeatures documentFeatures;
}
