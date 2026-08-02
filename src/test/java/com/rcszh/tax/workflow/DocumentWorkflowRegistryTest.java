package com.rcszh.tax.workflow;

import com.rcszh.tax.enums.IncomeMaterialTypeEnum;
import com.rcszh.tax.enums.OverseasIncomeTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentWorkflowRegistryTest {
    private final DocumentWorkflowRegistry registry = new DocumentWorkflowRegistry();

    @Test
    void registersEveryMaterialTypeAsFixedWorkflow() {
        int expectedCount = java.util.Arrays.stream(OverseasIncomeTypeEnum.values())
                .mapToInt(incomeType -> incomeType.getMaterials().size())
                .sum();
        assertThat(registry.list()).hasSize(expectedCount);
        for (OverseasIncomeTypeEnum incomeType : OverseasIncomeTypeEnum.values()) {
            for (IncomeMaterialTypeEnum materialType : incomeType.getMaterials()) {
                DocumentWorkflow workflow = registry.require(registry.codeOf(incomeType, materialType));
                assertThat(workflow.name()).contains(incomeType.getLabel(), materialType.getLabel());
                assertThat(workflow.buildPrompt()).contains("目标输出格式");
            }
        }
    }

    @Test
    void usesSpecializedDefinitionsForStructuredMaterials() {
        DocumentWorkflow dividendBank = registry.require("DIVIDEND_INCOME__BANK_STATEMENT");
        assertThat(dividendBank.documentType()).isEqualTo("BANK_STATEMENT");
        assertThat(dividendBank.capabilities()).contains("BANK_STATEMENT", "DIVIDEND");
        assertThat(registry.require("DIVIDEND_INCOME__DIVIDEND_DETAIL").documentType()).isEqualTo("DIVIDEND");
        assertThat(registry.require("DIVIDEND_INCOME__BROKER_DAILY_STATEMENT").documentType())
                .isEqualTo("BROKER_STATEMENT");
    }

    @Test
    void rejectsUnknownWorkflowCode() {
        assertThatThrownBy(() -> registry.require("UNKNOWN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未注册");
    }
}
