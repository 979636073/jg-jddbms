package com.jd.biz.migration;

import com.jd.common.tools.base.excption.BusinessException;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;
import static com.jd.biz.migration.MigrationMetadata.prepare;

final class MigrationConstraints {
    static void assess(Connection source, Config config, TablePlan plan, String sourceType) throws SQLException {
        Set<String> implicitIndexes = new HashSet<>();
        try (PreparedStatement query = prepare(source, "SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE, SEARCH_CONDITION, INDEX_NAME, STATUS, DEFERRABLE, VALIDATED, R_OWNER, R_CONSTRAINT_NAME, DELETE_RULE FROM ALL_CONSTRAINTS WHERE OWNER=? AND TABLE_NAME=? ORDER BY CONSTRAINT_NAME", plan.sourceSchema, plan.source);
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                String expression = rows.getString("SEARCH_CONDITION"), type = rows.getString("CONSTRAINT_TYPE"), name = rows.getString("CONSTRAINT_NAME");
                if ("P".equals(type) || "U".equals(type)) implicitIndexes.add(rows.getString("INDEX_NAME"));
                validateConstraint(rows.getString("STATUS"), rows.getString("DEFERRABLE"), rows.getString("VALIDATED"));
                if ("C".equals(type) && expression != null && expression.trim().matches("(?is)\\(?\\s*\"(?:[^\"]|\"\")+\"\\s+IS\\s+NOT\\s+NULL\\s*\\)?")) continue;
                if ("P".equals(type)) continue;
                DdlOperation operation = new DdlOperation(); operation.name = generated(plan, name); operation.type = type;
                operation.columns = columnNames(source, plan.sourceSchema, name, false).stream().map(n -> mappedName(plan, n)).collect(Collectors.toList());
                String definition;
                if ("U".equals(type)) definition = "UNIQUE (" + names(operation.columns) + ")";
                else if ("C".equals(type)) { operation.expression = checkExpression(expression, plan); definition = "CHECK (" + operation.expression + ")"; }
                else if ("R".equals(type)) {
                    operation.foreignKey = true;
                    String owner = rows.getString("R_OWNER"), reference = rows.getString("R_CONSTRAINT_NAME"), sourceTable;
                    try (PreparedStatement find = prepare(source, "SELECT TABLE_NAME FROM ALL_CONSTRAINTS WHERE OWNER=? AND CONSTRAINT_NAME=?", owner, reference); ResultSet found = find.executeQuery()) {
                        if (!found.next()) throw new BusinessException("外键依赖不可见"); sourceTable = found.getString(1);
                    }
                    Mapping mapping = config.tables.stream().filter(m -> "TABLE".equals(m.type) && sourceSchema(config, m).equals(owner) && m.source.equals(sourceTable)).findFirst()
                            .orElseThrow(() -> new BusinessException("外键依赖表未选择，请一并选择依赖表"));
                    operation.referenceSchema = targetSchema(config, mapping); operation.referenceTable = mapping.target;
                    for (String column : columnNames(source, owner, reference, false)) operation.referenceColumns.add(mapping.columns.stream().filter(c -> c.source.equals(column)).map(c -> c.target).findFirst().orElse(column));
                    operation.deleteRule = rows.getString("DELETE_RULE");
                    if (!Arrays.asList("NO ACTION", "CASCADE", "SET NULL").contains(operation.deleteRule)) throw new BusinessException("外键删除规则尚未适配");
                    definition = "FOREIGN KEY (" + names(operation.columns) + ") REFERENCES " + qualified(operation.referenceSchema, operation.referenceTable)
                            + " (" + names(operation.referenceColumns) + ")" + ("NO ACTION".equals(operation.deleteRule) ? "" : " ON DELETE " + operation.deleteRule);
                } else throw new BusinessException("约束类型尚未适配");
                operation.sql = "ALTER TABLE " + qualified(plan.targetSchema, plan.target) + " ADD CONSTRAINT " + quote(operation.name) + " " + definition;
                plan.postDdl.add(operation);
            }
        }
        try (PreparedStatement query = prepare(source, "SELECT INDEX_NAME, INDEX_TYPE, UNIQUENESS, STATUS, VISIBILITY FROM ALL_INDEXES WHERE TABLE_OWNER=? AND TABLE_NAME=? ORDER BY INDEX_NAME", plan.sourceSchema, plan.source); ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString(1), type = rows.getString(2);
                if (implicitIndexes.contains(name) || ("ORACLE".equals(sourceType) && "LOB".equals(type))) continue;
                List<String> sourceColumns = columnNames(source, plan.sourceSchema, name, true);
                if ("DM".equals(sourceType) && "CLUSTER".equals(type) && sourceColumns.isEmpty()) continue;
                // Ordinary DM foreign keys create an empty virtual index; it is recreated by the FK, not copied as a B-tree.
                if ("DM".equals(sourceType) && "VIRTUAL".equals(type) && plan.postDdl.stream().anyMatch(o -> o.foreignKey && o.columns.equals(sourceColumns.stream().map(n -> mappedName(plan, n)).collect(Collectors.toList())))) continue;
                if (!"NORMAL".equals(type)) throw new BusinessException("索引 " + name + "（" + type + "）尚未适配，不能降级为普通索引");
                validateIndex(rows.getString("STATUS"), rows.getString("VISIBILITY"));
                DdlOperation operation = new DdlOperation(); operation.name = generated(plan, name); operation.type = "INDEX";
                operation.unique = "UNIQUE".equals(rows.getString(3));
                for (String column : sourceColumns) operation.columns.add(mappedName(plan, column));
                if (operation.columns.isEmpty()) throw new BusinessException("无法读取索引字段");
                operation.sql = "CREATE " + (operation.unique ? "UNIQUE " : "") + "INDEX " + qualified(plan.targetSchema, operation.name) + " ON " + qualified(plan.targetSchema, plan.target) + " (" + names(operation.columns) + ")";
                plan.postDdl.add(operation);
            }
        }
    }

    static void validateConstraint(String status, String deferrable, String validated) {
        if (!"ENABLED".equals(status) || !"NOT DEFERRABLE".equals(deferrable) || !"VALIDATED".equals(validated)) throw new BusinessException("禁用、可延迟或未全量验证的约束尚未适配，不能改变约束状态");
    }

    static void validateIndex(String status, String visibility) {
        if (!"VALID".equals(status) || !"VISIBLE".equals(visibility)) throw new BusinessException("失效或不可见索引尚未适配，不能改变索引状态");
    }

    private static String generated(TablePlan plan, String name) { return "JD_M_" + hash(qualified(plan.targetSchema, plan.target) + ":" + name).substring(0, 24).toUpperCase(Locale.ROOT); }

    static List<String> columnNames(Connection connection, String schema, String name, boolean index) throws SQLException {
        List<String> columns = new ArrayList<>();
        String sql = index ? "SELECT COLUMN_NAME, DESCEND FROM ALL_IND_COLUMNS WHERE INDEX_OWNER=? AND INDEX_NAME=? ORDER BY COLUMN_POSITION"
                : "SELECT COLUMN_NAME FROM ALL_CONS_COLUMNS WHERE OWNER=? AND CONSTRAINT_NAME=? ORDER BY POSITION";
        try (PreparedStatement query = prepare(connection, sql, schema, name); ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                if (index && !"ASC".equals(rows.getString(2))) throw new BusinessException("降序/函数索引需专项适配，不能静默改变排序");
                columns.add(rows.getString(1));
            }
        }
        return columns;
    }

    static String checkExpression(String text, TablePlan plan) {
        if (text == null) throw new BusinessException("无法读取检查约束表达式");
        Pattern tokens = Pattern.compile("'(?:[^']|'')*'|\"(?:[^\"]|\"\")*\"|[\\p{L}_$#][\\p{L}\\p{N}_$#]*|\\d+(?:\\.\\d+)?|[\\s()<>=!+*/%,.-]");
        Matcher token = tokens.matcher(text); StringBuilder output = new StringBuilder(); int end = 0;
        while (token.find()) {
            if (token.start() != end) throw new BusinessException("检查约束包含未适配表达式");
            String value = token.group(); end = token.end();
            if (value.startsWith("\"")) value = quote(mappedName(plan, value.substring(1, value.length() - 1).replace("\"\"", "\"")));
            else if (Character.isLetter(value.charAt(0)) || "_$#".indexOf(value.charAt(0)) >= 0) {
                String upper = value.toUpperCase(Locale.ROOT);
                if (!Arrays.asList("IS", "NOT", "NULL", "AND", "OR", "BETWEEN", "IN", "LIKE").contains(upper)) value = quote(mappedName(plan, upper));
                else value = upper;
            }
            output.append(value);
        }
        if (end != text.length()) throw new BusinessException("检查约束包含未适配表达式");
        return output.toString();
    }

    static void verify(Connection target, TablePlan plan, DdlOperation operation) throws SQLException {
        String schema = plan.targetSchema;
        if ("INDEX".equals(operation.type)) {
            try (PreparedStatement query = prepare(target, "SELECT UNIQUENESS FROM ALL_INDEXES WHERE OWNER=? AND INDEX_NAME=? AND TABLE_NAME=?", schema, operation.name, plan.target); ResultSet rows = query.executeQuery()) {
                if (!rows.next() || operation.unique != "UNIQUE".equals(rows.getString(1))) throw new BusinessException("目标索引定义不一致");
            }
        } else {
            try (PreparedStatement query = prepare(target, "SELECT CONSTRAINT_TYPE, STATUS, SEARCH_CONDITION, R_OWNER, R_CONSTRAINT_NAME, DELETE_RULE FROM ALL_CONSTRAINTS WHERE OWNER=? AND CONSTRAINT_NAME=? AND TABLE_NAME=?", schema, operation.name, plan.target); ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new BusinessException("目标约束不存在");
                String expression = rows.getString("SEARCH_CONDITION");
                if (!operation.type.equals(rows.getString("CONSTRAINT_TYPE")) || !"ENABLED".equals(rows.getString("STATUS"))) throw new BusinessException("目标约束类型或状态不一致");
                if (operation.expression != null && !compact(operation.expression).equals(compact(expression))) throw new BusinessException("目标检查约束表达式不一致");
                if (operation.foreignKey) {
                    String owner = rows.getString("R_OWNER"), reference = rows.getString("R_CONSTRAINT_NAME");
                    if (!operation.referenceSchema.equals(owner) || !Objects.equals(operation.deleteRule, rows.getString("DELETE_RULE")) || !operation.referenceColumns.equals(columnNames(target, owner, reference, false))) throw new BusinessException("目标外键引用或删除规则不一致");
                    try (PreparedStatement find = prepare(target, "SELECT TABLE_NAME FROM ALL_CONSTRAINTS WHERE OWNER=? AND CONSTRAINT_NAME=?", owner, reference); ResultSet found = find.executeQuery()) {
                        if (!found.next() || !operation.referenceTable.equals(found.getString(1))) throw new BusinessException("目标外键引用表不一致");
                    }
                }
            }
        }
        if (!"C".equals(operation.type) && !operation.columns.equals(columnNames(target, schema, operation.name, "INDEX".equals(operation.type)))) throw new BusinessException("目标索引或约束字段映射不一致");
    }

    static boolean exists(Connection target, TablePlan plan, DdlOperation operation) throws SQLException {
        String sql = "INDEX".equals(operation.type) ? "SELECT 1 FROM ALL_INDEXES WHERE OWNER=? AND INDEX_NAME=?"
                : "SELECT 1 FROM ALL_CONSTRAINTS WHERE OWNER=? AND CONSTRAINT_NAME=?";
        try (PreparedStatement query = prepare(target, sql, plan.targetSchema, operation.name); ResultSet rows = query.executeQuery()) { return rows.next(); }
    }

    private static String compact(String expression) {
        if (expression == null) return "";
        Matcher token = Pattern.compile("'(?:[^']|'')*'|\"(?:[^\"]|\"\")*\"|\\S+").matcher(expression);
        StringBuilder output = new StringBuilder(); while (token.find()) output.append(token.group()); return output.toString();
    }
}
