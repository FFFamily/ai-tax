package com.rcszh.tax.workflow;

import com.rcszh.tax.enums.IncomeMaterialTypeEnum;
import com.rcszh.tax.enums.OverseasIncomeTypeEnum;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 固定文档流程注册表，以材料类型枚举编码作为唯一流程编码。
 */
@Component
public class DocumentWorkflowRegistry {
    private static final String COMMON_ERROR_RULE =
            "无法可靠映射的原始行必须写入 errorRecords，并保留原始数据和不匹配原因。";

    private final Map<String, DocumentWorkflow> workflows;

    public DocumentWorkflowRegistry() {
        Map<String, DocumentWorkflow> definitions = new LinkedHashMap<>();
        Arrays.stream(OverseasIncomeTypeEnum.values())
                .flatMap(incomeType -> incomeType.getMaterials().stream()
                        .map(materialType -> createWorkflow(incomeType, materialType)))
                .forEach(workflow -> definitions.put(normalize(workflow.code()), workflow));
        this.workflows = Map.copyOf(definitions);
    }

    /**
     * 获取指定固定流程；编码为空或未注册时立即终止任务。
     */
    public DocumentWorkflow require(String workflowCode) {
        if (workflowCode == null || workflowCode.isBlank()) {
            throw new IllegalStateException("任务项缺少固定流程编码");
        }
        DocumentWorkflow workflow = workflows.get(normalize(workflowCode));
        if (workflow == null) {
            throw new IllegalStateException("未注册的固定文档流程: " + workflowCode);
        }
        return workflow;
    }

    public List<DocumentWorkflow> list() {
        return List.copyOf(workflows.values());
    }

    /** 返回所得类型与材料类型对应的稳定流程编码。 */
    public String codeOf(OverseasIncomeTypeEnum incomeType, IncomeMaterialTypeEnum materialType) {
        if (incomeType == null || materialType == null || !incomeType.supports(materialType)) {
            throw new IllegalArgumentException("所得类型与材料类型不匹配");
        }
        return incomeType.name() + "__" + materialType.name();
    }

    private DocumentWorkflow createWorkflow(OverseasIncomeTypeEnum incomeType,
                                            IncomeMaterialTypeEnum materialType) {
        String businessType = materialType.getFixedDocumentType();
        if ("BANK_STATEMENT".equals(businessType)) {
            return bankStatement(incomeType, materialType);
        }
        if ("DIVIDEND".equals(businessType)) {
            return dividend(incomeType, materialType);
        }
        if ("BROKER_STATEMENT".equals(businessType)) {
            return brokerStatement(incomeType, materialType);
        }
        return generic(incomeType, materialType);
    }

    private DocumentWorkflow bankStatement(OverseasIncomeTypeEnum incomeType,
                                           IncomeMaterialTypeEnum materialType) {
        return workflow(incomeType, materialType, "BANK_STATEMENT", """
                识别银行流水中的每一笔交易。统一借贷方向和金额符号，保留交易日期、摘要、交易对手、
                币种、金额、余额及账号信息。不得合并或丢弃原始交易行。
                """, List.of(
                field("transactionDate", "交易发生日期"),
                field("valueDate", "银行实际入账日期"),
                field("summary", "交易摘要或用途"),
                field("counterparty", "付款方或收款方名称"),
                field("debitAmount", "支出金额，无值时留空"),
                field("creditAmount", "收入金额，无值时留空"),
                field("amount", "带方向的交易金额"),
                field("currency", "ISO币种或原文币种"),
                field("balance", "交易后账户余额"),
                field("accountNumber", "银行账号或遮罩账号")
        ));
    }

    private DocumentWorkflow dividend(OverseasIncomeTypeEnum incomeType,
                                      IncomeMaterialTypeEnum materialType) {
        return workflow(incomeType, materialType, "DIVIDEND", """
                识别全部股息、红利收入及对应境外预扣税记录。优先按同一日期、付款方和币种建立关联，
                分别输出净额、预扣税和毛额；无法确认关联关系时保留原始记录并降低置信度。
                """, List.of(
                field("date", "股息发生或入账日期"),
                field("payer", "股息支付机构或公司"),
                field("currency", "股息及税费币种"),
                field("netAmount", "实际到账金额"),
                field("withholdingTax", "境外预扣税金额"),
                field("grossAmount", "净额与预扣税之和"),
                field("summary", "股息业务描述")
        ));
    }

    private DocumentWorkflow brokerStatement(OverseasIncomeTypeEnum incomeType,
                                             IncomeMaterialTypeEnum materialType) {
        return workflow(incomeType, materialType, "BROKER_STATEMENT", """
                识别证券账户中的买入、卖出、拆股、合股、股息和费用事件。每个事件独立输出，
                保留证券代码、数量、价格、金额、币种和费用，不得将多笔成交合并为一笔。
                """, List.of(
                field("date", "证券事件发生日期"),
                field("action", "BUY、SELL、SPLIT、DIVIDEND或FEE"),
                field("symbol", "股票或证券代码"),
                field("quantity", "成交或变动数量"),
                field("price", "单位成交价格"),
                field("amount", "事件总金额"),
                field("currency", "交易币种"),
                field("fees", "佣金及其他费用"),
                field("withholdingTax", "股息等事件的预扣税"),
                field("summary", "原始事件说明")
        ));
    }

    private DocumentWorkflow generic(OverseasIncomeTypeEnum incomeType,
                                     IncomeMaterialTypeEnum materialType) {
        return workflow(incomeType, materialType, materialType.name(), """
                解析“%s”中的全部关键业务信息。优先保持原文含义和金额口径，存在多条明细时逐条输出，
                无法确认的字段留空并降低置信度，不得根据常识虚构数据。
                """.formatted(materialType.getLabel()), List.of(
                field("documentTitle", "文档标题或材料名称"),
                field("issuer", "文档出具机构或签署方"),
                field("recipient", "收件人、合同相对方或纳税人"),
                field("documentDate", "开具、签署或业务发生日期"),
                field("amount", "当前业务记录金额"),
                field("currency", "金额对应币种"),
                field("summary", "当前记录的关键内容摘要"),
                field("rawText", "支持当前记录的关键原文")
        ));
    }

    private DocumentWorkflow workflow(OverseasIncomeTypeEnum incomeType,
                                      IncomeMaterialTypeEnum materialType,
                                      String documentType,
                                      String prompt,
                                      List<DocumentOutputField> fields) {
        Set<String> capabilities = new LinkedHashSet<>();
        if (materialType.getFixedDocumentType() != null) {
            capabilities.add(materialType.getFixedDocumentType());
        }
        if (incomeType == OverseasIncomeTypeEnum.DIVIDEND_INCOME) {
            capabilities.add("DIVIDEND");
        }
        return new DocumentWorkflow(
                codeOf(incomeType, materialType),
                documentType,
                capabilities,
                20,
                prompt,
                COMMON_ERROR_RULE,
                fields
        );
    }

    private DocumentOutputField field(String code, String description) {
        return new DocumentOutputField(code, description);
    }

    private String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
