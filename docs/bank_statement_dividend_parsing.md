# 银行流水股息提取：类型识别 + Prompt 路由 + 结构化规范化（可落地方案）

## 0. 背景与目标
业务场景：用户在某个“分类”（如“股息收入”）下上传证明材料，材料可能是：

- 不同银行导出的 PDF/图片版银行流水（版式/表头/字段名称差异很大）
- 用户自己整理的 Excel（列名、顺序、币种/方向表达方式不统一）
- 第三方机构生成的对账单/明细（可能更像报表而不是流水）

目标：尽可能自动地从材料中提取“股息相关流水”，输出统一的结构化结果，并支持可追溯与人工兜底。

当前项目现状（从代码推断）：

- PDF 解析：`src/main/java/com/rcszh/tax/server/ParseFileServer.java` 调 MinerU 抽取（当前 `is_ocr=false`），`src/main/java/com/rcszh/tax/parser/PDFParser.java` 将抽取结果交给 `DeepSeekAi` + 文档配置 Prompt 做结构化抽取。
- Excel 解析：`src/main/java/com/rcszh/tax/parser/ExcelParser.java` 用 `ExcelUtil` 读取后交给 `DeepSeekAi` + 文档配置 Prompt。
- Prompt/字段映射：通过 `DocumentServer` 从动态表（`tax_question_document` 等）读取配置：`src/main/java/com/rcszh/tax/server/DocumentServer.java`。

痛点本质：**输入材料形态差异太大，而你目前把“识别 + 抽取 + 规范化”揉在一个 Prompt 里**，导致 Prompt 很难统一、鲁棒性差、维护成本高。

---

## 1. 总体改良：把“一个大 Prompt”拆成可扩展的流水线
建议落地成 4 层（每层都可插拔、可灰度）：

1) **文档预处理 / 统一中间表示（IR）**
   - 把各种来源（PDF/图片/OCR/Excel/第三方报表）先尽量转成一个“通用交易流水”中间表示 `TransactionLine[]`。
2) **类型识别（Document Routing）**
   - 先判定“这份数据更像哪一类”（银行流水/券商对账单/股息汇总表/用户自制表等），并在候选类型集合里做路由。
3) **抽取器（Extractor）**
   - 针对“类型”选择不同的抽取策略（规则/LLM/混合），但输出统一 Schema。
4) **规范化 + 质量评估 + 人工兜底**
   - 做字段规范、金额方向统一、币种/日期标准化、证据链（evidence）保留；低置信进入人工复核。

核心原则：**先把“版式差异”消掉，再做“业务语义抽取”。**  
对“银行流水提股息”来说，最稳的统一点通常不是“表头长什么样”，而是“它最终都是一串交易流水”。

---

## 2. 文档类型识别（Routing）怎么做更稳
你提出的“每种类型一个简约描述 → 先识别类型 → 再选 Prompt”是可行的，但建议做成“规则优先 + AI 兜底”的混合路由：

### 2.1 先做轻量特征抽取（不依赖 LLM）
从解析结果里提取特征（features），用于后续路由：

- 文件类型：pdf/xlsx/xls/csv/image
- 文本特征：前 N 行文本、出现频次最高的关键词、银行名/机构名候选
- 表格特征：表头集合（header set）、列数、日期列识别情况、金额列识别情况
- 版式特征：是否存在“余额/摘要/对方户名/交易渠道”等典型银行字段

这一步的产物建议是：

```json
{
  "fileType": "pdf",
  "languageHint": "zh",
  "topKeywords": ["交日期", "摘要", "余额", "人民币"],
  "tableHeaders": [["交易日期", "摘要", "收入", "支出", "余额"]],
  "hasBalanceColumn": true,
  "hasDebitCreditSplit": true
}
```

### 2.2 路由策略：规则匹配优先，LLM 只处理不确定样本
给每个“文档类型”配置一份 `matchRule`（可存在 DB/配置表），例如：

- 必须包含关键词：`["交易日期","余额"]`
- 表头同义词集合：`交易日期 ~ 日期 ~ Date ~ Transaction Date`
- 排除关键词：`["成交","持仓","ISIN"]`（更像券商报表）
- 文件类型约束：只接受 excel / 只接受 pdf

当规则匹配得分足够高（如 >0.8）直接路由；否则再调用 LLM 做“候选集合内分类”。

