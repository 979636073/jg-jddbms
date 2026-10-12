package com.jd.biz.migration;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.common.tools.base.excption.BusinessException;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** SQL package for selected tables. Native object DDL is preserved verbatim and reviewed at import. */
@Service
public class SchemaSqlTransfer {
    private static final String MAGIC = "-- JDDBMS-SCHEMA-SQL 1";
    private static final int MAX_TABLES = 10000;
    private static final int MAX_SCRIPT_BYTES = 200 * 1024 * 1024;
    private final MigrationConnections connections;
    private final MigrationMetadata metadata;
    private final TableFileTransfer tableFiles;

    public SchemaSqlTransfer(MigrationConnections connections, MigrationMetadata metadata, TableFileTransfer tableFiles) {
        this.connections = connections; this.metadata = metadata; this.tableFiles = tableFiles;
    }

    public Map<String, Object> tables(DataSource source, String schema, String search, int page, int size) throws Exception {
        MigrationDialect.quote(schema);
        if (page < 1 || size < 1 || size > 200) throw new BusinessException("表分页范围无效");
        List<String> all = tableNames(source, schema, search);
        int from = (int) Math.min((long) all.size(), (long) (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        Map<String, Object> result = new LinkedHashMap<>(); result.put("data", all.subList(from, to)); result.put("total", all.size());
        return result;
    }

    public List<String> tableNames(DataSource source, String schema, String search) throws Exception {
        MigrationDialect.quote(schema);
        List<String> names = new ArrayList<>();
        String filter = search == null ? "" : search.toUpperCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        try (Connection connection = connections.open(source);
             PreparedStatement statement = connection.prepareStatement("SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER=? AND UPPER(TABLE_NAME) LIKE ? ESCAPE '!' ORDER BY TABLE_NAME")) {
            statement.setString(1, schema); statement.setString(2, "%" + filter + "%"); statement.setQueryTimeout(120);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String name = result.getString(1);
                    names.add(name); if (names.size() > MAX_TABLES) throw new BusinessException("一次最多选择 10000 张表");
                }
            }
        }
        return names;
    }

    public byte[] exportSql(DataSource source, String schema, List<String> selected, Options options) throws Exception {
        return exportSql(source, schema, selected, options, (fraction, message) -> { });
    }

    public byte[] exportSql(DataSource source, String schema, List<String> selected, Options options, Progress progress) throws Exception {
        if (options == null) throw new BusinessException("请选择导出内容");
        validateSelection(selected); options.validate();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writer.write(MAGIC + "\n-- SCHEMA " + encode(schema) + "\n-- SOURCE " + source.getType() + "\n");
        try (Connection connection = connections.open(source)) {
            Set<String> available = new HashSet<>(tableNames(source, schema, ""));
            int completed = 0;
            for (String table : selected) {
                if (!available.contains(table)) throw new BusinessException("所选表不存在：" + table);
                String create;
                try (Statement probe = connection.createStatement(); ResultSet empty = probe.executeQuery("SELECT * FROM " + MigrationDialect.qualified(schema, table) + " WHERE 1=0")) {
                    create = TableFileTransfer.createTableSql(empty.getMetaData(), table).replaceFirst("CREATE TABLE ", "CREATE TABLE " + MigrationDialect.quote(schema) + ".");
                }
                section(writer, "TABLE", table, table, create);
                if (options.dataRows) {
                    String script = new String(tableFiles.exportFile(source, schema, table, "SQL"), StandardCharsets.UTF_8);
                    int firstInsert = script.indexOf("INSERT INTO ");
                    if (firstInsert >= 0) section(writer, "DATA", table, table,
                            script.substring(firstInsert).replace("INSERT INTO " + MigrationDialect.quote(table),
                                    "INSERT INTO " + MigrationDialect.qualified(schema, table)));
                }
                if (options.indexes) objectsForTable(connection, writer, schema, table, "INDEX", "SELECT I.INDEX_NAME FROM ALL_INDEXES I WHERE I.TABLE_OWNER=? AND I.TABLE_NAME=? AND NOT EXISTS (SELECT 1 FROM ALL_CONSTRAINTS C WHERE C.OWNER=I.OWNER AND C.INDEX_NAME=I.INDEX_NAME AND C.CONSTRAINT_TYPE IN ('P','U')) ORDER BY I.INDEX_NAME");
                if (options.constraints) objectsForTable(connection, writer, schema, table, "CONSTRAINT", "SELECT CONSTRAINT_NAME FROM ALL_CONSTRAINTS WHERE OWNER=? AND TABLE_NAME=? AND CONSTRAINT_TYPE IN ('P','U','R','C') AND (CONSTRAINT_TYPE<>'C' OR CONSTRAINT_NAME NOT LIKE 'SYS_C%') ORDER BY CONSTRAINT_NAME");
                if (options.triggers) objectsForTable(connection, writer, schema, table, "TRIGGER", "SELECT TRIGGER_NAME FROM ALL_TRIGGERS WHERE TABLE_OWNER=? AND TABLE_NAME=? ORDER BY TRIGGER_NAME");
                writer.flush(); if (output.size() > MAX_SCRIPT_BYTES) throw new BusinessException("SQL 文件超过 200 MiB，请缩小表选择范围");
                progress.update((double) ++completed / selected.size() * 0.9, "已导出 " + completed + "/" + selected.size() + " 张表");
            }
            if (options.views) schemaObjects(connection, writer, schema, "VIEW", selected);
            if (options.procedures) { schemaObjects(connection, writer, schema, "PROCEDURE", selected); schemaObjects(connection, writer, schema, "FUNCTION", selected); schemaObjects(connection, writer, schema, "PACKAGE", selected); }
        }
        writer.flush(); if (output.size() > MAX_SCRIPT_BYTES) throw new BusinessException("SQL 文件超过 200 MiB，请缩小表选择范围");
        progress.update(1, "SQL 文件已生成");
        return output.toByteArray();
    }

