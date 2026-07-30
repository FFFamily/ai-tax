INSERT INTO tax_document (id, name, type, variant, filter_type, page_type, page_step, match_rule, prompt, global_prompt, error_record)
SELECT 'DOC_DIVIDEND_BANK_V1',
       '银行流水股息收入模板',
       'DIVIDEND',
       'BANK_STATEMENT_DIVIDEND_V1',
       'all',
       'data',
       20,
       '{
         "fileTypes":["pdf","excel"],
         "mustKeywords":["交易日期"],
         "anyKeywords":["股息","红利","派息","余额","摘要","存入"],
         "forbiddenKeywords":["持仓","成交","证券代码","ISIN"],
         "headerSynonyms":{
           "tradeDate":["交易日期","日期","Transaction Date","Date"],
           "summary":["摘要","交易摘要","Description","Narrative"],
           "deposit":["存入","收入","Credit","Deposit"],
           "balance":["余额","Balance"]
         }
       }',
       '请从银行流水或对账单材料中提取与股息、红利、派息相关的结构化记录。若能识别税费，请将税费保存在 withholdingTax 相关信息中；若能识别净额与毛额，请尽量同时输出。',
       '关注股息日期、付款方、币种、净额、税额、毛额以及可追溯证据。',
       '{
         "oldRecord": {},
         "reason": ""
       }'
WHERE NOT EXISTS (SELECT 1 FROM tax_document WHERE id = 'DOC_DIVIDEND_BANK_V1');

INSERT INTO tax_document (id, name, type, variant, filter_type, page_type, page_step, match_rule, prompt, global_prompt, error_record)
SELECT 'DOC_BANK_STATEMENT_GENERIC_V1',
       '银行流水通用模板',
       'BANK_STATEMENT',
       'BANK_STATEMENT_GENERIC_V1',
       'all',
       'data',
       20,
       '{
         "fileTypes":["pdf","excel"],
         "mustKeywords":["交易日期"],
         "anyKeywords":["余额","摘要","存入","支出","账户"],
         "forbiddenKeywords":["持仓","成交","证券代码","ISIN"],
         "headerSynonyms":{
           "tradeDate":["交易日期","日期","Transaction Date","Date"],
           "summary":["摘要","交易摘要","Description","Narrative"],
           "amount":["金额","发生额","Amount"],
           "balance":["余额","Balance"]
         }
       }',
       '请从银行流水中提取标准交易记录，输出账户、日期、摘要、金额、余额等字段。',
       '关注通用流水字段，并保留原始上下文。',
       '{
         "oldRecord": {},
         "reason": ""
       }'
WHERE NOT EXISTS (SELECT 1 FROM tax_document WHERE id = 'DOC_BANK_STATEMENT_GENERIC_V1');

INSERT INTO tax_document (id, name, type, variant, filter_type, page_type, page_step, match_rule, prompt, global_prompt, error_record)
SELECT 'DOC_BROKER_STATEMENT_GENERIC_V1',
       '券商对账单模板',
       'BROKER_STATEMENT',
       'BROKER_STATEMENT_GENERIC_V1',
       'all',
       'data',
       20,
       '{
         "fileTypes":["pdf","excel"],
         "anyKeywords":["股息","红利","Dividend","Withholding Tax","证券"],
         "forbiddenKeywords":["工资","房租"],
         "headerSynonyms":{
           "tradeDate":["Trade Date","Pay Date","日期"],
           "summary":["Description","Narrative","摘要"],
           "amount":["Net Amount","Gross Amount","金额"],
           "tax":["Withholding Tax","税额","预扣税"]
         }
       }',
       '请从券商股息或对账单中提取股息收入、预扣税和相关日期、付款方、币种等信息。',
       '重点关注 Dividend、Withholding Tax、Net Amount、Gross Amount 等字段。',
       '{
         "oldRecord": {},
         "reason": ""
       }'
WHERE NOT EXISTS (SELECT 1 FROM tax_document WHERE id = 'DOC_BROKER_STATEMENT_GENERIC_V1');

INSERT INTO tax_document_field_mapping (id, document_id, field_label, field_code, field_desc, sort_num)
SELECT 'MAP_DIV_DATE', 'DOC_DIVIDEND_BANK_V1', '股息日期', 'dividendDate', '股息/红利发生日期，建议 yyyy-MM-dd', 1
WHERE NOT EXISTS (SELECT 1 FROM tax_document_field_mapping WHERE id = 'MAP_DIV_DATE');

INSERT INTO tax_document_field_mapping (id, document_id, field_label, field_code, field_desc, sort_num)
SELECT 'MAP_DIV_PAYER', 'DOC_DIVIDEND_BANK_V1', '付款方', 'payer', '支付股息/红利的付款方、公司或机构名称', 2
WHERE NOT EXISTS (SELECT 1 FROM tax_document_field_mapping WHERE id = 'MAP_DIV_PAYER');

INSERT INTO tax_document_field_mapping (id, document_id, field_label, field_code, field_desc, sort_num)
SELECT 'MAP_DIV_CCY', 'DOC_DIVIDEND_BANK_V1', '币种', 'currency', '股息记录对应的币种', 3
WHERE NOT EXISTS (SELECT 1 FROM tax_document_field_mapping WHERE id = 'MAP_DIV_CCY');

INSERT INTO tax_document_field_mapping (id, document_id, field_label, field_code, field_desc, sort_num)
SELECT 'MAP_DIV_NET', 'DOC_DIVIDEND_BANK_V1', '净额', 'netAmount', '股息净额，如无法确定可为空', 4
WHERE NOT EXISTS (SELECT 1 FROM tax_document_field_mapping WHERE id = 'MAP_DIV_NET');

INSERT INTO tax_document_field_mapping (id, document_id, field_label, field_code, field_desc, sort_num)
SELECT 'MAP_DIV_TAX', 'DOC_DIVIDEND_BANK_V1', '预扣税', 'withholdingTax', '股息对应的预扣税或红利税，如无法确定可为空', 5
WHERE NOT EXISTS (SELECT 1 FROM tax_document_field_mapping WHERE id = 'MAP_DIV_TAX');

INSERT INTO tax_document_field_mapping (id, document_id, field_label, field_code, field_desc, sort_num)
SELECT 'MAP_DIV_GROSS', 'DOC_DIVIDEND_BANK_V1', '毛额', 'grossAmount', '股息毛额，如无法确定可为空', 6
WHERE NOT EXISTS (SELECT 1 FROM tax_document_field_mapping WHERE id = 'MAP_DIV_GROSS');

INSERT INTO tax_document_field_mapping (id, document_id, field_label, field_code, field_desc, sort_num)
SELECT 'MAP_DIV_EVIDENCE', 'DOC_DIVIDEND_BANK_V1', '证据行', 'evidenceRowIds', '用于追溯原始材料的行标识列表', 7
WHERE NOT EXISTS (SELECT 1 FROM tax_document_field_mapping WHERE id = 'MAP_DIV_EVIDENCE');
