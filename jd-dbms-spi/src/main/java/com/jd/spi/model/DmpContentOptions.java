package com.jd.spi.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The nine independent schema DMP content switches exposed by the UI. */
public final class DmpContentOptions {
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            "ROWS", "INDEXES", "CONSTRAINTS", "TRIGGERS", "VIEWS",
            "PROCEDURE", "PACKAGE", "SEQUENCE", "GRANTS"));

    private DmpContentOptions() { }

    /** Null keeps existing API callers on the native tool defaults. */
    public static List<String> normalize(List<String> selected) {
        if (selected == null) return null;
        Set<String> names = new LinkedHashSet<>();
        for (String value : selected) {
            if (value == null || !ALL.contains(value.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("不支持的 DMP 导入导出内容：" + value);
            }
            names.add(value.toUpperCase(Locale.ROOT));
        }
        if (names.isEmpty()) throw new IllegalArgumentException("请至少选择一项 DMP 导入导出内容");
        return new ArrayList<>(names);
    }

    public static List<String> excluded(List<String> selected) {
        if (selected == null) return Collections.emptyList();
        List<String> result = new ArrayList<>(ALL);
        result.removeAll(selected);
        return result;
    }

    public static void validate(String family, boolean importing, boolean tableScope, List<String> selected) {
        if (selected == null) return;
        List<String> excluded = excluded(selected);
        if (tableScope && !excluded.isEmpty()) {
            throw new IllegalArgumentException("内容筛选仅适用于模式 DMP 导入导出");
        }
        if ("ORACLE_LEGACY".equalsIgnoreCase(family)) {
            for (String name : excluded) {
                if (Arrays.asList("VIEWS", "PROCEDURE", "PACKAGE", "SEQUENCE").contains(name)
                        || (importing && "TRIGGERS".equals(name))) {
                    throw new IllegalArgumentException("Oracle 传统 EXP/IMP 不支持单独筛选“" + name
                            + "”，请改用 Oracle Data Pump 客户端格式");
                }
            }
        }
    }
}