LLM 分类的关键不是让它“自由发挥”，而是把它限制在候选集合里：

- 候选集合来自“用户选择的分类（股息收入）”+ 文件后缀 + 规则初筛结果
- 只给 LLM 少量“证据片段”（headers + 少量行 + 关键词），避免把整份流水塞进分类 Prompt

LLM 分类输出建议包含置信与原因，方便后续灰度/回溯：

```json
{
  "docType": "BANK_STATEMENT",
  "variant": "BANK_ABC_V2",
  "confidence": 0.86,
  "reasons": ["出现'交易日期/摘要/余额'典型银行字段", "金额列呈现为收入/支出拆分"],
  "needHumanReview": false
}
```

### 2.3 永远要有“UNKNOWN → 通用抽取器”兜底
不要强迫模型在“错的候选”里二选一。  
当 `confidence` 低、或与规则冲突时，直接走通用抽取器（见第 3 节的 IR 思路），并把任务标记为“需复核”。

---

## 3. 统一中间表示（IR）：降低“银行差异”对 Prompt 的影响
建议将各种输入先统一为交易流水的最小字段集（类似你项目里股票解析文件的“最小字段集”思想）：

### 3.1 TransactionLine（建议字段）
```json
{
  "rowId": "p3_t2_r15",          // 可追溯：页/表/行，或 excel 的 sheet/row
  "tradeDate": "2025-01-18",
  "postDate": "2025-01-18",      // 可选：记账日期/入账日期
  "summary": "股息入账",
  "counterparty": "XXXX公司",
  "direction": "CREDIT",         // CREDIT/DEBIT
  "amount": 1234.56,
  "currency": "CNY",
  "balance": 8888.88,            // 可选
  "rawText": "...",              // 可选：拼接摘要/备注，便于回溯
  "evidence": {                  // 可选：原始证据
    "page": 3,
    "tableIndex": 2,
    "cells": ["2025/01/18","股息入账","1,234.56","8,888.88"]
  }
}
```

你会发现：一旦把“银行版式差异”压缩进这个 IR，后续的“股息识别与结构化” Prompt 基本就能统一了。

### 3.2 IR 怎么来：三条路（按稳定性排序）
1) **Excel/csv：规则列映射（优先）**
   - 先用表头同义词字典做列映射：日期列/摘要列/金额列/余额列
   - 实在映射不了再让 LLM 做“列名 → 字段”映射（只用 headers，不用整表）
2) **PDF（可提取到表格结构）：表格 → IR**
   - MinerU 若能给 table 结构，优先按表格解析（类似 `DocumentServer.convertTableHtmlToJson` 的思路）
3) **扫描件/图片：OCR → 行/表 → IR**
   - 这里建议你把 `ParseFileServer` 的 `is_ocr=false` 做成可配置；当检测到“文本极少/疑似扫描”时开启 OCR（并记录来源）

---

## 4. 股息识别：从“交易流水”里找“股息交易”
有了 IR 后，“股息提取”就变成两步：

### 4.1 交易级筛选（规则优先）
规则筛选用于“召回”，尽量别漏：

- 关键词：`股息|红利|分红|派息|Dividend|Distribution|Interest(视业务而定)`
- 备注/对方信息中出现证券/基金关键词（可选）
- 金额方向：多为 CREDIT（但也可能有税费扣款、冲正）

输出候选集合 `candidateLines[]`（数量一般远小于全量流水）。

### 4.2 候选行结构化（LLM/规则混合）
对候选行再做“股息语义解析”，提取字段（建议输出“净额/税额/毛额”并允许缺失）：

```json
{
  "dividendDate": "2025-01-18",
  "payer": "XXXX公司",
  "grossAmount": 1500.00,
  "withholdingTax": 265.44,
  "netAmount": 1234.56,
  "currency": "CNY",
  "evidenceRowIds": ["p3_t2_r15"],
  "confidence": 0.78,
  "warnings": ["未识别到税额字段，gross/withholdingTax 可能缺失"]
}
```

注意：**LLM 的输入只需要候选行及其少量邻近上下文**（如同表的前后 2 行），不需要整份流水。

---

## 5. Prompt 体系怎么设计才“能长久维护”
你现在的做法更像“每份文档一个 Prompt”。建议演进到“Prompt 模板 + 配置驱动”：

