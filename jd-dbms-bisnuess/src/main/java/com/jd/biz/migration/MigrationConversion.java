package com.jd.biz.migration;

import com.jd.common.tools.base.excption.BusinessException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

/** Explicit SQL CAST/attribute projection, not arbitrary SQL or automatic UDT serialization. */
final class MigrationConversion {
    private MigrationConversion() { }

    static void apply(Column column, FieldMapping field, String source, String target) {
        if (field == null || field.targetType == null || field.targetType.trim().isEmpty()) {
            if (field != null && field.sourceAttribute != null && !field.sourceAttribute.trim().isEmpty()) throw new BusinessException("属性投影必须明确转换类型");
            mapType(column, source, target); return;
        }
        if (!field.conversionConfirmed) throw new BusinessException("自定义类型转换必须确认精度、截断及属性投影的业务语义");
        if (column.defaultValue != null && !column.defaultValue.trim().equalsIgnoreCase("NULL") && !column.defaultValue.trim().isEmpty()) throw new BusinessException("转换字段存在默认值，请专项转换默认值，不能静默丢弃");
        Column destination = new Column();
        String type = field.targetType.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if ("DM".equals(source) && "TEXT".equalsIgnoreCase(column.sourceType)
                && (type.equals("CLOB") || type.equals("NCLOB"))) {
            if (field.sourceAttribute != null && !field.sourceAttribute.trim().isEmpty()) throw new BusinessException("大字段类型选择不支持对象属性投影");
            column.kind = type; column.targetType = type; return;
        }
        Matcher number = Pattern.compile("NUMBER\\((\\d+),(-?\\d+)\\)").matcher(type);
        Matcher text = Pattern.compile("(VARCHAR|NVARCHAR)\\((\\d+)\\)").matcher(type);
        Matcher binary = Pattern.compile("RAW\\((\\d+)\\)").matcher(type);
        Matcher time = Pattern.compile("TIMESTAMP\\(([0-6])\\)").matcher(type);
        if (number.matches()) { destination.sourceType = "NUMBER"; destination.precision = bounded(number.group(1)); destination.scale = bounded(number.group(2)); }
        else if (text.matches()) { destination.sourceType = text.group(1); destination.length = bounded(text.group(2)); destination.unit = "CHAR"; }
        else if (binary.matches()) { destination.sourceType = "RAW"; destination.length = bounded(binary.group(1)); }
        else if (time.matches()) { destination.sourceType = "TIMESTAMP"; destination.scale = bounded(time.group(1)); }
        else throw new BusinessException("转换类型请使用 NUMBER(p,s)、VARCHAR(n)、NVARCHAR(n)、RAW(n) 或 TIMESTAMP(0～6)，不接受 SQL 表达式");
        mapType(destination, target, target);
        Column projection = new Column(); projection.sourceType = destination.sourceType; projection.precision = destination.precision;
        projection.scale = destination.scale; projection.length = destination.length; projection.unit = destination.unit;
        mapType(projection, source, source);
        String expression = quote(column.name);
        if (field.sourceAttribute != null && !field.sourceAttribute.trim().isEmpty()) {
            String[] attributes = field.sourceAttribute.split("\\.", -1);
            if (attributes.length > 3) throw new BusinessException("对象属性最多三层");
            expression = "JD_SRC." + expression;
            for (String attribute : attributes) {
                if (!attribute.matches("[\\p{L}_][\\p{L}\\p{N}_$#]*")) throw new BusinessException("属性路径只能填写字段标识符，不接受 SQL");
                expression += "." + quote(attribute);
            }
        }
        column.sourceExpression = "CAST(" + expression + " AS " + projection.targetType + ")";
        column.targetType = destination.targetType; column.kind = destination.kind; column.length = destination.length;
        column.precision = destination.precision; column.scale = destination.scale; column.unit = destination.unit;
    }

    private static int bounded(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException error) { throw new BusinessException("类型尺寸超出范围"); }
    }
}
