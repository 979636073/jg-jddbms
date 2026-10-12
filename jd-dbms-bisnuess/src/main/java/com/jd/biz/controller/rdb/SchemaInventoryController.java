package com.jd.biz.controller.rdb;

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
        Map<String, String> errors = new LinkedHashMap<>();
        List<String> truncated = new ArrayList<>();
        String schema = query.getSchemaName();
        collect(connection, "INDEX", "SELECT I.TABLE_NAME,I.INDEX_NAME,I.UNIQUENESS,I.INDEX_TYPE,C.COLUMN_NAME,C.DESCEND "
                + "FROM ALL_INDEXES I LEFT JOIN ALL_IND_COLUMNS C ON C.INDEX_OWNER=I.OWNER AND C.INDEX_NAME=I.INDEX_NAME "
                + "WHERE I.TABLE_OWNER=? ORDER BY I.TABLE_NAME,I.INDEX_NAME,C.COLUMN_POSITION", schema, definitions, errors, truncated, 2);
        collect(connection, "CONSTRAINT", "SELECT C.TABLE_NAME,C.CONSTRAINT_NAME,C.CONSTRAINT_TYPE,C.STATUS,C.SEARCH_CONDITION,"
                + "C.R_OWNER,C.R_CONSTRAINT_NAME,C.DELETE_RULE,K.COLUMN_NAME "
                + "FROM ALL_CONSTRAINTS C LEFT JOIN ALL_CONS_COLUMNS K ON K.OWNER=C.OWNER AND K.CONSTRAINT_NAME=C.CONSTRAINT_NAME "
                + "WHERE C.OWNER=? ORDER BY C.TABLE_NAME,C.CONSTRAINT_NAME,K.POSITION", schema, definitions, errors, truncated, 2);
        collect(connection, "VIEW", "SELECT VIEW_NAME,TEXT FROM ALL_VIEWS WHERE OWNER=? ORDER BY VIEW_NAME",
                schema, definitions, errors, truncated, 1);
        String triggerType = "DM".equalsIgnoreCase(Chat2DBContext.getConnectInfo().getDbType()) ? "TRIGGERING_TYPE" : "TRIGGER_TYPE";
        collect(connection, "TRIGGER", "SELECT TABLE_NAME,TRIGGER_NAME," + triggerType
                + ",TRIGGERING_EVENT,STATUS,TRIGGER_BODY FROM ALL_TRIGGERS WHERE OWNER=? ORDER BY TRIGGER_NAME",
                schema, definitions, errors, truncated, 2);
        collect(connection, "PROGRAM", "SELECT TYPE,NAME,LINE,TEXT FROM ALL_SOURCE WHERE OWNER=? "
                + "AND TYPE IN ('PROCEDURE','FUNCTION','PACKAGE','PACKAGE BODY') ORDER BY TYPE,NAME,LINE",
                schema, definitions, errors, truncated, 2);
        collect(connection, "SEQUENCE", "SELECT SEQUENCE_NAME,MIN_VALUE,MAX_VALUE,INCREMENT_BY,CYCLE_FLAG,CACHE_SIZE "
                + "FROM ALL_SEQUENCES WHERE SEQUENCE_OWNER=? ORDER BY SEQUENCE_NAME",
                schema, definitions, errors, truncated, 1);
        collect(connection, "SYNONYM", "SELECT SYNONYM_NAME,TABLE_OWNER,TABLE_NAME,DB_LINK "
                + "FROM ALL_SYNONYMS WHERE OWNER=? ORDER BY SYNONYM_NAME",
                schema, definitions, errors, truncated, 1);
        List<Map<String, String>> objects = new ArrayList<>();
        for (Map.Entry<String, String> entry : definitions.entrySet()) {
            String[] key = entry.getKey().split("\\|", 3);
            Map<String, String> object = new LinkedHashMap<>();
            object.put("kind", key[0]);
            object.put("name", key.length == 3 ? key[1] + "." + key[2] : key[1]);
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

    private void collect(Connection connection, String kind, String sql, String schema,
                         Map<String, String> definitions, Map<String, String> errors,
                         List<String> truncated, int keyColumns) {
        Map<String, StringBuilder> group = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, schema);
            query.setQueryTimeout(15);
            query.setMaxRows(MAX_ROWS + 1);
            try (ResultSet rows = query.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    if (++count > MAX_ROWS) { truncated.add(kind); break; }
                    StringBuilder key = new StringBuilder(kind);
                    for (int i = 1; i <= keyColumns; i++) key.append('|').append(rows.getString(i));
                    StringBuilder definition = group.computeIfAbsent(key.toString(), ignored -> new StringBuilder());
                    for (int i = keyColumns + 1; i <= rows.getMetaData().getColumnCount(); i++) {
                        String value = rows.getString(i);
                        definition.append(value == null ? "<NULL>" : value.trim()).append('|');
                    }
                    definition.append('\n');
                }
            }
            for (Map.Entry<String, StringBuilder> entry : group.entrySet()) definitions.put(entry.getKey(), entry.getValue().toString());
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
    }
}
