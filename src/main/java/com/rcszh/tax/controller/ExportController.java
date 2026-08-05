package com.rcszh.tax.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.rcszh.tax.common.ApiResponse;
import com.rcszh.tax.dto.DetailRecord;
import com.rcszh.tax.dto.DetailRecordImport;
import com.rcszh.tax.dto.export.ExportRequestDTO;
import com.rcszh.tax.entity.task.DocumentTask;
import com.rcszh.tax.entity.task.DocumentTaskItem;
import com.rcszh.tax.server.DocumentTaskServer;
import com.rcszh.tax.service.StorageService;
import com.rcszh.tax.util.BaseExportUtil;
import com.rcszh.tax.util.export.SheetConfig;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/exports")
public class ExportController {
    @Resource
    private DocumentTaskServer documentTaskServer;
    @Resource
    private StorageService storageService;

    private String safeToString(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private String ensureDownloadPath(String fileName) {
        try {
            return storageService.ensureParent(fileName).toString();
        } catch (IOException e) {
            throw new RuntimeException("创建导出目录失败", e);
        }
    }

    private String extractFilename(String suffix) {
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return datePath + "/" + UUID.randomUUID() + suffix + ".xlsx";
    }

    @GetMapping("/records/{taskId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> exportRecords(@PathVariable Long taskId) {
        DocumentTask task = documentTaskServer.getTaskAndItemById(taskId);
        if (task == null) {
            return ApiResponse.error("任务不存在");
        }
        for (DocumentTaskItem taskItem : task.getItems()) {
            String jsonData = taskItem.getChangeResult();
            if (StringUtils.isEmpty(jsonData)) {
                continue;
            }
            try {
                List<DetailRecord> records = loadRecords(jsonData);
                if (records == null || records.isEmpty()) {
                    return ApiResponse.error("没有可导出的数据");
                }
                String storedFileName = extractFilename("_records");
                String absoluteFilePath = ensureDownloadPath(storedFileName);
                List<List<String>> head = buildRecordHead();
                Map<String, List<DetailRecord>> accountGroupMap = new LinkedHashMap<>();
                for (DetailRecord record : records) {
                    String account = StrUtil.blankToDefault(record.getAccount(), "未知账户");
                    accountGroupMap.computeIfAbsent(account, k -> new ArrayList<>()).add(record);
                }
                List<List<Object>> summaryData = buildRecordData(records);
                try (ExcelWriter excelWriter = EasyExcel.write(absoluteFilePath).build()) {
                    excelWriter.write(buildNoticeData(), EasyExcel.writerSheet(0, "注意事项").build());
                    excelWriter.write(summaryData, EasyExcel.writerSheet(1, "汇总").head(head).build());
                    int sheetIndex = 2;
                    for (Map.Entry<String, List<DetailRecord>> entry : accountGroupMap.entrySet()) {
                        String sheetName = sanitizeSheetName(entry.getKey());
                        if (sheetName.length() > 31) {
                            sheetName = sheetName.substring(0, 31);
                        }
                        excelWriter.write(buildRecordData(entry.getValue()),
                                EasyExcel.writerSheet(sheetIndex, sheetName).head(head).build());
                        sheetIndex++;
                    }
                }
                Map<String, Object> result = new HashMap<>();
                result.put("url", storageService.buildDownloadUrl(storedFileName));
                result.put("fileName", storedFileName);
                return ApiResponse.success(result);
            } catch (Exception e) {
                return ApiResponse.error("导出Excel失败：" + e.getMessage());
            }
        }
        return ApiResponse.error("没有可导出的数据");
    }

    private List<DetailRecord> loadRecords(String jsonData) {
        JSONObject root = JSONUtil.parseObj(jsonData);
        Object recordValue = root.get("records");
        if (!(recordValue instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<DetailRecord> records = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> recordMap = (Map<String, Object>) map;
            if (recordMap.containsKey("payer") || recordMap.containsKey("dividendDate")) {
                records.add(toDetailRecord(recordMap));
            } else {
                records.add(JSONUtil.toBean(JSONUtil.parseObj(recordMap), DetailRecord.class));
            }
        }
        return records;
    }

    private DetailRecord toDetailRecord(Map<String, Object> recordMap) {
        DetailRecord record = new DetailRecord();
        record.setAccount(safeToString(recordMap.get("payer")));
        record.setAccountType("DIVIDEND");
        record.setCurrency(safeToString(recordMap.get("currency")));
        record.setDate(safeToString(recordMap.get("dividendDate")));
        record.setSummary(safeToString(recordMap.get("summary")));
        Object netAmount = recordMap.get("netAmount");
        if (netAmount != null) {
            record.setDeposit(netAmount.toString());
        }
        Object withholdingTax = recordMap.get("withholdingTax");
        if (withholdingTax != null) {
            record.setWithdrawal(withholdingTax.toString());
        }
        Object grossAmount = recordMap.get("grossAmount");
        if (grossAmount != null) {
            record.setBalance(grossAmount.toString());
        }
        record.setCategory(safeToString(recordMap.get("category")));
        if (StrUtil.isNotBlank(record.getDate())) {
            try {
                record.setYear(Integer.parseInt(record.getDate().substring(0, 4)));
            } catch (Exception ignored) {
            }
        }
        return record;
    }

    private List<List<String>> buildRecordHead() {
        List<List<String>> head = new ArrayList<>();
        head.add(Collections.singletonList("账户"));
        head.add(Collections.singletonList("账户类型"));
        head.add(Collections.singletonList("年份"));
        head.add(Collections.singletonList("币种"));
        head.add(Collections.singletonList("日期"));
        head.add(Collections.singletonList("交易摘要"));
        head.add(Collections.singletonList("存入"));
        head.add(Collections.singletonList("取出"));
        head.add(Collections.singletonList("余额"));
        head.add(Collections.singletonList("类别"));
        return head;
    }

    private List<List<Object>> buildRecordData(List<DetailRecord> records) {
        List<List<Object>> data = new ArrayList<>();
        for (DetailRecord record : records) {
            List<Object> row = new ArrayList<>();
            row.add(safeToString(record.getAccount()));
            row.add(safeToString(record.getAccountType()));
            row.add(record.getYear() == null ? "" : record.getYear());
            row.add(safeToString(record.getCurrency()));
            row.add(safeToString(record.getDate()));
            row.add(safeToString(record.getSummary()));
            row.add(safeToString(record.getDeposit()));
            row.add(safeToString(record.getWithdrawal()));
            row.add(safeToString(record.getBalance()));
            row.add(safeToString(record.getCategory()));
            data.add(row);
        }
        return data;
    }

    private String sanitizeSheetName(String name) {
        if (StrUtil.isBlank(name)) {
            return "Sheet";
        }
        return name.replaceAll("[\\\\/:?*\\[\\]]", "_");
    }

    private List<List<Object>> buildNoticeData() {
        List<List<Object>> noticeData = new ArrayList<>();
        noticeData.add(Collections.singletonList("注意事项"));
        noticeData.add(Collections.singletonList(""));
        noticeData.add(Collections.singletonList("1. 请勿新增或删减列，保持列结构不变。"));
        noticeData.add(Collections.singletonList("2. 可以对数据进行编辑、新增、删除等操作。"));
        return noticeData;
    }

    @GetMapping("/records/import/{taskId}")
    @SuppressWarnings("unchecked")
    public ApiResponse<Void> importRecords(@PathVariable Long taskId,
                                           @RequestParam("fileUrl") String fileUrl,
                                           HttpServletRequest request) throws IOException {
        if (StrUtil.isBlank(fileUrl)) {
            return ApiResponse.error("文件地址不能为空");
        }
        DocumentTask task = documentTaskServer.getTaskAndItemById(taskId);
        if (task == null) {
            return ApiResponse.error("任务不存在");
        }
        List<DocumentTaskItem> taskItems = task.getItems();
        if (taskItems.isEmpty()) {
            return ApiResponse.error("任务项不存在");
        }
        DocumentTaskItem item = taskItems.getFirst();
        try {
            String fullUrl = buildFullUrl(fileUrl, request);
            RestTemplate restTemplate = new RestTemplate();
            byte[] fileBytes = restTemplate.getForObject(fullUrl, byte[].class);
            if (fileBytes == null || fileBytes.length == 0) {
                return ApiResponse.error("下载文件失败");
            }
            List<DetailRecordImport> importList = EasyExcel.read(new ByteArrayInputStream(fileBytes), DetailRecordImport.class, null)
                    .sheet(1)
                    .doReadSync();
            if (importList == null || importList.isEmpty()) {
                return ApiResponse.error("文件无数据");
            }
            List<DetailRecord> records = convertToDetailRecords(importList);
            JSONObject jsonObject = JSONUtil.parseObj(item.getChangeResult());
            jsonObject.set("records", records);
            item.setChangeResult(jsonObject.toString());
            documentTaskServer.updateTaskItem(item);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error("导入失败：" + e.getMessage());
        }
    }

    private String buildFullUrl(String fileUrl, HttpServletRequest request) {
        if (StrUtil.isBlank(fileUrl)) {
            return fileUrl;
        }
        String trimmed = fileUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (!((scheme.equalsIgnoreCase("http") && port == 80) || (scheme.equalsIgnoreCase("https") && port == 443))) {
            sb.append(":").append(port);
        }
        if (!trimmed.startsWith("/")) {
            sb.append("/");
        }
        sb.append(trimmed);
        return sb.toString();
    }

    private List<DetailRecord> convertToDetailRecords(List<DetailRecordImport> dataList) {
        List<DetailRecord> records = new ArrayList<>();
        for (DetailRecordImport row : dataList) {
            if (row == null) {
                continue;
            }
            DetailRecord record = new DetailRecord();
            record.setAccount(safeToString(row.getAccount()));
            record.setAccountType(safeToString(row.getAccountType()));
            record.setYear(row.getYear());
            record.setCurrency(safeToString(row.getCurrency()));
            record.setDate(safeToString(row.getDate()));
            record.setSummary(safeToString(row.getSummary()));
            record.setDeposit(safeToString(row.getDeposit()));
            record.setWithdrawal(safeToString(row.getWithdrawal()));
            record.setBalance(safeToString(row.getBalance()));
            record.setCategory(safeToString(row.getCategory()));
            records.add(record);
        }
        return records;
    }

    @PostMapping("/config")
    public ApiResponse<Map<String, Object>> exportByConfig(@RequestBody ExportRequestDTO request) {
        try {
            if (request == null) {
                return ApiResponse.error("请求参数不能为空");
            }
            HashMap<String, Object> data = request.getData();
            List<SheetConfig> dataConfig = request.getDataConfig();
            String fileName = request.getFileName();
            if (data == null) {
                data = new HashMap<>();
            }
            if (dataConfig == null || dataConfig.isEmpty()) {
                return ApiResponse.error("工作表配置不能为空");
            }
            String finalFileName = (fileName == null || fileName.trim().isEmpty()) ? "export" : fileName;
            String storedFileName = extractFilename("_" + finalFileName);
            String absoluteFilePath = ensureDownloadPath(storedFileName);
            try (FileOutputStream fos = new FileOutputStream(absoluteFilePath)) {
                BaseExportUtil.export(data, dataConfig, fos);
                fos.flush();
            }
            Map<String, Object> result = new HashMap<>();
            result.put("url", storageService.buildDownloadUrl(storedFileName));
            result.put("fileName", storedFileName);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error("导出Excel失败：" + e.getMessage());
        }
    }
}
