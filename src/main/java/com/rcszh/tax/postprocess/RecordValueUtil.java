package com.rcszh.tax.postprocess;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Map records 取值/解析工具（尽量容错）。
 */
public final class RecordValueUtil {
    /** 工具类不允许实例化。 */
    private RecordValueUtil() {}

    /**
     * 按候选 key 依次取第一个非空字符串值。
     */
    public static String firstString(Map<String, Object> record, String... keys) {
        for (String key : keys) {
            if (key == null) continue;
            Object v = record.get(key);
            if (v == null) continue;
            String s = v.toString().trim();
            if (StrUtil.isNotBlank(s)) return s;
        }
        return null;
    }

    /**
     * 按候选 key 依次取第一个可解析为 BigDecimal 的值。
     */
    public static BigDecimal firstDecimal(Map<String, Object> record, String... keys) {
        for (String key : keys) {
            Object v = record.get(key);
            BigDecimal bd = toDecimal(v);
            if (bd != null) return bd;
        }
        return null;
    }

    /**
     * 将对象解析成 BigDecimal（支持常见金额格式清洗，例如 1,234.56、(123.45)、带币种/符号）。
     */
    public static BigDecimal toDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        String s = v.toString().trim();
        if (StrUtil.isBlank(s)) return null;
        // 常见金额格式清洗：逗号、空格、货币符号、括号负数
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1);
        }
        s = s.replace(",", "")
                .replace("，", "")
                .replace("￥", "")
                .replace("$", "")
                .replace("HK$", "")
                .replace("USD", "")
                .replace("CNY", "")
                .replace("RMB", "")
                .trim();
        if (StrUtil.isBlank(s)) return null;
        try {
            BigDecimal bd = new BigDecimal(s);
            return negative ? bd.negate() : bd;
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 按候选 key 依次取第一个可解析为 LocalDate 的值。
     */
    public static LocalDate firstLocalDate(Map<String, Object> record, String... keys) {
        for (String key : keys) {
            Object v = record.get(key);
            LocalDate d = toLocalDate(v);
            if (d != null) return d;
        }
        return null;
    }

    /**
     * 将对象解析为 LocalDate（支持 Date、以及 "2020年11月11日"、"2024-12-01" 等字符串）。
     */
    public static LocalDate toLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate ld) return ld;
        if (v instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String s = v.toString().trim();
        if (StrUtil.isBlank(s)) return null;
        // hutool 对 "2020年11月11日"、"2024-12-01" 等兼容较好
        try {
            Date d = DateUtil.parse(s);
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 匹配 {@code 4:1}、{@code 10：1} 等拆股或合股比例表达式。 */
    private static final Pattern SPLIT_PATTERN = Pattern.compile(
            "(?<a>\\d+(?:\\.\\d+)?)\\s*[:：]\\s*(?<b>\\d+(?:\\.\\d+)?)"
    );

    /**
     * 从描述中解析拆分比例，例如：
     * - "4:1拆股" -> 4
     * - "10：1拆股" -> 10
     * - "1:10合股" -> 0.1
     */
    public static BigDecimal parseSplitRatio(String text) {
        if (StrUtil.isBlank(text)) return null;
        Matcher m = SPLIT_PATTERN.matcher(text);
        if (!m.find()) return null;
        BigDecimal a = new BigDecimal(m.group("a"));
        BigDecimal b = new BigDecimal(m.group("b"));
        if (b.compareTo(BigDecimal.ZERO) == 0) return null;
        return a.divide(b, 18, RoundingMode.HALF_UP);
    }
}