    private void objectsForTable(Connection connection, Writer writer, String schema, String table, String kind, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema); statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String name = result.getString(1);
                    if ("INDEX".equals(kind) && name.matches("(?i)INDEX[0-9]+")) continue; // DM internal index for a constraint.
                    String ddl = objectDdl(connection, schema, table, name, kind);
                    section(writer, kind, table, name, ddl);
                }
            }
        }
    }

    private String objectDdl(Connection connection, String schema, String table, String name, String kind) throws Exception {
        if ("INDEX".equals(kind)) return indexDdl(connection, schema, table, name);
        if ("CONSTRAINT".equals(kind)) return constraintDdl(connection, schema, table, name);
        if ("TRIGGER".equals(kind)) {
            try { return metadata.sourceDdl(connection, schema, name, "TRIGGER"); }
            catch (Exception unavailable) { return triggerDdl(connection, schema, name); }
        }
        throw new BusinessException("对象类型不支持：" + kind);
    }

    private static String indexDdl(Connection connection, String schema, String table, String name) throws SQLException {
        String uniqueness;
        try (PreparedStatement query = connection.prepareStatement("SELECT UNIQUENESS FROM ALL_INDEXES WHERE OWNER=? AND INDEX_NAME=?")) {
            query.setString(1, schema); query.setString(2, name);
            try (ResultSet result = query.executeQuery()) { if (!result.next()) throw new BusinessException("索引不存在：" + name); uniqueness = result.getString(1); }
        }
        List<String> columns = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("SELECT COLUMN_NAME FROM ALL_IND_COLUMNS WHERE INDEX_OWNER=? AND INDEX_NAME=? ORDER BY COLUMN_POSITION")) {
            query.setString(1, schema); query.setString(2, name);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    String column = result.getString(1);
                    if (column.startsWith("SYS_NC")) throw new BusinessException("索引 " + name + " 包含表达式列，请使用数据库迁移审阅");
                    columns.add(MigrationDialect.quote(column));
                }
            }
        }
        if (columns.isEmpty()) throw new BusinessException("索引没有列：" + name);
        return "CREATE " + ("UNIQUE".equalsIgnoreCase(uniqueness) ? "UNIQUE " : "") + "INDEX "
                + MigrationDialect.qualified(schema, name) + " ON " + MigrationDialect.qualified(schema, table)
                + " (" + String.join(", ", columns) + ");";
    }

    private static String constraintDdl(Connection connection, String schema, String table, String name) throws SQLException {
        String type, referencedOwner, referencedName, condition;
        try (PreparedStatement query = connection.prepareStatement("SELECT CONSTRAINT_TYPE, R_OWNER, R_CONSTRAINT_NAME, SEARCH_CONDITION FROM ALL_CONSTRAINTS WHERE OWNER=? AND CONSTRAINT_NAME=?")) {
            query.setString(1, schema); query.setString(2, name);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new BusinessException("约束不存在：" + name);
                type = result.getString(1); referencedOwner = result.getString(2); referencedName = result.getString(3); condition = result.getString(4);
            }
        }
        String prefix = "ALTER TABLE " + MigrationDialect.qualified(schema, table) + " ADD CONSTRAINT " + MigrationDialect.quote(name) + " ";
        if ("P".equals(type) || "U".equals(type)) return prefix + ("P".equals(type) ? "PRIMARY KEY" : "UNIQUE")
                + " (" + String.join(", ", constraintColumns(connection, schema, name)) + ");";
        if ("C".equals(type)) return prefix + "CHECK (" + condition + ");";
        if ("R".equals(type)) {
            String referencedTable;
            try (PreparedStatement query = connection.prepareStatement("SELECT TABLE_NAME FROM ALL_CONSTRAINTS WHERE OWNER=? AND CONSTRAINT_NAME=?")) {
                query.setString(1, referencedOwner); query.setString(2, referencedName);
                try (ResultSet result = query.executeQuery()) { if (!result.next()) throw new BusinessException("外键引用目标不存在"); referencedTable = result.getString(1); }
            }
            return prefix + "FOREIGN KEY (" + String.join(", ", constraintColumns(connection, schema, name)) + ") REFERENCES "
                    + MigrationDialect.qualified(referencedOwner, referencedTable) + " (" + String.join(", ", constraintColumns(connection, referencedOwner, referencedName)) + ");";
        }
        throw new BusinessException("不支持的约束类型：" + type);
    }

    private static List<String> constraintColumns(Connection connection, String schema, String name) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("SELECT COLUMN_NAME FROM ALL_CONS_COLUMNS WHERE OWNER=? AND CONSTRAINT_NAME=? ORDER BY POSITION")) {
            query.setString(1, schema); query.setString(2, name);
            try (ResultSet result = query.executeQuery()) { while (result.next()) columns.add(MigrationDialect.quote(result.getString(1))); }
        }
        if (columns.isEmpty()) throw new BusinessException("约束没有列：" + name);
        return columns;
    }

    private static String triggerDdl(Connection connection, String schema, String name) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT DESCRIPTION, TRIGGER_BODY FROM ALL_TRIGGERS WHERE OWNER=? AND TRIGGER_NAME=?")) {
            query.setString(1, schema); query.setString(2, name);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) throw new BusinessException("触发器不存在：" + name);
                return "CREATE OR REPLACE TRIGGER " + result.getString(1).trim() + "\n" + result.getString(2).trim();
            }
        }
    }

    private void schemaObjects(Connection connection, Writer writer, String schema, String type, List<String> selected) throws Exception {
        Set<String> tables = new HashSet<>(selected);
        Map<String, String> names = new TreeMap<>();
        String queryType = "PACKAGE".equals(type) ? "PACKAGE BODY" : type;
        try (PreparedStatement statement = connection.prepareStatement("SELECT NAME, REFERENCED_NAME FROM ALL_DEPENDENCIES WHERE OWNER=? AND TYPE=? AND REFERENCED_OWNER=?")) {
            statement.setString(1, schema); statement.setString(2, queryType); statement.setString(3, schema);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String referencedTable = result.getString(2);
                    if (tables.contains(referencedTable)) names.putIfAbsent(result.getString(1), referencedTable);
                }
            }
        }
        for (Map.Entry<String, String> object : names.entrySet()) {
            String name = object.getKey(), table = object.getValue();
            section(writer, type, table, name, schemaObjectDdl(connection, schema, name, type));
            if ("PACKAGE".equals(type)) {
                String body = schemaObjectDdl(connection, schema, name, "PACKAGE BODY");
                if (body != null) section(writer, "PACKAGE_BODY", table, name, body);
            }
        }
    }

    private String schemaObjectDdl(Connection connection, String schema, String name, String type) throws Exception {
        if ("VIEW".equals(type)) {
            try (PreparedStatement query = connection.prepareStatement("SELECT TEXT FROM ALL_VIEWS WHERE OWNER=? AND VIEW_NAME=?")) {
                query.setString(1, schema); query.setString(2, name);
                try (ResultSet result = query.executeQuery()) {
                    if (!result.next()) throw new BusinessException("视图不存在：" + name);
                    return "CREATE OR REPLACE VIEW " + MigrationDialect.qualified(schema, name) + " AS " + result.getString(1).trim() + ";";
                }
            }
        }
        StringBuilder text = new StringBuilder();
        try (PreparedStatement query = connection.prepareStatement("SELECT TEXT FROM ALL_SOURCE WHERE OWNER=? AND NAME=? AND TYPE=? ORDER BY LINE")) {
            query.setString(1, schema); query.setString(2, name); query.setString(3, type);
            try (ResultSet result = query.executeQuery()) { while (result.next()) text.append(result.getString(1)); }
        }
        if (text.length() == 0) {
            if ("PACKAGE BODY".equals(type)) return null;
            return metadata.sourceDdl(connection, schema, name, type);
        }
        String source = text.toString().trim();
        return source.toUpperCase(Locale.ROOT).startsWith("CREATE ") ? source : "CREATE OR REPLACE " + source;
    }

    static void section(Writer writer, String type, String table, String name, String sql) throws IOException {
        String body = sql.trim();
        writer.write("-- JDDBMS-BEGIN " + type + " " + encode(table) + " " + encode(name) + " " + body.length() + "\n");
        writer.write(body); writer.write("\n-- JDDBMS-END\n");
    }

    public Preview preview(byte[] file) {
        Parsed parsed = parse(file);
        Preview result = new Preview(); result.schema = parsed.schema; result.sourceType = parsed.sourceType;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Section part : parsed.sections) if ("TABLE".equals(part.type)) names.add(part.table);
        result.tables = new ArrayList<>(names); return result;
    }

    public int importSql(DataSource target, String schema, byte[] file, List<String> selected, Options options) throws Exception {
        return importSql(target, schema, file, selected, options, (fraction, message) -> { });
    }

    public int importSql(DataSource target, String schema, byte[] file, List<String> selected, Options options, Progress progress) throws Exception {
        if (options == null) throw new BusinessException("请选择导入内容");
        Parsed parsed = parse(file); validateSelection(selected); options.validate();
        boolean nativeObjects = options.indexes || options.constraints || options.triggers || options.views || options.procedures;
        if (!parsed.schema.equals(schema) && nativeObjects) throw new BusinessException("跨模式 SQL 仅支持建表与数据行；索引、约束、触发器、视图和过程请使用数据库迁移并审阅转换结果");
        if (!parsed.sourceType.equals(target.getType()) && nativeObjects)
            throw new BusinessException("跨达梦/Oracle 的模式 SQL 仅支持建表与数据行；索引、约束、触发器、视图和过程请使用数据库迁移并审阅转换结果");
        Set<String> available = new HashSet<>(); for (Section section : parsed.sections) if ("TABLE".equals(section.type)) available.add(section.table);
        if (!available.containsAll(selected)) throw new BusinessException("所选表不在 SQL 文件中");
        int imported = 0;
        int totalSections = (int) parsed.sections.stream().filter(part -> options.enabled(part.type)
                && (part.table.isEmpty() || selected.contains(part.table))).count();
        int completed = 0;
        for (String type : Arrays.asList("TABLE", "DATA", "INDEX", "CONSTRAINT", "VIEW", "PROCEDURE", "FUNCTION", "PACKAGE", "PACKAGE_BODY", "TRIGGER")) {
            if (!options.enabled(type)) continue;
            for (Section part : parsed.sections) {
                if (!type.equals(part.type) || !part.table.isEmpty() && !selected.contains(part.table)) continue;
                if ("DATA".equals(type)) imported += tableFiles.importFile(target, schema, part.table, "SQL", part.sql.getBytes(StandardCharsets.UTF_8));
                else {
                    String ddl = part.sql.trim();
                    if ("TABLE".equals(type) && !parsed.schema.equals(schema)) {
                        String original = "CREATE TABLE " + MigrationDialect.qualified(parsed.schema, part.table);
                        if (!ddl.startsWith(original)) throw new BusinessException("建表 SQL 与所选表不匹配");
                        ddl = "CREATE TABLE " + MigrationDialect.qualified(schema, part.table) + ddl.substring(original.length());
                    }
                    if (Arrays.asList("TABLE", "INDEX", "CONSTRAINT", "VIEW").contains(type)) ddl = ddl.replaceFirst("(?s);\\s*$", "");
                    try (Connection connection = connections.open(target); Statement statement = connection.createStatement()) {
                        statement.execute(ddl);
                    } catch (SQLException error) { throw new BusinessException("执行 " + type + " " + part.name + " 失败：" + MigrationConnections.safeError(error)); }
                }
                progress.update((double) ++completed / Math.max(1, totalSections), "已处理 " + completed + "/" + totalSections + " 个 SQL 段");
            }
        }
        return imported;
    }

    private static Parsed parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_SCRIPT_BYTES) throw new BusinessException("SQL 文件不能超过 200 MiB");
        String content = new String(bytes, StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
        int first = content.indexOf('\n'), second = first < 0 ? -1 : content.indexOf('\n', first + 1), third = second < 0 ? -1 : content.indexOf('\n', second + 1);
        if (first < 0 || second < 0 || third < 0 || !MAGIC.equals(content.substring(0, first))
                || !content.substring(first + 1, second).startsWith("-- SCHEMA ")
                || !content.substring(second + 1, third).startsWith("-- SOURCE ")) throw new BusinessException("请选择由本系统模式导出生成的 SQL 文件");
        Parsed parsed = new Parsed(); parsed.schema = decode(content.substring(first + 11, second)); parsed.sourceType = content.substring(second + 11, third);
        MigrationDialect.quote(parsed.schema);
        if (!Arrays.asList("DM", "ORACLE").contains(parsed.sourceType)) throw new BusinessException("SQL 文件来源数据库类型无效");
        for (int position = third + 1; position < content.length();) {
            int endLine = content.indexOf('\n', position);
            if (endLine < 0) break;
            String markerLine = content.substring(position, endLine);
            if (markerLine.isEmpty()) { position = endLine + 1; continue; }
            if (!markerLine.startsWith("-- JDDBMS-BEGIN ")) throw new BusinessException("SQL 文件包含未标记的语句");
            String[] marker = markerLine.substring("-- JDDBMS-BEGIN ".length()).split(" ", -1);
            if (marker.length != 4) throw new BusinessException("SQL 文件对象标记无效");
            Section section = new Section(); section.type = marker[0]; section.table = decode(marker[1]); section.name = decode(marker[2]);
            if (!Arrays.asList("TABLE", "DATA", "INDEX", "CONSTRAINT", "VIEW", "PROCEDURE", "FUNCTION", "PACKAGE", "PACKAGE_BODY", "TRIGGER").contains(section.type)) throw new BusinessException("SQL 文件对象类型无效");
            if (!section.table.isEmpty()) MigrationDialect.quote(section.table); MigrationDialect.quote(section.name);
            int length;
            try { length = Integer.parseInt(marker[3]); } catch (NumberFormatException error) { throw new BusinessException("SQL 文件对象长度无效"); }
            position = endLine + 1;
            if (length < 1 || length > MAX_SCRIPT_BYTES || (long) position + length > content.length()) throw new BusinessException("SQL 文件对象内容不完整");
            section.sql = content.substring(position, position + length); position += length;
            String ending = "\n-- JDDBMS-END\n";
            if (!content.startsWith(ending, position)) throw new BusinessException("SQL 文件对象结束标记无效");
            position += ending.length(); parsed.sections.add(section);
        }
        if (parsed.sections.isEmpty()) throw new BusinessException("SQL 文件中没有对象");
        return parsed;
    }

    private static String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String decode(String value) { try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); } catch (Exception error) { throw new BusinessException("SQL 文件对象标记无效"); } }
    private static void validateSelection(List<String> selected) {
        if (selected == null || selected.isEmpty() || selected.size() > MAX_TABLES) throw new BusinessException("请选择 1～10000 张表");
        Set<String> unique = new HashSet<>(); for (String name : selected) if (!unique.add(name)) throw new BusinessException("表选择重复：" + name); else MigrationDialect.quote(name);
    }

    public static class Options {
        public boolean dataRows, indexes, constraints, triggers, views, procedures;
        public static Options fullExport() {
            Options options = new Options();
            options.dataRows = true; options.indexes = true; options.constraints = true;
            options.triggers = true; options.views = true; options.procedures = true;
            return options;
        }
        void validate() { }
        boolean enabled(String type) {
            switch (type) {
                case "TABLE": return true; case "DATA": return dataRows; case "INDEX": return indexes;
                case "CONSTRAINT": return constraints; case "TRIGGER": return triggers; case "VIEW": return views;
                case "PROCEDURE": case "FUNCTION": case "PACKAGE": case "PACKAGE_BODY": return procedures; default: return false;
            }
        }
    }
    @FunctionalInterface public interface Progress { void update(double fraction, String message); }
    public static class Preview { public String schema, sourceType; public List<String> tables; }
    private static class Parsed { String schema, sourceType; List<Section> sections = new ArrayList<>(); }
    private static class Section { String type, table, name, sql; }
}
