package com.rcszh.tax.server;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.dto.HtmlTable;
import com.rcszh.tax.dto.MinerUFileParseResult;
import com.rcszh.tax.entity.DocumentFieldMapping;
import com.rcszh.tax.entity.MaterialDocument;
import com.rcszh.tax.entity.document.DocumentConfig;
import com.rcszh.tax.mapper.DocumentConfigMapper;
import com.rcszh.tax.mapper.DocumentFieldMappingMapper;
import com.rcszh.tax.mapper.MaterialDocumentMapper;
import jakarta.annotation.Resource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DocumentServer {
    private static final Logger logger = LoggerFactory.getLogger(DocumentServer.class);

    public static final String FILTER_TYPE = "filterType";
    public static final String PAGE_TYPE = "pageType";
    public static final String PAGE_STEP = "pageStep";
    public static final String PROMPT = "prompt";
    public static final String TYPE = "type";
    public static final String VARIANT = "variant";
    public static final String MATCH_RULE = "matchRule";
    public static final String GLOBAL_PROMPT = "globalPrompt";
    public static final String ERROR_RECORD = "errorRecord";
    public static final String ID_KEY = "id";

    @Resource
    private MaterialDocumentMapper materialDocumentMapper;
    @Resource
    private DocumentConfigMapper documentConfigMapper;
    @Resource
    private DocumentFieldMappingMapper documentFieldMappingMapper;

    public static class Item {
        public static final String TITLE_FILTER_RULE = "titleFilter";
        public static final String TABLE_HEAD_CHECK_RULE = "tableHeadCheckRule";
    }

    public static class MAPPING {
        public static final String FIELD_LABEL = "fieldLabel";
        public static final String FIELD_CODE = "fieldCode";
        public static final String FIELD_DESC = "fieldDesc";
    }

    public static final Map<String, Object> ID = new HashMap<>();
    public static final Map<String, Object> CONFIDENCE = new HashMap<>();

    static {
        ID.put(MAPPING.FIELD_CODE, "record_id");
        ID.put(MAPPING.FIELD_DESC, "生成能确认当前记录唯一性的ID，格式为：当前生成时间(yyyyMMdd)+随机6位数字或者大写字母");
        CONFIDENCE.put(MAPPING.FIELD_CODE, "confidence");
        CONFIDENCE.put(MAPPING.FIELD_DESC, "对于当前数据的置信度(0-1 保留一位小数)");
    }

    public static String toMappingJsonStr(List<Map<String, Object>> mapping) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < mapping.size(); i++) {
            Map<String, Object> map = mapping.get(i);
            appendStr(sb, map);
            sb.append(",");
        }
        appendStr(sb, ID);
        sb.append(",");
        appendStr(sb, CONFIDENCE);
        sb.append("}");
        return sb.toString();
    }

    public static void appendStr(StringBuilder sb, Map<String, Object> map) {
        String code = (String) map.get(MAPPING.FIELD_CODE);
        String label = (String) map.get(MAPPING.FIELD_LABEL);
        String desc = (String) map.get(MAPPING.FIELD_DESC);
        sb.append(code);
        sb.append(":");
        sb.append(StrUtil.isNotBlank(desc) ? desc : label);
    }

    public Map<String, Object> getDocument(String id) {
        MaterialDocument document = materialDocumentMapper.selectById(id);
        if (document == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put(ID_KEY, document.getId());
        result.put("name", document.getName());
        result.put(TYPE, document.getType());
        result.put(VARIANT, document.getVariant());
        result.put(FILTER_TYPE, document.getFilterType());
        result.put(PAGE_TYPE, document.getPageType());
        result.put(PAGE_STEP, document.getPageStep());
        result.put(MATCH_RULE, document.getMatchRule());
        result.put(PROMPT, document.getPrompt());
        result.put(GLOBAL_PROMPT, document.getGlobalPrompt());
        result.put(ERROR_RECORD, document.getErrorRecord());
        return result;
    }

    public List<Map<String, Object>> listRouteCandidates(String requestedType) {
        LambdaQueryWrapper<MaterialDocument> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(requestedType)) {
            wrapper.eq(MaterialDocument::getType, requestedType);
        }
        return materialDocumentMapper.selectList(wrapper).stream()
                .map(this::toRouteCandidate)
                .toList();
    }

    public List<Map<String, Object>> getRules(String documentId) {
        List<DocumentConfig> rules = documentConfigMapper.selectList(new LambdaQueryWrapper<DocumentConfig>()
                .eq(DocumentConfig::getDocumentId, documentId)
                .orderByAsc(DocumentConfig::getSortNum));
        List<Map<String, Object>> result = new ArrayList<>();
        for (DocumentConfig rule : rules) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", rule.getId());
            item.put(Item.TITLE_FILTER_RULE, rule.getTitleFilter());
            item.put(Item.TABLE_HEAD_CHECK_RULE, rule.getTableHeadCheckRule());
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getMapping(String documentId) {
        List<DocumentFieldMapping> mappings = documentFieldMappingMapper.selectList(new LambdaQueryWrapper<DocumentFieldMapping>()
                .eq(DocumentFieldMapping::getDocumentId, documentId)
                .orderByAsc(DocumentFieldMapping::getSortNum));
        List<Map<String, Object>> result = new ArrayList<>();
        for (DocumentFieldMapping mapping : mappings) {
            Map<String, Object> item = new HashMap<>();
            item.put(MAPPING.FIELD_LABEL, mapping.getFieldLabel());
            item.put(MAPPING.FIELD_CODE, mapping.getFieldCode());
            item.put(MAPPING.FIELD_DESC, mapping.getFieldDesc());
            result.add(item);
        }
        return result;
    }

    public static List<HtmlTable> doFilterTitle(List<HtmlTable> htmlTables, Map<String, Object> ruleConfig) {
        Object rule = ruleConfig.get(Item.TITLE_FILTER_RULE);
        if (rule == null || StrUtil.isBlank((String) rule)) {
            logger.info("未配置标题过滤规则，不进行过滤");
            return htmlTables;
        }
        List<HtmlTable> result = htmlTables;
        String ruleInfo = (String) rule;
        logger.info("当前标题过滤：{}", ruleInfo);
        String[] filterTitles = ruleInfo.split(",");
        for (String filterTitleItem : filterTitles) {
            result = result.stream().filter(i -> i.getTitle() != null && i.getTitle().contains(filterTitleItem)).toList();
        }
        return result;
    }

    private Map<String, Object> toRouteCandidate(MaterialDocument document) {
        Map<String, Object> result = new HashMap<>();
        result.put(ID_KEY, document.getId());
        result.put("name", document.getName());
        result.put(TYPE, document.getType());
        result.put(VARIANT, document.getVariant());
        result.put(MATCH_RULE, document.getMatchRule());
        return result;
    }

    public List<HtmlTable> convertTableHtmlToJson(List<MinerUFileParseResult> parseResults, Map<String, Object> ruleConfig) {
        List<HtmlTable> resultList = new ArrayList<>();
        for (MinerUFileParseResult parseResult : parseResults) {
            String tableBody = parseResult.getTable_body();
            HtmlTable htmlTable = convertTableHtmlToJson(tableBody);
            htmlTable.setTitle(parseResult.getTable_caption());
            String title = htmlTable.getTitle().replaceAll("\\[", "").replaceAll("]", "").trim();
            int start = title.indexOf("\"");
            int end = title.lastIndexOf("\"");
            if (start >= 0 && end > 0) {
                title = title.substring(start + 1, end).trim();
            }
            htmlTable.setTitle(title);
            htmlTable.setPageIdx(parseResult.getPage_idx());
            resultList.add(htmlTable);
        }
        return resultList;
    }

    public static void checkTableFormat(List<HtmlTable> htmlTables, Map<String, Object> ruleConfig) {
        Object rule = ruleConfig.get(Item.TABLE_HEAD_CHECK_RULE);
        if (rule == null) {
            for (HtmlTable htmlTable : htmlTables) {
                htmlTable.setIsSuccess(true);
            }
            return;
        }
        String ruleInfo = (String) rule;
        List<String> headList = Arrays.asList(ruleInfo.split(","));
        for (HtmlTable htmlTable : htmlTables) {
            if (htmlTable.getHead().size() != headList.size()) {
                htmlTable.setIsSuccess(false);
                htmlTable.setFailReason("表格列数不一致");
                continue;
            }
            for (String head : headList) {
                if (!htmlTable.getHead().contains(head)) {
                    htmlTable.setIsSuccess(false);
                    if (StrUtil.isBlank(htmlTable.getFailReason())) {
                        htmlTable.setFailReason("该表格未能包含表头：" + head);
                    } else {
                        htmlTable.setFailReason(htmlTable.getFailReason() + "、" + head);
                    }
                }
            }
        }
    }

    public HtmlTable convertTableHtmlToJson(String html) {
        Document doc = Jsoup.parse(html);
        Element table = doc.selectFirst("table");
        if (table == null) {
            throw new IllegalArgumentException("HTML 中没有找到 <table>");
        }
        HtmlTable htmlTable = new HtmlTable();
        Elements rows = table.select("tr");
        Element head = rows.getFirst();
        Elements headCells = head.select("td");
        List<String> headList = new ArrayList<>();
        for (Element cell : headCells) {
            headList.add(cell.text().trim());
        }
        htmlTable.setHead(headList);
        for (int i = 1; i < rows.size(); i++) {
            htmlTable.getItems().add(new ArrayList<>());
        }
        for (int i = 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cells = row.select("td");
            List<Object> item = htmlTable.getItems().get(i - 1);
            for (Element cell : cells) {
                String value = cell.text().trim();
                String rowspan = cell.attributes().get("rowspan");
                if (!rowspan.isEmpty()) {
                    int rowspanInt = Integer.parseInt(rowspan.replace("\\\"", "")) - 1;
                    for (int j = 0; j < rowspanInt; j++) {
                        htmlTable.getItems().get(i + j).add(value);
                    }
                }
                item.add(value);
            }
        }
        return htmlTable;
    }
}
