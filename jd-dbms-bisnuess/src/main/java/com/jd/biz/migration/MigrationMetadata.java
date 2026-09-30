package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.jd.common.tools.base.excption.BusinessException;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.Reader;
import java.sql.*;
import java.util.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

@Component
public class MigrationMetadata {
    public List<Map<String, String>> schemas(Connection connection) throws SQLException {
        List<Map<String, String>> output = new ArrayList<>();
        try (ResultSet rows = connection.getMetaData().getSchemas()) {
            while (rows.next()) {
                String name = rows.getString("TABLE_SCHEM");
                if (name != null && !systemSchema(name)) output.add(Collections.singletonMap("name", name));
            }
        }
        output.sort(Comparator.comparing(row -> row.get("name")));
        return output;
    }

    public Map<String, Object> objects(Connection connection, String schema, String search, int page, int size) throws SQLException {
        quote(schema);
        if (systemSchema(schema)) throw new BusinessException("系统模式不允许迁移");
        if (page < 1 || page > 10000 || size < 1 || size > 200) throw new BusinessException("对象分页范围无效");
        List<Map<String, String>> output = new ArrayList<>();
        long offset = (long) (page - 1) * size, total = 0;
        String sql = "SELECT OBJECT_NAME, OBJECT_TYPE FROM ALL_OBJECTS WHERE OWNER = ? "
                + "AND OBJECT_TYPE IN ('TABLE','VIEW','PROCEDURE','FUNCTION','PACKAGE','TRIGGER','SEQUENCE','SYNONYM','MATERIALIZED VIEW') "
                + "ORDER BY OBJECT_NAME, OBJECT_TYPE";
        try (PreparedStatement statement = prepare(connection, sql, schema); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString(1), type = rows.getString(2);
                if (search != null && !name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) continue;
                if (total >= offset && output.size() < size) {
                    Map<String, String> row = new LinkedHashMap<>(); row.put("name", name); row.put("type", type); output.add(row);
                }
                total++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(); result.put("data", output); result.put("total", total); return result;
    }

    public Assessment assess(Connection source, Connection target, Config config, String sourceType, String targetType) throws SQLException {
        validate(config, true);
        if (sourceType.equals(targetType)) {
            Set<String> sourceNames = new HashSet<>(); config.tables.forEach(table -> sourceNames.add(qualified(sourceSchema(config, table), table.source)));
            if (config.tables.stream().anyMatch(table -> sourceNames.contains(qualified(targetSchema(config, table), table.target)))) {
                throw new BusinessException("同类型数据库在同名模式中存在源目标表重叠，首版不能可靠排除同一物理对象；请使用独立目标模式或目标表名");
            }
        }
        Assessment report = new Assessment();
        report.direction = sourceType + " → " + targetType;
        report.sourceVersion = source.getMetaData().getDatabaseProductVersion();
        report.targetVersion = target.getMetaData().getDatabaseProductVersion();
        report.sourceDriver = source.getMetaData().getDriverVersion(); report.targetDriver = target.getMetaData().getDriverVersion();
        report.warnings.add("全量离线：源端数据与结构冻结，目标对象禁止业务并发写入。");
        report.warnings.add("复制逻辑结构、普通索引和已适配约束；不复制物理存储、用户和授权。非表对象的目标 DDL 必须逐个人工确认。");
        report.warnings.add("取消不撤销已提交数据及 DDL；中断后不自动续传或重复执行。");
        report.warnings.add("预检查不执行试写；目标写入权限、页大小和字符集限制仍可能在执行时阻断。DATE 映射为 TIMESTAMP(0)。");
        if (highRisk(config)) report.warnings.add("存在高风险单表策略：必须逐表审阅并备份目标。清空/删除 DDL 可能隐式提交；不自动回滚、级联删除或关闭约束。追加的数据必须与原目标内容合并校验。");
        Set<String> schemas = new HashSet<>();
        for (Map<String, String> row : schemas(target)) schemas.add(row.get("name"));
        for (Mapping mapping : config.tables) {
            TablePlan table = new TablePlan(); table.source = mapping.source; table.target = mapping.target; report.tables.add(table);
            table.sourceSchema = sourceSchema(config, mapping); table.targetSchema = targetSchema(config, mapping); table.type = mapping.type;
            table.scope = scope(config, mapping); table.conflict = conflict(config, mapping); table.filters = mapping.filters;
            table.targetTablespace = mapping.targetTablespace == null || mapping.targetTablespace.isEmpty() ? config.targetTablespace : mapping.targetTablespace;
            if (table.targetTablespace != null && table.targetTablespace.isEmpty()) table.targetTablespace = null;
            if ("TABLE".equals(table.type)) table.dependencies.addAll(mapping.dependencies);
            String from = table.sourceSchema, to = table.targetSchema;
            try {
                if (!schemas.contains(to)) throw new BusinessException("目标业务模式不存在或当前账号不可见，请预先准备模式和权限");
                if (!mapping.type.equals(objectType(source, from, table.source))) throw new BusinessException("对象不存在或类型已经变化");
                table.sourceDdl = sourceDdl(source, from, table.source, table.type);
                table.targetExists = objectType(target, to, table.target) != null;
                if (!"TABLE".equals(table.type)) {
                    MigrationObjects.assess(source, config, mapping, table);
                    if (table.targetExists && "SKIP".equals(table.conflict)) table.skipped = true;
                    else if (table.targetExists) throw new BusinessException("目标已有同名对象，不允许替换");
                    if (!table.skipped) MigrationObjects.assertCreatePrivilege(target, table, targetType);
                    continue;
                }
                if (table.sourceDdl.toUpperCase(Locale.ROOT).matches("(?s).*\\b(PARTITION\\s+BY|IDENTITY|AUTO_INCREMENT|VIRTUAL|INVISIBLE|GENERATED|ENCRYPT|ORGANIZATION\\s+(INDEX|EXTERNAL)|NESTED\\s+TABLE|GLOBAL\\s+TEMPORARY|ON\\s+COMMIT)\\b.*")) {
                    throw new BusinessException("检测到分区、自增/生成列、特殊表或加密特性，需要专项转换，不能降级为普通表");
                }
                table.columns = columns(source, from, table.source, sourceType, targetType, mapping.columns, false);
                if (table.columns.isEmpty()) throw new BusinessException("没有读取到字段，不能生成空表");
                applyFields(mapping, table);
                for (Column column : table.columns) if (column.sourceExpression != null) {
                    report.warnings.add(qualified(from, table.source) + "." + column.name + "：显式转换 " + column.sourceExpression + " → " + column.targetType + "；全量校验转换后的值，不代表原始类型无损或完整保留对象属性。");
                    if (table.filters.stream().anyMatch(filter -> column.name.equals(filter.column))) throw new BusinessException("转换字段不能同时使用源筛选，请改用其他原始字段筛选");
                }
                MigrationFilter.predicate(table);
                if (!table.filters.isEmpty()) report.warnings.add(qualified(from, table.source) + "：只迁移 AND 筛选内数据，校验及恢复使用相同范围；外键依赖数据仍需完整，筛选值属于敏感配置。");
                primaryKeys(source, from, table);
                if (!"DATA".equals(table.scope)) {
                    MigrationConstraints.assess(source, config, table, sourceType);
                    try (PreparedStatement triggers = prepare(source, "SELECT TRIGGER_NAME, OWNER FROM ALL_TRIGGERS WHERE TABLE_OWNER = ? AND TABLE_NAME = ?", from, table.source);
                         ResultSet rows = triggers.executeQuery()) {
                        while (rows.next()) {
                            String trigger = rows.getString(1), owner = rows.getString(2);
                            if (config.tables.stream().noneMatch(m -> "TRIGGER".equals(m.type) && trigger.equals(m.source) && owner.equals(sourceSchema(config, m))))
                                throw new BusinessException("源表存在未选择的触发器（含禁用状态），请加入人工转换清单，不能静默丢弃");
                        }
                    }
                    table.targetDdl = createTable(to, table);
                }
                if (table.targetTablespace != null) {
                    if ("DATA".equals(table.scope)) throw new BusinessException("仅数据迁移不移动既有表空间，请清空表空间映射");
                    if (tablespaces(target).stream().noneMatch(row -> table.targetTablespace.equals(row.get("name")))) throw new BusinessException("目标表空间不存在、非永久/可写或当前用户不可见");
                }
                comments(source, from, to, table);
                if ("DATA".equals(table.scope)) {
                    if (!table.targetExists || !"TABLE".equals(objectType(target, to, table.target))) {
                        throw new BusinessException("仅数据迁移要求目标普通表已存在");
                    }
                    List<Column> targetColumns = columns(target, to, table.target, targetType, targetType);
                    table.targetDdl = sourceDdl(target, to, table.target);
                    if (table.targetDdl.toUpperCase(Locale.ROOT).matches("(?s).*\\b(IDENTITY|AUTO_INCREMENT|GENERATED|VIRTUAL)\\b.*")) {
                        throw new BusinessException("目标存在生成列或自增特性，仅数据迁移尚未适配");
                    }
                    compatible(table.columns, targetColumns);
                    if (!Arrays.asList("APPEND", "CLEAR").contains(table.conflict)) assertEmpty(target, to, table.target);
                    assertNoTriggers(target, to, table.target);
                } else if (table.targetExists) {
                    if ("SKIP".equals(table.conflict)) table.skipped = true;
                    else if ("REBUILD".equals(table.conflict) && "TABLE".equals(objectType(target, to, table.target))) {
                        try (PreparedStatement references = prepare(target, "SELECT C.OWNER, C.TABLE_NAME FROM ALL_CONSTRAINTS C JOIN ALL_CONSTRAINTS P ON C.R_OWNER=P.OWNER AND C.R_CONSTRAINT_NAME=P.CONSTRAINT_NAME WHERE P.OWNER=? AND P.TABLE_NAME=? AND C.CONSTRAINT_TYPE='R'", to, table.target); ResultSet rows = references.executeQuery()) {
                            while (rows.next()) if (!to.equals(rows.getString(1)) || !table.target.equals(rows.getString(2))) throw new BusinessException("目标表被其他表的外键引用，删除重建阻断；不级联删除，请先人工处理依赖");
                        }
                        table.rebuild = true; table.previousTargetDdl = sourceDdl(target, to, table.target);
                    }
                    else throw new BusinessException("目标已有同名对象，默认阻断；不会删除或清空");
                }
                if (!"DATA".equals(table.scope) && !table.skipped) MigrationObjects.assertCreatePrivilege(target, table, targetType);
                assertReadable(source, from, table);
            } catch (Exception e) { table.blockers.add(MigrationConnections.safeError(e)); }
        }
        MigrationObjects.order(report);
        report.fingerprint = hash(JSON.toJSONString(config) + JSON.toJSONString(report));
        report.checkedAt = System.currentTimeMillis();
        return report;
    }

    static void applyFields(Mapping mapping, TablePlan table) {
        for (FieldMapping field : mapping.columns) {
            Column column = table.columns.stream().filter(c -> c.name.equals(field.source)).findFirst()
                    .orElseThrow(() -> new BusinessException("字段映射引用不存在的源字段"));
            column.targetName = field.target;
        }
        Set<String> targets = new HashSet<>();
        for (Column column : table.columns) if (!targets.add(targetName(column))) throw new BusinessException("目标字段名称重复");
    }

    public List<Column> columns(Connection connection, String schema, String table, String sourceType, String targetType) throws SQLException {
        return columns(connection, schema, table, sourceType, targetType, Collections.emptyList(), false);
    }

    public List<Column> editableColumns(Connection connection, String schema, String table, String sourceType) throws SQLException {
        return columns(connection, schema, table, sourceType, sourceType, Collections.emptyList(), true);
    }

    private List<Column> columns(Connection connection, String schema, String table, String sourceType, String targetType, List<FieldMapping> mappings, boolean raw) throws SQLException {
        List<Column> output = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection,
                "SELECT * FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ? ORDER BY COLUMN_ID", schema, table);
             ResultSet rows = statement.executeQuery()) {
            Set<String> fields = new HashSet<>();
            for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) fields.add(rows.getMetaData().getColumnLabel(i).toUpperCase(Locale.ROOT));
            while (rows.next()) {
                Column column = new Column();
                column.defaultValue = rows.getString("DATA_DEFAULT");
                column.name = rows.getString("COLUMN_NAME"); column.sourceType = rows.getString("DATA_TYPE");
                column.precision = integer(rows, "DATA_PRECISION"); column.scale = integer(rows, "DATA_SCALE");
                column.nullable = "Y".equalsIgnoreCase(rows.getString("NULLABLE"));
                String unit = fields.contains("CHAR_USED") ? rows.getString("CHAR_USED") : null;
                column.unit = "C".equals(unit) ? "CHAR" : "B".equals(unit) ? "BYTE" : null;
                boolean national = column.sourceType.toUpperCase(Locale.ROOT).matches("NVARCHAR2?|NCHAR");
                if ((national || "CHAR".equals(column.unit)) && !fields.contains("CHAR_LENGTH")) {
                    throw new BusinessException("无法读取字符长度语义，不能猜测字符与字节大小");
                }
                column.length = rows.getInt(national || "CHAR".equals(column.unit) ? "CHAR_LENGTH" : "DATA_LENGTH");
                if (fields.contains("VIRTUAL_COLUMN") && "YES".equalsIgnoreCase(rows.getString("VIRTUAL_COLUMN"))) throw new BusinessException("虚拟列尚未适配");
                FieldMapping mapping = mappings.stream().filter(field -> column.name.equals(field.source)).findFirst().orElse(null);
                try { MigrationConversion.apply(column, mapping, sourceType, targetType); }
                catch (BusinessException error) { if (!raw) throw error; column.kind = "UNSUPPORTED"; column.targetType = null; }
                output.add(column);
            }
        }
        return output;
    }

    public List<Map<String, String>> tablespaces(Connection connection) throws SQLException {
        List<Map<String, String>> output = new ArrayList<>();
        try (PreparedStatement query = prepare(connection, "SELECT TABLESPACE_NAME FROM USER_TABLESPACES WHERE STATUS='ONLINE' AND CONTENTS='PERMANENT' ORDER BY TABLESPACE_NAME"); ResultSet rows = query.executeQuery()) {
            while (rows.next()) { String name = rows.getString(1); if (!Arrays.asList("SYSTEM", "SYSAUX", "ROLL").contains(name)) output.add(Collections.singletonMap("name", name)); }
        }
        return output;
    }

    static void compatible(List<Column> source, List<Column> target) {
        if (source.size() != target.size()) throw new BusinessException("目标字段数量不兼容");
        Map<String, Column> byName = new HashMap<>(); target.forEach(c -> byName.put(c.name, c));
        for (Column column : source) {
            Column actual = byName.get(targetName(column));
            if (actual == null || !column.targetType.equals(actual.targetType) || (column.nullable && !actual.nullable)) {
                throw new BusinessException("目标字段 " + column.name + " 类型、长度、精度或空值语义不兼容");
            }
        }
    }

    public void verifyCreated(Connection target, Config config, TablePlan planned, String targetType) throws SQLException {
        config = scoped(config, planned);
        List<Column> actual = columns(target, config.targetSchema, planned.target, targetType, targetType);
        compatible(planned.columns, actual);
        if (planned.targetTablespace != null) try (PreparedStatement query = prepare(target, "SELECT TABLESPACE_NAME FROM ALL_TABLES WHERE OWNER=? AND TABLE_NAME=?", config.targetSchema, planned.target); ResultSet rows = query.executeQuery()) {
            if (!rows.next() || !planned.targetTablespace.equals(rows.getString(1))) throw new BusinessException("目标实际表空间与计划不一致");
        }
        Map<String, Column> fields = new HashMap<>(); actual.forEach(c -> fields.put(c.name, c));
        for (Column column : planned.columns) {
            Column found = fields.get(targetName(column));
            String expectedDefault = column.defaultValue == null ? "" : column.defaultValue.trim();
            String foundDefault = found.defaultValue == null ? "" : found.defaultValue.trim();
            if (column.nullable != found.nullable || !expectedDefault.equals(foundDefault)) {
                throw new BusinessException("目标字段默认值或空值定义不一致，需要人工复核");
            }
        }
        TablePlan checked = new TablePlan(); checked.source = planned.target; checked.target = planned.target; checked.columns = actual;
        primaryKeys(target, config.targetSchema, checked);
        if (!planned.primaryKey.stream().map(n -> mappedName(planned, n)).collect(java.util.stream.Collectors.toList()).equals(checked.primaryKey)) throw new BusinessException("目标主键定义不一致");
        comments(target, config.targetSchema, config.targetSchema, checked);
        if (!Objects.equals(planned.tableComment, checked.tableComment)) throw new BusinessException("目标表注释不一致");
        for (Column column : planned.columns) {
            if (!Objects.equals(column.comment, fields.get(targetName(column)).comment)) throw new BusinessException("目标字段注释不一致");
        }
    }

    private void primaryKeys(Connection connection, String schema, TablePlan table) throws SQLException {
        TreeMap<Integer, String> names = new TreeMap<>();
        try (ResultSet rows = connection.getMetaData().getPrimaryKeys(null, schema, table.source)) {
            while (rows.next()) names.put(rows.getInt("KEY_SEQ"), rows.getString("COLUMN_NAME"));
        }
        table.primaryKey.addAll(names.values());
    }

    private void comments(Connection connection, String sourceSchema, String targetSchema, TablePlan table) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER = ? AND TABLE_NAME = ?", sourceSchema, table.source);
             ResultSet rows = statement.executeQuery()) {
            if (rows.next()) {
                table.tableComment = rows.getString(1);
                if (table.tableComment != null) table.afterDdl.add("COMMENT ON TABLE " + qualified(targetSchema, table.target) + " IS " + literal(table.tableComment));
            }
        }
        try (PreparedStatement statement = prepare(connection,
                "SELECT COLUMN_NAME, COMMENTS FROM ALL_COL_COMMENTS WHERE OWNER = ? AND TABLE_NAME = ?", sourceSchema, table.source);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString(1), comment = rows.getString(2);
                table.columns.stream().filter(c -> c.name.equals(name)).forEach(c -> c.comment = comment);
                if (comment != null) table.afterDdl.add("COMMENT ON COLUMN " + qualified(targetSchema, table.target) + "." + quote(mappedName(table, name)) + " IS " + literal(comment));
            }
        }
    }

    static void assertEmpty(Connection connection, String schema, String table) throws SQLException {
        try (PreparedStatement statement = prepare(connection, "SELECT 1 FROM " + qualified(schema, table) + " WHERE ROWNUM <= 1");
             ResultSet rows = statement.executeQuery()) {
            if (rows.next()) throw new BusinessException("目标表不为空；当前策略要求兼容空目标，请明确选择并确认其他策略");
        }
    }

    static void assertNoTriggers(Connection connection, String schema, String table) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                "SELECT STATUS FROM ALL_TRIGGERS WHERE TABLE_OWNER = ? AND TABLE_NAME = ?", schema, table);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) if (MigrationObjects.triggerEnabled(rows.getString(1))) throw new BusinessException("目标存在启用触发器，可能产生副作用；要求人工审核处理");
        }
    }

    private void assertReadable(Connection connection, String schema, TablePlan table) throws SQLException {
        String filter = MigrationFilter.predicate(table);
        try (PreparedStatement statement = prepare(connection, "SELECT " + MigrationTransfer.selectColumns(table.columns) + " FROM " + qualified(schema, table.source) + " JD_SRC WHERE 1 = 0" + (filter.isEmpty() ? "" : " AND " + filter))) {
            MigrationFilter.bind(statement, table, 1);
            try (ResultSet ignored = statement.executeQuery()) { }
        }
    }

    static String objectType(Connection connection, String schema, String name) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                "SELECT OBJECT_TYPE FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_NAME = ? AND OBJECT_TYPE NOT IN ('INDEX','PACKAGE BODY')", schema, name);
             ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
    }

    String sourceDdl(Connection connection, String schema, String table) throws SQLException, IOException {
        return sourceDdl(connection, schema, table, "TABLE");
    }

    public String sourceDdl(Connection connection, String schema, String table, String type) throws SQLException, IOException {
        if (!Arrays.asList("TABLE", "VIEW", "SEQUENCE", "SYNONYM", "PROCEDURE", "FUNCTION", "PACKAGE", "TRIGGER").contains(type)) throw new BusinessException("对象定义类型不支持");
        if ("PACKAGE".equals(type)) {
            Map<String, StringBuilder> parts = new LinkedHashMap<>();
            try (PreparedStatement query = prepare(connection, "SELECT TYPE, TEXT FROM ALL_SOURCE WHERE OWNER=? AND NAME=? AND TYPE IN ('PACKAGE','PACKAGE BODY') ORDER BY TYPE, LINE", schema, table); ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    StringBuilder part = parts.computeIfAbsent(rows.getString(1), key -> new StringBuilder());
                    if (part.length() > 1024 * 1024) throw new BusinessException("源包定义超过 1MB 限制"); part.append(rows.getString(2));
                }
            }
            if (!parts.containsKey("PACKAGE")) throw new BusinessException("无法读取包规范");
            return parts.entrySet().stream().map(e -> { String text = e.getValue().toString().trim(); return text.toUpperCase(Locale.ROOT).startsWith("CREATE ") ? text : "CREATE " + text; }).collect(java.util.stream.Collectors.joining("\n/\n"));
        }
        try (PreparedStatement statement = prepare(connection, "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) FROM DUAL", type, table, schema);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw new BusinessException("无法读取源表 DDL");
            try (Reader reader = rows.getCharacterStream(1)) {
                if (reader == null) throw new BusinessException("源表 DDL 为空");
                StringBuilder text = new StringBuilder(); char[] buffer = new char[4096]; int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (text.length() + count > 1024 * 1024) throw new BusinessException("源 DDL 超出首版评估大小范围");
                    text.append(buffer, 0, count);
                }
                return text.toString();
            }
        }
    }

    static Integer integer(ResultSet rows, String column) throws SQLException { int value = rows.getInt(column); return rows.wasNull() ? null : value; }
    static PreparedStatement prepare(Connection connection, String sql, String... args) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try { statement.setQueryTimeout(300); for (int i = 0; i < args.length; i++) statement.setString(i + 1, args[i]); return statement; }
        catch (SQLException e) { statement.close(); throw e; }
    }
}
