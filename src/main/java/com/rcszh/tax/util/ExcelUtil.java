package com.rcszh.tax.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.rcszh.tax.dto.ExcelParseResult;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel 工具类
 */
public class ExcelUtil {
    private static final Set<String> ALLOWED_SUFFIX = Set.of(
            "xls", "xlsx", "xlsm", "xlsb", "csv"
    );

    /**
     * 判断远程地址url是否为Excel
     * @param originalFileUrl 远程url文件地址
     */
    public static boolean checkFileSuffix(String originalFileUrl) {
        if (originalFileUrl == null || originalFileUrl.isBlank()){
            return false;
        }
        String suffix = originalFileUrl
                .substring(originalFileUrl.lastIndexOf('.') + 1)
                .toLowerCase();
        return ALLOWED_SUFFIX.contains(suffix);
    }


    public static List<ExcelParseResult> readExcel(File file) {
        List<ExcelParseResult> result = new ArrayList<>();
        NoModelDataListener noModelDataListener = new NoModelDataListener();
        try (ExcelReader excelReader = EasyExcel.read(file, noModelDataListener).build()) {
            excelReader.readAll();
        }
        List<NoModelDataListener.RowData> dataList = noModelDataListener.getRows();
        for (NoModelDataListener.RowData rowData : dataList) {
            ExcelParseResult excelParseResult = new ExcelParseResult();
            excelParseResult.setExcelData(rowData.getData());
            excelParseResult.setRowIndex(rowData.getRowIndex());
            excelParseResult.setSheetName(rowData.getSheetName());
            result.add(excelParseResult);
        }
        return result;
    }


    public static class NoModelDataListener extends AnalysisEventListener<Map<Integer, String>> {
        @Getter
        private final List<RowData> rows = new ArrayList<>();
        @Getter
        private Map<Integer, String> headMap;
        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            this.headMap = headMap;
        }

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            Map<String, String> resultData = new LinkedHashMap<>();
            data.forEach((k, v) -> {
                resultData.put(headMap.getOrDefault(k,k.toString()), v);
            });
            RowData rowData = new RowData();
            rowData.setRowIndex(context.readRowHolder() == null ? null : context.readRowHolder().getRowIndex());
            String sheetName = context.readSheetHolder() == null ? null : context.readSheetHolder().getSheetName();
            Integer sheetNo = context.readSheetHolder() == null ? null : context.readSheetHolder().getSheetNo();
            rowData.setSheetName(sheetName == null ? "sheet" + (sheetNo == null ? 0 : sheetNo) : sheetName);
            rowData.setData(resultData);
            rows.add(rowData);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {

        }

        @Getter
        @Setter
        public static class RowData {
            private Integer rowIndex;
            private String sheetName;
            private Map<String, String> data = new LinkedHashMap<>();
        }
    }


}
