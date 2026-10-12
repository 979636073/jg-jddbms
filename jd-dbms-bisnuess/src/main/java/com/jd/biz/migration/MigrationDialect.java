package com.jd.biz.migration;

import com.jd.common.tools.base.excption.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import static com.jd.biz.migration.MigrationModel.*;

public final class MigrationDialect {
    public static final int MAX_OBJECTS = 10000;
    private static final Set<String> SYSTEM_SCHEMAS = new HashSet<>(Arrays.asList(
            "SYS", "SYSTEM", "SYSSSO", "SYSAUDITOR", "CTISYS", "SYSINFO", "MDSYS", "CTXSYS", "XDB"));

    private MigrationDialect() { }

    public static String quote(String name) {
        if (name == null || name.trim().isEmpty() || name.length() > 128
                || name.chars().anyMatch(c -> Character.isISOControl(c))) {
            throw new BusinessException("对象名称为空、过长或包含控制字符");
        }
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    public static String qualified(String schema, String name) { return quote(schema) + "." + quote(name); }
    public static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }
    public static boolean systemSchema(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return SYSTEM_SCHEMAS.contains(upper) || upper.startsWith("APEX_") || upper.startsWith("FLOWS_");
    }

    public static void validate(Config config, boolean complete) {
        if (config == null || config.name == null || config.name.trim().isEmpty() || config.name.length() > 80) {
            throw new BusinessException("请填写 1～80 字符的迁移名称");
        }
        validateStrategy(config.scope, config.conflict);
        if (!Arrays.asList("STOP", "CONTINUE_INDEPENDENT").contains(config.errorPolicy)) throw new BusinessException("错误策略不支持");
        if (config.rowsPerSecond < 0 || config.rowsPerSecond > 100000) throw new BusinessException("写入限速必须为 0～100000 行/秒，0 表示不限速");
        if (config.parallelism < 1 || config.parallelism > 4) throw new BusinessException("表级并发必须为 1～4，LOB 表始终单独串行处理");
        if (config.targetTablespace != null && !config.targetTablespace.isEmpty()) quote(config.targetTablespace);
        if (config.fetchSize < 1 || config.fetchSize > 1000 || config.commitSize < 1 || config.commitSize > 1000) {
            throw new BusinessException("读取与提交批次必须为 1～1000 行");
        }
        if (config.tables == null || config.tables.size() > MAX_OBJECTS) throw new BusinessException("单任务最多选择 " + MAX_OBJECTS + " 个对象");
        if (!complete) return;
        if (config.sourceId == null || config.targetId == null || config.tables.isEmpty()) {
            throw new BusinessException("请选择源端、目标端及至少一张表");
        }
        quote(config.sourceSchema); quote(config.targetSchema);
        if (systemSchema(config.sourceSchema) || systemSchema(config.targetSchema)) {
            throw new BusinessException("迁移只允许业务模式，系统模式不能作为源端或目标端");
        }
        Set<String> sources = new HashSet<>(), targets = new HashSet<>();
        for (Mapping table : config.tables) {
            if (table == null) throw new BusinessException("表映射不能为空");
            quote(table.source); quote(table.target);
            if (table.targetTablespace != null && !table.targetTablespace.isEmpty()) quote(table.targetTablespace);
            String from = sourceSchema(config, table), to = targetSchema(config, table);
            quote(from); quote(to);
            if (systemSchema(from) || systemSchema(to)) throw new BusinessException("系统模式不能参与迁移");
            if (!Arrays.asList("TABLE", "VIEW", "SEQUENCE", "SYNONYM", "PROCEDURE", "FUNCTION", "PACKAGE", "TRIGGER").contains(table.type)) throw new BusinessException("对象类型尚未适配");
            String scope = scope(config, table), conflict = conflict(config, table); validateStrategy(scope, conflict);
            if (!"TABLE".equals(table.type) && Arrays.asList("APPEND", "CLEAR", "REBUILD").contains(conflict)) throw new BusinessException("高风险策略只允许明确选择的表，不替换程序对象");
            if ("DATA".equals(scope) && !"TABLE".equals(table.type)) throw new BusinessException("仅数据范围只能选择表");
            if (table.filters == null || table.filters.size() > 20) throw new BusinessException("每张表最多 20 个筛选条件");
            if (!table.filters.isEmpty() && (!"TABLE".equals(table.type) || "STRUCTURE".equals(scope))) throw new BusinessException("筛选仅用于包含数据迁移的普通表");
            if (!sources.add(qualified(from, table.source)) || !targets.add(qualified(to, table.target))) throw new BusinessException("源对象或目标对象映射重复");
            if (table.columns == null || table.columns.size() > 1000 || table.dependencies == null || table.dependencies.size() > 100) throw new BusinessException("字段或依赖映射超出限制");
            Set<String> fields = new HashSet<>(), mapped = new HashSet<>();
            for (FieldMapping column : table.columns) {
                quote(column.source); quote(column.target);
                if (!fields.add(column.source) || !mapped.add(column.target)) throw new BusinessException("字段映射重复");
            }
        }
        if (config.sourceId.equals(config.targetId)
                && targets.stream().anyMatch(sources::contains)) throw new BusinessException("目标映射不能覆盖本任务中的任一源表");
    }

