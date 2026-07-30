# 路由规则与模板示例

本文档用于说明 `tax_document.match_rule` 的推荐配置方式，并提供第一批可落地的模板示例。

## 1. 配置目标

当任务未显式传入 `documentId` 时，系统会基于：

1. 文件类型
2. 预处理后的表头
3. 文本关键词
4. 候选机构名

去匹配最合适的模板。

## 2. match_rule JSON 结构

推荐结构如下：

```json
{
  "fileTypes": ["pdf", "excel"],
  "mustKeywords": ["交易日期"],
  "anyKeywords": ["余额", "摘要", "存入", "支出"],
  "forbiddenKeywords": ["持仓", "成交", "ISIN", "证券代码"],
  "headerSynonyms": {
    "tradeDate": ["交易日期", "日期", "Transaction Date", "Date"],
    "summary": ["摘要", "交易摘要", "Description", "Narrative"],
    "amount": ["金额", "发生额", "Amount"],
    "balance": ["余额", "Balance"]
  }
}
```

## 3. 示例模板

### 3.1 银行流水股息收入模板

- `type`: `DIVIDEND`
- `variant`: `BANK_STATEMENT_DIVIDEND_V1`

```json
{
  "fileTypes": ["pdf", "excel"],
  "mustKeywords": ["交易日期"],
  "anyKeywords": ["股息", "红利", "派息", "余额", "摘要", "存入"],
  "forbiddenKeywords": ["持仓", "成交", "证券代码", "ISIN"],
  "headerSynonyms": {
    "tradeDate": ["交易日期", "日期", "Transaction Date", "Date"],
    "summary": ["摘要", "交易摘要", "Description", "Narrative"],
    "deposit": ["存入", "收入", "Credit", "Deposit"],
    "balance": ["余额", "Balance"]
  }
}
```

### 3.2 银行流水通用模板

- `type`: `BANK_STATEMENT`
- `variant`: `BANK_STATEMENT_GENERIC_V1`

```json
{
  "fileTypes": ["pdf", "excel"],
  "mustKeywords": ["交易日期"],
  "anyKeywords": ["余额", "摘要", "存入", "支出", "账户"],
  "forbiddenKeywords": ["持仓", "成交", "证券代码", "ISIN"],
  "headerSynonyms": {
    "tradeDate": ["交易日期", "日期", "Transaction Date", "Date"],
    "summary": ["摘要", "交易摘要", "Description", "Narrative"],
    "amount": ["金额", "发生额", "Amount"],
    "balance": ["余额", "Balance"]
  }
}
```

### 3.3 券商对账单模板

- `type`: `BROKER_STATEMENT`
- `variant`: `BROKER_STATEMENT_GENERIC_V1`

```json
{
  "fileTypes": ["pdf", "excel"],
  "anyKeywords": ["股息", "红利", "Dividend", "Withholding Tax", "证券"],
  "forbiddenKeywords": ["工资", "房租"],
  "headerSynonyms": {
    "tradeDate": ["Trade Date", "Pay Date", "日期"],
    "summary": ["Description", "Narrative", "摘要"],
    "amount": ["Net Amount", "Gross Amount", "金额"],
    "tax": ["Withholding Tax", "税额", "预扣税"]
  }
}
```

## 4. 建议初始化方式

推荐至少先准备以下几类模板：

1. `DIVIDEND / BANK_STATEMENT_DIVIDEND_V1`
2. `BANK_STATEMENT / BANK_STATEMENT_GENERIC_V1`
3. `BROKER_STATEMENT / BROKER_STATEMENT_GENERIC_V1`

在没有完整运营数据前，先使用这些模板作为默认种子配置，可以快速启动路由能力。