### 5.1 统一的 Schema + 统一的错误/警告输出
你项目里 `AIDocumentParseServer.generateParsePrompt()` 已经在系统 Prompt 里定义了 `warnings/records/globalParam` 的 JSON 结构（见 `src/main/java/com/rcszh/tax/server/AIDocumentParseServer.java`）。  
建议把“股息解析”的 records schema 固化，并引入：

- `evidenceRowIds`：可追溯
- `confidence / needHumanReview`：可运营
- `normalizationNotes`：记录模型做了哪些推断

### 5.2 路由 Prompt（分类）与抽取 Prompt（结构化）分离
- 分类 Prompt：短、小、只吃 features/headers/snippets
- 抽取 Prompt：只吃 IR（或候选行 IR），并严格输出 schema

### 5.3 每个类型只保留“差异化补丁”
例如 `BANK_ABC_V2` 只需要补充：

- 列名同义词
- 金额方向规则（收入/支出/发生额/借贷）
- 典型摘要关键词（如“派息入账”“红利税”“股息税”）

不要为每个银行复制一整份大 Prompt；而是在通用模板上叠加小 patch。

---

## 6. 质量评估与人工兜底（强烈建议一开始就做）
自动化率想提升，必须能“知道自己什么时候不确定”。

建议至少做这些校验：

- **字段完整性**：日期/金额/方向缺失 → warning
- **金额方向一致性**：同一银行同一列既出现正负又出现借贷 → 低置信
- **余额连续性（可选）**：若有余额列，可抽样检查 `prevBalance + credit - debit ≈ nextBalance`
- **去重**：同一日期+金额+摘要重复多次，且 rowId 不同 → 可能重复页/重复导出
- **审计日志**：保存路由结果（docType/variant/confidence/reasons）与 LLM 响应摘要

人工兜底建议表现为：

- 低置信的结果自动进入“待复核”列表
- 前端可以基于 `evidenceRowIds` 高亮原始行，减少人工成本

---

## 7. 可行性与落地节奏（建议分三步灰度）
### 7.1 可行性判断
- 方案可行：因为你现有系统已经有“解析 → LLM → 结构化”的骨架，差的只是“路由层 + IR 层”。
- 成本可控：分类只用少量上下文；抽取只针对候选行；整体 token 会比“整份流水全喂 LLM”更低。
- 维护成本更低：新增一个银行/一种格式，大概率只需要补充 `matchRule + header 同义词 + 少量 few-shot`。

### 7.2 灰度路径
1) 先做 IR（把 PDF/Excel 都吐出 `TransactionLine[]`），不改变现有股息 Prompt，仅用于观测差异
2) 再加路由：规则优先 + LLM fallback，但仍允许用户手动选择“我上传的是哪种”
3) 最后把股息抽取变成“IR → 股息记录”，并加质量评估/人工兜底闭环

---

## 8. 结合本项目的代码实现方案（接口与挂载点）
下面给的是“能在你现有结构里渐进接入”的实现草图（不强依赖你动态表的具体查询能力，便于先做 MVP）。

### 8.1 建议新增的模块划分
建议包名（示例）：

- `com.rcszh.tax.route`：文档路由（类型识别）
- `com.rcszh.tax.ir`：中间表示（TransactionLine 等）
- `com.rcszh.tax.extractor`：抽取器（银行流水抽取器/股息抽取器/通用抽取器）
- `com.rcszh.tax.quality`：质量评估（规则校验、置信度）

### 8.2 在哪里挂载（最少改动）
目前解析入口在：

- PDF：`src/main/java/com/rcszh/tax/parser/PDFParser.java`
- Excel：`src/main/java/com/rcszh/tax/parser/ExcelParser.java`

推荐接入点：在 `doParse()` 里拿到 `parseResults/results` 后、获取 prompt 前加入：

1) `IRBuilder`：把 `parseResults` / `ExcelParseResult` 转成 `TransactionLine[]`
2) `DocumentRouter`：根据 IR/features 选择 `documentId`（或直接选择 extractor）
3) 执行对应 extractor（LLM/规则），输出统一 records

