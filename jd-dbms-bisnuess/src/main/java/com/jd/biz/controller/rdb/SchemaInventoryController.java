package com.jd.biz.controller.rdb;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.spi.sql.Chat2DBContext;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only schema inventory used by cross-connection structural comparison. */
@ConnectionInfoAspect
@RestController
@RequestMapping("/api/rdb/schema-inventory")
public class SchemaInventoryController {
    private static final int MAX_ROWS = 20000;

    @GetMapping
    public DataResult<Map<String, Object>> inventory(@Valid Query query) {
        if (StringUtils.isBlank(query.getSchemaName())) throw new BusinessException("请选择模式");
        Connection connection = Chat2DBContext.getConnection();
        Map<String, String> definitions = new LinkedHashMap<>();
        Map<String, Map<String, String>> attributes = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        List<String> truncated = new ArrayList<>();
        collect(connection, "INDEX", "SELECT I.TABLE_NAME,I.INDEX_NAME,I.UNIQUENESS,I.INDEX_TYPE,I.STATUS,C.COLUMN_NAME,C.DESCEND,E.COLUMN_EXPRESSION,I.GENERATED "
                + "FROM ALL_INDEXES I LEFT JOIN ALL_IND_COLUMNS C ON C.INDEX_OWNER=I.OWNER AND C.INDEX_NAME=I.INDEX_NAME "
                + "LEFT JOIN ALL_IND_EXPRESSIONS E ON E.INDEX_OWNER=I.OWNER AND E.INDEX_NAME=I.INDEX_NAME AND E.COLUMN_POSITION=C.COLUMN_POSITION "
                + "WHERE I.TABLE_OWNER=?" + tableFilter(query, "I") + " ORDER BY I.TABLE_NAME,I.INDEX_NAME,C.COLUMN_POSITION",
                query, definitions, attributes, errors, truncated, 2);
        loadMissingIndexExpressions(connection, query, attributes, errors);
        collect(connection, "CONSTRAINT", "SELECT C.TABLE_NAME,C.CONSTRAINT_NAME,C.CONSTRAINT_TYPE,C.STATUS,C.SEARCH_CONDITION,"
                + "CASE WHEN C.R_OWNER=C.OWNER THEN '<CURRENT_SCHEMA>' ELSE C.R_OWNER END AS REFERENCE_OWNER,"
                + "R.TABLE_NAME AS REFERENCE_TABLE,C.DELETE_RULE,C.DEFERRABLE,C.DEFERRED,C.VALIDATED,"
                + "K.COLUMN_NAME,RK.COLUMN_NAME AS REFERENCE_COLUMN,C.GENERATED "
                + "FROM ALL_CONSTRAINTS C LEFT JOIN ALL_CONS_COLUMNS K ON K.OWNER=C.OWNER AND K.CONSTRAINT_NAME=C.CONSTRAINT_NAME "
                + "LEFT JOIN ALL_CONSTRAINTS R ON R.OWNER=C.R_OWNER AND R.CONSTRAINT_NAME=C.R_CONSTRAINT_NAME "
                + "LEFT JOIN ALL_CONS_COLUMNS RK ON RK.OWNER=R.OWNER AND RK.CONSTRAINT_NAME=R.CONSTRAINT_NAME AND RK.POSITION=K.POSITION "
                + "WHERE C.OWNER=?" + tableFilter(query, "C") + " ORDER BY C.TABLE_NAME,C.CONSTRAINT_NAME,K.POSITION",
                query, definitions, attributes, errors, truncated, 2);
        if (StringUtils.isBlank(query.getTableName())) {
            collect(connection, "VIEW", "SELECT VIEW_NAME,TEXT FROM ALL_VIEWS WHERE OWNER=? ORDER BY VIEW_NAME",
                    query, definitions, attributes, errors, truncated, 1);
        }
        String triggerType = "DM".equalsIgnoreCase(Chat2DBContext.getConnectInfo().getDbType()) ? "TRIGGERING_TYPE" : "TRIGGER_TYPE";
        collect(connection, "TRIGGER", "SELECT TABLE_NAME,TRIGGER_NAME," + triggerType
                + ",TRIGGERING_EVENT,STATUS,TRIGGER_BODY FROM ALL_TRIGGERS WHERE OWNER=?" + tableFilter(query, "ALL_TRIGGERS") + " ORDER BY TRIGGER_NAME",
                query, definitions, attributes, errors, truncated, 2);
        if (StringUtils.isBlank(query.getTableName())) {
            collect(connection, "PROGRAM", "SELECT TYPE,NAME,LINE,TEXT FROM ALL_SOURCE WHERE OWNER=? "
                    + "AND TYPE IN ('PROCEDURE','FUNCTION','PACKAGE','PACKAGE BODY') ORDER BY TYPE,NAME,LINE",
                    query, definitions, attributes, errors, truncated, 2);
            collect(connection, "SEQUENCE", "SELECT SEQUENCE_NAME,MIN_VALUE,MAX_VALUE,INCREMENT_BY,CYCLE_FLAG,CACHE_SIZE "
                    + "FROM ALL_SEQUENCES WHERE SEQUENCE_OWNER=? ORDER BY SEQUENCE_NAME",
                    query, definitions, attributes, errors, truncated, 1);
            collect(connection, "SYNONYM", "SELECT SYNONYM_NAME,TABLE_OWNER,TABLE_NAME,DB_LINK "
                    + "FROM ALL_SYNONYMS WHERE OWNER=? ORDER BY SYNONYM_NAME",
                    query, definitions, attributes, errors, truncated, 1);
        }
        List<Map<String, Object>> objects = new ArrayList<>();
        for (Map.Entry<String, String> entry : definitions.entrySet()) {
            String[] key = entry.getKey().split("\\|", 3);
            Map<String, Object> object = new LinkedHashMap<>();
            object.put("kind", key[0]);
            object.put("name", key.length == 3 ? key[1] + "." + key[2] : key[1]);
            object.put("tableName", key.length == 3 && !"PROGRAM".equals(key[0]) ? key[1] : null);
            object.put("objectName", key.length == 3 ? key[2] : key[1]);
            if (attributes.containsKey(entry.getKey())) {
                Map<String, String> fields = attributes.get(entry.getKey());
                object.put("attributes", fields);
                object.put("generated", "Y".equals(fields.get("GENERATED[1]")) || "GENERATED NAME".equals(fields.get("GENERATED[1]")));
                StringBuilder canonical = new StringBuilder();
                fields.forEach((field, value) -> canonical.append(field).append('=')
                        .append(value == null ? "-1:" : value.length() + ":" + value).append('\n'));
                entry.setValue(canonical.toString());
            }
            object.put("fingerprint", fingerprint(entry.getValue()));
            object.put("definition", entry.getValue().length() > 2000 ? entry.getValue().substring(0, 2000) + "…" : entry.getValue());
            objects.add(object);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objects", objects);
        result.put("errors", errors);
        result.put("truncated", truncated);
        return DataResult.of(result);
    }

    private String tableFilter(Query query, String alias) {
        return StringUtils.isBlank(query.getTableName()) ? "" : " AND " + alias + ".TABLE_NAME=?";
    }

    private void loadMissingIndexExpressions(Connection connection, Query request,
            Map<String, Map<String, String>> attributes, Map<String, String> errors) {
        for (Map.Entry<String, Map<String, String>> entry : attributes.entrySet()) {
            Map<String, String> fields = entry.getValue();
            if (!StringUtils.contains(fields.get("INDEX_TYPE[1]"), "FUNCTION-BASED")) continue;
            boolean missing = fields.entrySet().stream().anyMatch(field -> field.getKey().startsWith("COLUMN_EXPRESSION[") && field.getValue() == null);
            if (!missing) continue;
            String[] key = entry.getKey().split("\\|", 3);
            try (PreparedStatement statement = connection.prepareStatement("SELECT DBMS_METADATA.GET_DDL('INDEX',INDEX_NAME,OWNER) "
                    + "FROM ALL_INDEXES WHERE TABLE_OWNER=? AND TABLE_NAME=? AND INDEX_NAME=?")) {
                statement.setString(1, request.getSchemaName());
                statement.setString(2, key[1]);
                statement.setString(3, key[2]);
                statement.setQueryTimeout(15);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("索引定义不存在");
                    List<String> expressions = indexExpressions(result.getString(1));
                    long count = fields.keySet().stream().filter(field -> field.startsWith("COLUMN_EXPRESSION[")).count();
                    if (expressions.size() != count) throw new SQLException("索引表达式读取不完整");
                    for (int i = 0; i < expressions.size(); i++) {
                        fields.put("COLUMN_EXPRESSION[" + (i + 1) + "]", expressions.get(i));
                        fields.put("COLUMN_NAME[" + (i + 1) + "]", null);
                    }
                }
            } catch (Exception error) {
                errors.put("INDEX", "函数索引表达式无法完整读取，请核对索引定义及读取权限");
            }
        }
    }

