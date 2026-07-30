package com.rcszh.tax.util;

import com.rcszh.tax.util.export.CellConfig;
import com.rcszh.tax.util.export.RowConfig;
import com.rcszh.tax.util.export.SheetConfig;
import com.rcszh.tax.util.export.StyleConfig;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseExportUtil {
    /**
     * 导出
     *
     * @param data 数据源
     * @param dataConfig 工作表配置
     * @param outputStream 输出流（可以是文件流或响应流）
     */
    public static void export(HashMap<String,Object> data, List<SheetConfig> dataConfig, OutputStream outputStream) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(); // 使用流式API防止内存溢出

        // 1. 样式缓存池 (关键！POI限制一个Workbook最多约4000个CellStyle)
        // Key可以是 StyleConfig 的 hash 或 toString
        Map<String, CellStyle> styleCache = new HashMap<>();

        for (SheetConfig sheetConf : dataConfig) {
            Sheet sheet = workbook.createSheet(sheetConf.getSheetName());
            // 2. 设置列宽
            if (sheetConf.getColumnWidths() != null) {
                sheetConf.getColumnWidths().forEach(sheet::setColumnWidth);
            }
            // 3. 逐行绘制
            int index = 0;
            for (RowConfig rowConf : sheetConf.getRows()) {
                int itemIndex = rowConf.getIndex() == null ? index++ : rowConf.getIndex();
                Row row = sheet.createRow(itemIndex);
                if (rowConf.getHeight() != null) {
                    row.setHeight(rowConf.getHeight());
                }

                for (CellConfig cellConf : rowConf.getCells()) {
                    // A. 创建单元格
                    Cell cell = row.createCell(cellConf.getColIndex());
                    // B. 填充值 (使用反射或 BeanUtils 获取 data 中的值)
                    Object value = data.get(cellConf.getSourceKey());
                    cell.setCellValue(value == null ? "缺失数据，请检查数据完整性" :value.toString());
                    // C. 设置样式 (从缓存获取或创建)
                    CellStyle style = getCellStyle(workbook, styleCache, cellConf.getStyle());
                    cell.setCellStyle(style);
                    // D. 处理合并单元格 (Merge)
                    if (cellConf.getRowSpan() > 1 || cellConf.getColSpan() > 1) {
                        CellRangeAddress region = new CellRangeAddress(
                                itemIndex,
                                itemIndex + cellConf.getRowSpan() - 1,
                                cellConf.getColIndex(),
                                cellConf.getColIndex() + cellConf.getColSpan() - 1
                        );
                        sheet.addMergedRegion(region);
                        // E. 重要：合并后的边框处理
                        // POI 合并后，只有左上角单元格有样式，需要使用 RegionUtil 为整个合并区域设置边框
                        if (cellConf.getStyle() != null && cellConf.getStyle().isBorder()) {
                            fixRegionBorder(region, sheet, workbook);
                        }
                    }
                }
            }
        }
        workbook.write(outputStream);
    }

    // 获取或创建样式
    private static CellStyle getCellStyle(Workbook workbook, Map<String, CellStyle> cache, StyleConfig conf) {
        // TODO: 启用样式缓存以优化性能（POI限制一个Workbook最多约4000个CellStyle）
        // String key = conf.getUniqueKey();
        // return cache.computeIfAbsent(key, k -> { ... });
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        // 字体设置
        font.setFontName(conf.getFontName() == null ? "宋体" : conf.getFontName());
        font.setFontHeightInPoints(conf.getFontSize());
        font.setBold(conf.isBold());
        style.setFont(font);
        // 对齐设置
        style.setAlignment(HorizontalAlignment.valueOf(conf.getAlignment()));
        style.setVerticalAlignment(VerticalAlignment.valueOf(conf.getVerticalAlignment()));
        style.setWrapText(true); // 自动换行
        // 边框设置
        if (conf.isBorder()) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }

        // 数据格式化
//            if (conf.getDataFormat() != null) {
//                style.setDataFormat(workbook.createDataFormat().getFormat(conf.getDataFormat()));
//            }

        return style;
    }

    /**
     * 为合并单元格区域设置边框
     * 使用 RegionUtil 为整个合并区域的所有边界设置边框样式
     *
     * @param region 合并单元格区域
     * @param sheet 工作表
     * @param workbook 工作簿
     */
    private static void fixRegionBorder(CellRangeAddress region, Sheet sheet, Workbook workbook) {
        // 创建边框样式（使用细线边框）
        BorderStyle borderStyle = BorderStyle.THIN;

        // 为合并区域的上边界设置边框
        RegionUtil.setBorderTop(borderStyle, region, sheet);

        // 为合并区域的下边界设置边框
        RegionUtil.setBorderBottom(borderStyle, region, sheet);

        // 为合并区域的左边界设置边框
        RegionUtil.setBorderLeft(borderStyle, region, sheet);

        // 为合并区域的右边界设置边框
        RegionUtil.setBorderRight(borderStyle, region, sheet);
    }
}
