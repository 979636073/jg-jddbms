package com.jd.biz.migration;

import com.jd.common.tools.base.excption.BusinessException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

/** AND-only structured conditions; identifiers come from metadata and every value is bound. */
final class MigrationFilter {
    private static final Map<String, String> OPERATORS = new HashMap<>();
    static { OPERATORS.put("EQ", "="); OPERATORS.put("NE", "<>"); OPERATORS.put("GT", ">"); OPERATORS.put("GE", ">="); OPERATORS.put("LT", "<"); OPERATORS.put("LE", "<="); OPERATORS.put("LIKE", "LIKE"); }

    static String predicate(TablePlan table) {
        List<String> parts = new ArrayList<>();
        for (Filter filter : table.filters) {
            Column column = column(table, filter); String name = quote(column.name);
            if ("IS_NULL".equals(filter.operator) || "IS_NOT_NULL".equals(filter.operator)) parts.add(name + ("IS_NULL".equals(filter.operator) ? " IS NULL" : " IS NOT NULL"));
            else {
                String operator = OPERATORS.get(filter.operator);
                if (operator == null || !Arrays.asList("NUMBER", "TEXT", "TIMESTAMP").contains(column.kind) || ("LIKE".equals(filter.operator) && !"TEXT".equals(column.kind))) throw new BusinessException("筛选操作符或字段类型不支持");
                value(column, filter);
                parts.add(name + " " + operator + ("TIMESTAMP".equals(column.kind) ? " TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS.FF6')" : " ?"));
            }
        }
        return String.join(" AND ", parts);
    }

    static String where(TablePlan table) { String predicate = predicate(table); return predicate.isEmpty() ? "" : " WHERE " + predicate; }

    static void bind(PreparedStatement query, TablePlan table, int index) throws SQLException {
        for (Filter filter : table.filters) {
            if ("IS_NULL".equals(filter.operator) || "IS_NOT_NULL".equals(filter.operator)) continue;
            Column column = column(table, filter); Object value = value(column, filter);
            if (value instanceof BigDecimal) query.setBigDecimal(index++, (BigDecimal) value);
            else if (column.sourceType != null && column.sourceType.startsWith("N")) query.setNString(index++, (String) value);
            else query.setString(index++, (String) value);
        }
    }

    private static Column column(TablePlan table, Filter filter) {
        if (filter == null) throw new BusinessException("筛选条件不能为空");
        return table.columns.stream().filter(column -> column.name.equals(filter.column)).findFirst().orElseThrow(() -> new BusinessException("筛选引用未知源字段"));
    }

    private static Object value(Column column, Filter filter) {
        if (filter.value == null || filter.value.isEmpty() || filter.value.length() > 4000) throw new BusinessException("筛选值必须为 1～4000 字符，空值请使用 IS NULL");
        try {
            if ("NUMBER".equals(column.kind)) {
                if (!filter.value.matches("[+-]?\\d+(?:\\.\\d+)?")) throw new IllegalArgumentException();
                BigDecimal number = new BigDecimal(filter.value); if (number.precision() > 38 || number.scale() > 127) throw new IllegalArgumentException(); return number;
            }
            if ("TIMESTAMP".equals(column.kind)) {
                if (!filter.value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,6})?")) throw new IllegalArgumentException();
                LocalDateTime time = LocalDateTime.parse(filter.value.replace(' ', 'T')); if (time.getYear() < 1) throw new IllegalArgumentException();
                return time.format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSS"));
            }
            return filter.value;
        } catch (IllegalArgumentException | java.time.DateTimeException error) { throw new BusinessException("筛选数值或民用时间格式不正确（时间使用 yyyy-MM-dd HH:mm:ss，最多 6 位小数）"); }
    }
}