    static List<String> indexExpressions(String ddl) {
        String normalized = ddl.replaceFirst("(?i)^\\s*CREATE\\s+OR\\s+REPLACE\\s+", "CREATE ");
        // Parse the index clause; DM's physical STORAGE/WITHOUT options follow it.
        SQLCreateIndexStatement statement = SQLParserUtils.createSQLStatementParser(normalized, DbType.dm).parseCreateIndex(true);
        List<String> expressions = new ArrayList<>();
        statement.getItems().forEach(item -> expressions.add(item.getExpr().toString()));
        return expressions;
    }

    private void collect(Connection connection, String kind, String sql, Query request,
                         Map<String, String> definitions, Map<String, Map<String, String>> attributes, Map<String, String> errors,
                         List<String> truncated, int keyColumns) {
        Map<String, StringBuilder> group = new LinkedHashMap<>();
        Map<String, Map<String, String>> details = new LinkedHashMap<>();
        Map<String, Integer> positions = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, request.getSchemaName());
            if (StringUtils.isNotBlank(request.getTableName())) query.setString(2, request.getTableName());
            query.setQueryTimeout(15);
            query.setMaxRows(MAX_ROWS + 1);
            try (ResultSet rows = query.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    if (++count > MAX_ROWS) { truncated.add(kind); break; }
                    StringBuilder key = new StringBuilder(kind);
                    for (int i = 1; i <= keyColumns; i++) key.append('|').append(rows.getString(i));
                    StringBuilder definition = group.computeIfAbsent(key.toString(), ignored -> new StringBuilder());
                    int position = positions.merge(key.toString(), 1, Integer::sum);
                    Map<String, String> fields = details.computeIfAbsent(key.toString(), ignored -> new LinkedHashMap<>());
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int i = keyColumns + 1; i <= rows.getMetaData().getColumnCount(); i++) {
                        values.put(rows.getMetaData().getColumnLabel(i), rows.getString(i));
                    }
                    if ("INDEX".equals(kind) && values.get("COLUMN_EXPRESSION") != null) values.put("COLUMN_NAME", null);
                    if ("CONSTRAINT".equals(kind) && "R".equals(values.get("CONSTRAINT_TYPE")) &&
                            (values.get("REFERENCE_TABLE") == null || values.get("REFERENCE_COLUMN") == null)) {
                        errors.put(kind, "外键引用结构无法完整读取，请核对引用对象的可见权限");
                    }
                    for (Map.Entry<String, String> valueEntry : values.entrySet()) {
                        String value = valueEntry.getValue();
                        definition.append(value == null ? "-1:" : value.length() + ":" + value).append('|');
                        if ("INDEX".equals(kind) || "CONSTRAINT".equals(kind)) {
                            fields.put(valueEntry.getKey() + "[" + position + "]", value);
                        }
                    }
                    definition.append('\n');
                }
            }
            for (Map.Entry<String, StringBuilder> entry : group.entrySet()) definitions.put(entry.getKey(), entry.getValue().toString());
            if ("INDEX".equals(kind) || "CONSTRAINT".equals(kind)) attributes.putAll(details);
        } catch (SQLException error) {
            errors.put(kind, error.getMessage());
        }
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    @Data
    public static class Query implements DataSourceBaseRequestInfo {
        @NotNull private Long dataSourceId;
        private String databaseName;
        private String schemaName;
        private String tableName;
    }
}