    static void validateStrategy(String scope, String conflict) {
        if (!Arrays.asList("ALL", "STRUCTURE", "DATA").contains(scope) || !Arrays.asList("STOP", "SKIP", "APPEND", "CLEAR", "REBUILD").contains(conflict)) throw new BusinessException("迁移范围或冲突策略不支持");
        if (Arrays.asList("APPEND", "CLEAR").contains(conflict) && !"DATA".equals(scope)) throw new BusinessException("追加/清空策略只用于预建兼容表的仅数据迁移");
        if ("REBUILD".equals(conflict) && "DATA".equals(scope)) throw new BusinessException("删除重建策略需要包含表结构迁移");
    }
    public static String scope(Config config, Mapping table) { return table.scope == null || table.scope.isEmpty() ? config.scope : table.scope; }
    public static String conflict(Config config, Mapping table) { return table.conflict == null || table.conflict.isEmpty() ? config.conflict : table.conflict; }
    public static boolean highRisk(Config config) { return config.tables.stream().anyMatch(table -> Arrays.asList("APPEND", "CLEAR", "REBUILD").contains(conflict(config, table))); }

    public static String sourceSchema(Config config, Mapping mapping) { return mapping.sourceSchema == null ? config.sourceSchema : mapping.sourceSchema; }
    public static String targetSchema(Config config, Mapping mapping) { return mapping.targetSchema == null || mapping.targetSchema.isEmpty() ? config.targetSchema : mapping.targetSchema; }
    public static Config scoped(Config config, TablePlan table) {
        Config scoped = new Config(); scoped.sourceSchema = table.sourceSchema == null ? config.sourceSchema : table.sourceSchema;
        scoped.targetSchema = table.targetSchema == null ? config.targetSchema : table.targetSchema;
        scoped.scope = table.scope == null ? config.scope : table.scope; scoped.conflict = table.conflict == null ? config.conflict : table.conflict;
        scoped.fetchSize = config.fetchSize; scoped.commitSize = config.commitSize; scoped.rowsPerSecond = config.rowsPerSecond;
        return scoped;
    }
    public static String targetName(Column column) { return column.targetName == null ? column.name : column.targetName; }
    public static String mappedName(TablePlan table, String source) {
        return table.columns.stream().filter(c -> c.name.equals(source)).map(MigrationDialect::targetName).findFirst()
                .orElseThrow(() -> new BusinessException("约束或索引引用未知字段，不能猜测映射"));
    }
    public static List<Column> targetColumns(TablePlan table) {
        return table.columns.stream().map(c -> { Column target = new Column(); target.name = targetName(c);
            target.kind = c.kind; target.targetType = c.targetType; return target; }).collect(Collectors.toList());
    }