### 8.3 核心接口（建议）
```java
public interface DocumentRouter {
    RouteResult route(RouteContext ctx);
}

public record RouteResult(
        String docType,
        String variant,
        String documentId,     // 对应 tax_question_document 的 id（如果仍复用现有 Prompt 配置体系）
        double confidence,
        boolean needHumanReview,
        List<String> reasons
) {}

public interface IRBuilder<I> {
    List<TransactionLine> build(I input);
}

public interface DividendExtractor {
    DividendExtractResult extract(List<TransactionLine> lines, ExtractContext ctx);
}
```

`TransactionLine` / `DividendExtractResult` 建议是你们自己的 DTO（并带 `rowId/evidence`）。

### 8.4 配置怎么存（建议）
沿用你们动态表的思路（`tax_question_document`），建议新增/扩展字段：

- `type`：例如 `DIVIDEND_INCOME` / `BANK_STATEMENT`（你们已有 `DocumentServer.TYPE` 常量）
- `match_rule`：JSON（关键词/表头/排除词/文件类型）
- `variant`：如 `BANK_ABC_V2`
- `prompt_patch`：只存差异化补丁（可选）
- `examples`：few-shot 示例（可选，注意脱敏）

并在任务项表 `tax_user_document_task_item` 增加（或复用现有扩展字段）：

- `resolved_document_id`
- `route_confidence`
- `route_reason`
- `need_human_review`

### 8.5 路由的“规则 JSON”示例
```json
{
  "fileTypes": ["pdf","jpg","png"],
  "mustKeywords": ["交易日期", "余额"],
  "anyKeywords": ["摘要", "对方户名", "交易渠道"],
  "forbiddenKeywords": ["持仓", "成交", "证券代码", "ISIN"],
  "headerSynonyms": {
    "tradeDate": ["交易日期","交易时间","日期","Date","Transaction Date"],
    "summary": ["摘要","用途","交易摘要","Narrative","Description"],
    "amount": ["发生额","金额","收入","支出","Amount","Credit","Debit"],
    "balance": ["余额","Balance"]
  }
}
```

### 8.6 LLM 分类 Prompt（示例）
只给 features/snippets，并限制输出：

```text
你是一个文档类型分类器。只能从候选类型中选择最匹配的一类。

候选类型：
1) BANK_STATEMENT
2) DIVIDEND_SUMMARY_EXCEL
3) BROKER_STATEMENT
4) OTHER_UNKNOWN

输入特征（JSON）：
{{featuresJson}}

请输出 JSON：
{
  "docType": "...",
  "variant": "...",
  "confidence": 0-1,
  "reasons": ["..."],
  "needHumanReview": true/false
}
```

### 8.7 股息抽取 Prompt（示例）
输入只用候选行 IR：

```text
请从交易流水中找出“股息/红利/派息”相关交易，并输出 records。

规则：
- 尽量提取：股息日期、付款方、币种、净额(net)、税(withholdingTax)、毛额(gross)。
- 若无法确定毛额或税额，允许为空，但要写 warnings，并给出 confidence。
- 必须返回 evidenceRowIds，用于回溯原始行。

交易流水（JSON）：
{{candidateTransactionLinesJson}}
```

---

## 9. 你这个“先识别类型再选 Prompt”方案的扩展点（建议做成产品能力）
在你现有思路上，可以自然拓展出一些很值钱的能力：

- **“自动识别失败 → 引导用户选择”**：把候选类型 + 置信度展示给用户，用户一键确认即可形成标注数据
- **持续学习**：把“用户确认的类型 + 抽取结果修正”沉淀为新 matchRule / few-shot
- **跨银行统一能力**：一旦 IR 稳定，你后面的业务抽取（股息/利息/工资/房租）都能复用同一套流水线

---

## 10. 工程注意事项（基于现有代码的风险点）
基于你当前代码，有几个点建议尽早处理（否则会影响长期维护）：

- **密钥与 token 不要硬编码**：`ParseFileServer` / `DeepSeekAi` 里都有硬编码 key/token，建议迁移到配置中心/环境变量，并做脱敏日志。
- **OCR 开关需要可配置**：`ParseFileServer.sendParseRequest()` 当前 `is_ocr=false`，遇到扫描件会直接失败或质量很差；建议根据“文本密度”自动开启。
- **控制 LLM 输入大小**：先 IR、再候选行抽取，会显著降低 token；否则并发切片（`AiManage.groupArrayByConfig`）也会很贵。
- **保留证据链**：没有 `rowId/evidence` 的结构化结果很难审计，也很难做人工复核提效。