    public static void mapType(Column column, String source, String target) {
        String type = column.sourceType.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (type.matches("NUMBER|NUMERIC|DECIMAL|DEC|INTEGER|INT|BIGINT|SMALLINT|TINYINT")) {
            column.kind = "NUMBER";
            if (type.matches("INTEGER|INT|BIGINT|SMALLINT|TINYINT") && column.precision == null) {
                column.precision = type.equals("BIGINT") ? 19 : type.equals("SMALLINT") ? 5 : type.equals("TINYINT") ? 3 : 10;
                column.scale = 0;
            }
            int precision = column.precision == null ? 38 : column.precision;
            int scale = column.scale == null ? 0 : column.scale;
            if (precision < 1 || precision > 38 || scale < -84 || scale > 127
                    || ("DM".equals(target) && (scale < 0 || scale > precision))) {
                throw new BusinessException("数值精度/标度超出目标支持范围，需要明确转换");
            }
            column.targetType = column.precision == null ? "NUMBER" : "NUMBER(" + precision + "," + scale + ")";
        } else if (type.matches("VARCHAR2?|CHAR|NVARCHAR2?|NCHAR")) {
            column.kind = "TEXT";
            boolean national = type.startsWith("N"), fixed = type.equals("CHAR") || type.equals("NCHAR");
            int limit = "ORACLE".equals(target) ? (national || fixed ? 2000 : 4000) : 32767;
            if (column.length < 1 || column.length > limit) throw new BusinessException("字符长度超出首版目标范围，不能自动截断");
            String mapped = national ? (fixed ? "NCHAR" : ("ORACLE".equals(target) ? "NVARCHAR2" : "NVARCHAR"))
                    : (fixed ? "CHAR" : ("ORACLE".equals(target) ? "VARCHAR2" : "VARCHAR"));
            column.targetType = mapped + "(" + column.length
                    + (!national && column.unit != null ? " " + column.unit : "") + ")";
        } else if (type.equals("RAW") || type.equals("VARBINARY") || type.equals("BINARY")) {
            column.kind = "BINARY";
            if (column.length < 1 || column.length > 2000) throw new BusinessException("RAW/二进制长度超出首版范围");
            column.targetType = ("ORACLE".equals(target) ? "RAW" : "VARBINARY") + "(" + column.length + ")";
        } else if ("DM".equals(source) && type.equals("TEXT")) {
            // DM exposes NCLOB as TEXT in ALL_TAB_COLUMNS; retain the national-character LOB path.
            column.kind = "NCLOB"; column.targetType = "NCLOB";
        } else if (type.matches("BLOB|CLOB|NCLOB")) {
            column.kind = type; column.targetType = type;
        } else if (type.equals("DATE") || type.matches("TIMESTAMP(?:\\(\\d+\\))?")) {
            column.kind = "TIMESTAMP";
            int scale = type.equals("DATE") ? 0 : (column.scale == null ? 6 : column.scale);
            if (scale < 0 || scale > 6) throw new BusinessException("首版时间精度最多 6 位，不允许静默丢失精度");
            column.targetType = "TIMESTAMP(" + scale + ")";
        } else {
            throw new BusinessException("类型 " + type + " 尚未通过迁移适配，不能自动转成字符串");
        }
    }

    public static String defaultSql(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String text = value.trim();
        if (text.equalsIgnoreCase("NULL") || text.matches("[+-]?\\d+(?:\\.\\d+)?")
                || text.matches("'(?:[^']|'')*'")) return " DEFAULT " + text;
        throw new BusinessException("默认值表达式尚未适配，请先完成明确转换");
    }

    public static String createTable(String schema, TablePlan table) {
        String columns = table.columns.stream().map(c -> quote(targetName(c)) + " " + c.targetType
                + defaultSql(c.defaultValue) + (c.nullable ? "" : " NOT NULL")).collect(Collectors.joining(",\n  "));
        if (!table.primaryKey.isEmpty()) columns += ",\n  PRIMARY KEY (" + names(table.primaryKey.stream().map(n -> mappedName(table, n)).collect(Collectors.toList())) + ")";
        return "CREATE TABLE " + qualified(schema, table.target) + " (\n  " + columns + "\n)" + (table.targetTablespace == null ? "" : " TABLESPACE " + quote(table.targetTablespace));
    }

    public static String names(java.util.List<String> names) {
        return names.stream().map(MigrationDialect::quote).collect(Collectors.joining(", "));
    }

    public static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte b : bytes) output.append(String.format("%02x", b & 255));
            return output.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
