package com.jd.plugin.dm;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.plugin.dm.builder.DMSqlBuilder;
import com.jd.plugin.dm.type.DMColumnDefaultEnum;
import com.jd.plugin.dm.type.DMColumnTypeEnum;
import com.jd.plugin.dm.type.DMDefaultValueEnum;
import com.jd.plugin.dm.type.DMIndexTypeEnum;
import com.jd.spi.MetaData;
import com.jd.spi.SqlBuilder;
import com.jd.spi.jdbc.DefaultMetaService;
import com.jd.spi.model.*;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.sql.SQLExecutor;
import com.jd.spi.util.SortUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import javax.validation.constraints.NotEmpty;
import java.io.File;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DMMetaData extends DefaultMetaService implements MetaData {

    private static final Logger log = LoggerFactory.getLogger(DMMetaData.class);
    private static final int RELATION_QUERY_TIMEOUT_SECONDS = 15;

    private List<String> systemSchemas = Arrays.asList("CTISYS", "SYS", "SYSDBA", "SYSSSO", "SYSAUDITOR");

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        return SortUtils.sortSchema(schemas, systemSchemas);
    }

    private String format(String tableName) {
        return "\"" + tableName.replace("\"", "\"\"") + "\"";
    }

    private static String sqlLiteral(String value) {
        return value.replace("'", "''");
    }

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = "SELECT t.TABLE_NAME, c.COMMENTS FROM ALL_TABLES t " +
                "LEFT JOIN ALL_TAB_COMMENTS c ON c.OWNER = t.OWNER AND c.TABLE_NAME = t.TABLE_NAME " +
                "AND c.TABLE_TYPE = 'TABLE' WHERE t.OWNER = ? " +
                "AND t.TABLE_NAME NOT LIKE 'MTAB$%' AND t.TABLE_NAME NOT LIKE 'SREF_CON_TAB%' " +
                "AND t.TABLE_NAME NOT LIKE 'BIN$%'" + (tableName == null ? "" : " AND t.TABLE_NAME = ?") +
                " ORDER BY t.TABLE_NAME";
        List<Table> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schemaName);
            if (tableName != null) statement.setString(2, tableName);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Table table = new Table();
                    table.setName(rows.getString("TABLE_NAME"));
                    table.setComment(rows.getString("COMMENTS"));
                    table.setSchemaName(schemaName);
                    table.setDatabaseName(databaseName);
                    tables.add(table);
                }
            }
            return tables;
        } catch (SQLException e) {
            throw new BusinessException("读取表目录失败: " + e.getMessage());
        }
    }

    @Override
    public PageResult<Table> tableDetailsPage(Connection connection, String databaseName, String schemaName,
                                              String searchKey, int pageNo, int pageSize) {
        String filter = StringUtils.isBlank(searchKey) ? "" :
                " AND (INSTR(UPPER(t.TABLE_NAME), ?) > 0 OR INSTR(UPPER(c.COMMENTS), ?) > 0)";
        String from = " FROM ALL_TABLES t LEFT JOIN ALL_TAB_COMMENTS c " +
                "ON c.OWNER = t.OWNER AND c.TABLE_NAME = t.TABLE_NAME AND c.TABLE_TYPE = 'TABLE' " +
                "WHERE t.OWNER = ?" + filter;
        String pattern = StringUtils.isBlank(searchKey) ? null : searchKey.trim().toUpperCase(Locale.ROOT);
        try {
            long total;
            String countSql = pattern == null
                    ? "SELECT COUNT(*) FROM ALL_TABLES t WHERE t.OWNER = ?"
                    : "SELECT COUNT(*)" + from;
            try (PreparedStatement count = connection.prepareStatement(countSql)) {
                bindTablePageFilter(count, schemaName, pattern);
                try (ResultSet rows = count.executeQuery()) {
                    rows.next();
                    total = rows.getLong(1);
                }
            }
            List<Table> tables = new ArrayList<>();
            String sql = "SELECT t.TABLE_NAME, t.OWNER, t.TABLESPACE_NAME, t.LAST_ANALYZED, " +
                    "t.NUM_ROWS, c.COMMENTS, o.CREATED, o.LAST_DDL_TIME" + from.replace("WHERE t.OWNER", 
                    "LEFT JOIN ALL_OBJECTS o ON o.OWNER = t.OWNER AND o.OBJECT_NAME = t.TABLE_NAME " +
                    "AND o.OBJECT_TYPE = 'TABLE' WHERE t.OWNER") +
                    " ORDER BY t.TABLE_NAME LIMIT ? OFFSET ?";
            try (PreparedStatement page = connection.prepareStatement(sql)) {
                int next = bindTablePageFilter(page, schemaName, pattern);
                page.setInt(next++, pageSize);
                page.setLong(next, (long) (pageNo - 1) * pageSize);
                try (ResultSet rows = page.executeQuery()) {
                    while (rows.next()) {
                        Table table = new Table();
                        TableDetails details = new TableDetails();
                        String name = rows.getString("TABLE_NAME");
                        table.setName(name);
                        table.setSchemaName(rows.getString("OWNER"));
                        table.setDatabaseName(databaseName);
                        table.setComment(rows.getString("COMMENTS"));
                        details.setName(name);
                        details.setSchema(table.getSchemaName());
                        details.setDataBaseName(databaseName);
                        details.setTableSpace(rows.getString("TABLESPACE_NAME"));
                        details.setLastAnalyzed(rows.getString("LAST_ANALYZED"));
                        details.setNumRows(rows.getString("NUM_ROWS"));
                        details.setComment(table.getComment());
                        details.setCreated(rows.getString("CREATED"));
                        details.setLastDDL(rows.getString("LAST_DDL_TIME"));
                        table.setTableDetails(details);
                        tables.add(table);
                    }
                }
            }
            return PageResult.of(tables, total, pageNo, pageSize);
        } catch (SQLException e) {
            throw new BusinessException("table.page.query.failed", new Object[]{e.getMessage()});
        }
    }

    private static int bindTablePageFilter(PreparedStatement statement, String schemaName, String pattern)
            throws SQLException {
        int next = 1;
        statement.setString(next++, schemaName);
        if (pattern != null) {
            statement.setString(next++, pattern);
            statement.setString(next++, pattern);
        }
        return next;
    }

    static String buildStandaloneIndexDdlSql(String schemaName, String tableName) {
        return "SELECT DBMS_METADATA.GET_DDL('INDEX', i.INDEX_NAME, i.TABLE_OWNER) AS DDL " +
                "FROM ALL_INDEXES i WHERE i.TABLE_OWNER = '" + sqlLiteral(schemaName) + "' " +
                "AND i.TABLE_NAME = '" + sqlLiteral(tableName) + "' " +
                "AND i.INDEX_TYPE NOT IN ('CLUSTER', 'VIRTUAL') " +
                "AND NOT EXISTS (SELECT 1 FROM ALL_CONSTRAINTS c " +
                "WHERE c.OWNER = i.TABLE_OWNER AND c.TABLE_NAME = i.TABLE_NAME " +
                "AND c.INDEX_NAME = i.INDEX_NAME AND c.CONSTRAINT_TYPE IN ('P', 'U')) " +
                "ORDER BY i.INDEX_NAME";
    }

    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = " SELECT " +
                " (SELECT comments FROM all_tab_comments WHERE table_name = '%s' and owner='%s') AS comments, " +
                " (SELECT dbms_metadata.get_ddl('TABLE', '%s', '%s') FROM dual) AS ddl " +
                " FROM dual; ";
        String selectObjectDDLSQL = String.format(sql, sqlLiteral(tableName), sqlLiteral(schemaName),
                sqlLiteral(tableName), sqlLiteral(schemaName));
        String tableSql = SQLExecutor.getInstance().execute(connection, selectObjectDDLSQL, resultSet -> {
            String ddl = "";
            try {
                while (resultSet.next()) {
                    ddl = resultSet.getString("ddl");
                    String comment = resultSet.getString("comments");
                    if (StringUtils.isNotBlank(comment)) {
                        return ddl + "\n" + "COMMENT ON TABLE " + format(schemaName) + "." + format(tableName) +
                                " IS " + "'" + sqlLiteral(comment) + "';";
                    }
                }
                return ddl;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        String columnCommentsSql = " SELECT " +
                " ACC.SCHEMA_NAME, " +
                " ATC.TABLE_NAME                  , " +
                " ATC.COLUMN_ID                   , " +
                " ATC.COLUMN_NAME  , " +
                " ATC.DATA_TYPE    , " +
                " ATC.DATA_LENGTH  , " +
                " ATC.DATA_TYPE_MOD               , " +
                " ATC.NULLABLE                    , " +
                " ATC.DATA_DEFAULT, " +
                " ACC.COMMENTS                    , " +
                " ATC.DATA_PRECISION              , " +
                " ATC.DATA_SCALE                  , " +
                " ATC.CHAR_USED " +
                " FROM " +
                " ALL_TAB_COLUMNS ATC, " +
                " ALL_COL_COMMENTS ACC " +
                " WHERE " +
                " ATC.OWNER       = ACC.OWNER " +
                " AND ATC.TABLE_NAME  = ACC.TABLE_NAME " +
                " AND ATC.COLUMN_NAME = ACC.COLUMN_NAME " +
                " AND ACC.SCHEMA_NAME = '%s' " +
                " AND ACC.TABLE_NAME  = '%s' " +
                " ORDER BY ATC.COLUMN_ID ";
        String columnCommentsDDLSQL = String.format(columnCommentsSql, sqlLiteral(schemaName), sqlLiteral(tableName));
        String columnSql = SQLExecutor.getInstance().execute(connection, columnCommentsDDLSQL, resultSet -> {
            try {
                String ddl = "";
                while (resultSet.next()) {
                    String owner = resultSet.getString("SCHEMA_NAME");
                    String table = resultSet.getString("TABLE_NAME");
                    String columnName = resultSet.getString("COLUMN_NAME");
                    String comments = resultSet.getString("COMMENTS");
                    if (StringUtils.isNotBlank(comments)) {
                        ddl += "\n" + "COMMENT ON COLUMN " + format(owner) + "." + format(table) +
                                "." + format(columnName) + " IS " + "'" + sqlLiteral(comments) + "';";
                    }
                }
                return ddl;
            } catch (SQLException e) {
                return "";
            }
        });
        String indexSql = SQLExecutor.getInstance().execute(connection,
                buildStandaloneIndexDdlSql(schemaName, tableName), resultSet -> {
                    StringBuilder ddl = new StringBuilder();
                    while (resultSet.next()) {
                        String definition = resultSet.getString("DDL");
                        if (StringUtils.isNotBlank(definition)) {
                            ddl.append("\n").append(definition.trim());
                        }
                    }
                    return ddl.toString();
                });
        return tableSql + columnSql + indexSql;
    }

    private static String ROUTINES_SQL
            = "SELECT OWNER, NAME, TEXT FROM ALL_SOURCE WHERE TYPE = '%s' AND OWNER = '%s' AND NAME = '%s' ORDER BY LINE";

    private static final String FUNCTIONS_SQL
            = "SELECT OWNER, OBJECT_NAME FROM ALL_OBJECTS WHERE OBJECT_TYPE = 'FUNCTION'";

    static String buildFunctionsSql(String schemaName) {
        String sql = FUNCTIONS_SQL;
        if (StringUtils.isNotBlank(schemaName)) {
            sql += " AND OWNER = '" + schemaName.replace("'", "''") + "'";
        }
        return sql + " ORDER BY OWNER, OBJECT_NAME";
    }

    static String buildFunctionDetailSql(String schemaName, String functionName) {
        return String.format(ROUTINES_SQL, "FUNCTION", schemaName, functionName);
    }

    @Override
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        return SQLExecutor.getInstance().execute(connection, buildFunctionsSql(schemaName), resultSet -> {
            List<Function> functions = new ArrayList<>();
            while (resultSet.next()) {
                Function function = new Function();
                function.setDatabaseName(databaseName);
                function.setSchemaName(resultSet.getString("OWNER"));
                function.setFunctionName(resultSet.getString("OBJECT_NAME"));
                function.setSpecificName(resultSet.getString("OBJECT_NAME"));
                functions.add(function);
            }
            return functions;
        });
    }

    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {

        String sql = buildFunctionDetailSql(schemaName, functionName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString("TEXT") + "\n");
            }
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            function.setFunctionBody(sb.toString());
            return function;
        });
    }

    static String buildProcedureListSql(String schemaName) {
        String sql = "SELECT OWNER, OBJECT_NAME FROM ALL_OBJECTS WHERE OBJECT_TYPE = 'PROCEDURE'";
        if (StringUtils.isNotBlank(schemaName)) {
            sql += " AND OWNER = '" + schemaName.replace("'", "''") + "'";
        }
        return sql + " ORDER BY OWNER, OBJECT_NAME";
    }

    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        return SQLExecutor.getInstance().execute(connection, buildProcedureListSql(schemaName), resultSet -> {
            List<Procedure> procedures = new ArrayList<>();
            while (resultSet.next()) {
                Procedure procedure = new Procedure();
                procedure.setDatabaseName(databaseName);
                procedure.setSchemaName(resultSet.getString("OWNER"));
                procedure.setProcedureName(resultSet.getString("OBJECT_NAME"));
                procedures.add(procedure);
            }
            return procedures;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = buildProcedureDetailSql(schemaName, procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString("TEXT") + "\n");
            }
            Procedure procedure = new Procedure();
            procedure.setDatabaseName(databaseName);
            procedure.setSchemaName(schemaName);
            procedure.setProcedureName(procedureName);
            procedure.setProcedureBody(sb.toString());
            return procedure;
        });
    }

    static String buildProcedureDetailSql(String schemaName, String procedureName) {
        return String.format(ROUTINES_SQL, "PROCEDURE", schemaName.replace("'", "''"),
                procedureName.replace("'", "''"));
    }

    private static String TRIGGER_SQL
            = "SELECT OWNER, TRIGGER_NAME, TABLE_OWNER, TABLE_NAME, TRIGGERING_TYPE, TRIGGERING_EVENT, STATUS, TRIGGER_BODY "
            + "FROM ALL_TRIGGERS WHERE OWNER = '%s' AND TRIGGER_NAME = '%s'";

    static String buildTriggerListSql(String schemaName, String tableName) {
        String sql = "SELECT OWNER, TRIGGER_NAME, TABLE_OWNER, TABLE_NAME, TRIGGERING_TYPE, TRIGGERING_EVENT, STATUS, TRIGGER_BODY FROM ALL_TRIGGERS WHERE OWNER = '"
                + schemaName.replace("'", "''") + "'";
        if (StringUtils.isNotBlank(tableName)) {
            sql += " AND TABLE_NAME = '" + tableName.replace("'", "''") + "'";
        }
        return sql + " ORDER BY TRIGGER_NAME";
    }

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName, String tableName) {
        List<Trigger> triggers = new ArrayList<>();
        String sql = buildTriggerListSql(schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                Trigger trigger = new Trigger();
                trigger.setTriggerName(resultSet.getString("TRIGGER_NAME"));
                trigger.setSchemaName(schemaName);
                trigger.setDatabaseName(databaseName);
                trigger.setTriggerBody(resultSet.getString("TRIGGER_BODY"));
                trigger.setType(resultSet.getString("TRIGGERING_TYPE"));
                trigger.setStatus(resultSet.getString("STATUS"));
                trigger.setEvent(resultSet.getString("TRIGGERING_EVENT"));
                triggers.add(trigger);
            }
            return triggers;
        });
    }

    @Override
    public String columnDefault(Connection connection, String name) {
        String defaultValue = DMColumnDefaultEnum.getByName(name) == null ? "" : DMColumnDefaultEnum.getByName(name).getDefaultValue();
        return defaultValue;
    }

    private static final String TABLE_DATA_COUNT_SQL = "SELECT\n" +
            "        A.OWNER     ,\n" +
            "        A.TABLE_NAME,\n" +
            "        TABLE_ROWCOUNT(A.OWNER, A.TABLE_NAME) AS TABLE_ROWS\n" +
            "FROM\n" +
            "        ALL_TABLES A\n" +
            "WHERE\n" +
            "        A.OWNER = '%s'";

    @Override
    public List<TableDetails> rowCount(Connection connection, String databaseName, String schemaName) {
        List<TableDetails> tableDetails = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(TABLE_DATA_COUNT_SQL, schemaName), resultSet -> {
            while (resultSet.next()) {
                TableDetails details = new TableDetails();
                details.setNumRows(resultSet.getString("TABLE_ROWS"));
                details.setName(resultSet.getString("TABLE_NAME"));
                details.setSchema(resultSet.getString("OWNER"));
                tableDetails.add(details);
            }
            return tableDetails;
        });
    }


    private static String getCheckSQl = "SELECT CONSTRAINT_NAME, SEARCH_CONDITION, STATUS FROM ALL_CONSTRAINTS WHERE OWNER = '%s' AND TABLE_NAME = '%s' AND CONSTRAINT_TYPE = 'C'";

    public Set<TableIndex> getCheckSQl(Connection connection, String schemaName, String tableName) {
        return SQLExecutor.getInstance().execute(connection, String.format(getCheckSQl, schemaName, tableName), resultSet -> {
            Set<TableIndex> name = new HashSet<>();
            List<String> value = new ArrayList<>();
            while (resultSet.next()) {
                String search_condition = resultSet.getString("SEARCH_CONDITION");
                if (!value.contains(search_condition)) {
                    value.add(search_condition);
                    TableIndex index = new TableIndex();
                    index.setConstraintsDesc(search_condition);
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(resultSet.getString("CONSTRAINT_NAME"));
                    index.setStatus("ENABLED".equals(resultSet.getString("STATUS")) ? "VALID": "INVALID");
                    name.add(index);
                }
            }
            return name;
        });
    }

    private static String getObjectRule = "SELECT GRANTEE,OWNER,TABLE_NAME,GRANTOR,PRIVILEGE,GRANTABLE FROM ALL_TAB_PRIVS WHERE GRANTEE = '%s' AND TABLE_NAME = '%s' AND OWNER = '%s'";
    @Override
    public List<TableObjectRoleData> userObjectRoles(Connection connection, String userName) {
        String sql = "SELECT GRANTEE,OWNER,TABLE_NAME,PRIVILEGE,GRANTABLE FROM ALL_TAB_PRIVS WHERE GRANTEE = '"
                + userName.replace("'", "''") + "' ORDER BY OWNER,TABLE_NAME,PRIVILEGE";
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            List<TableObjectRoleData> list = new ArrayList<>();
            while (resultSet.next()) {
                TableObjectRoleData privilege = new TableObjectRoleData();
                privilege.setUserName(resultSet.getString("GRANTEE"));
                privilege.setOwner(resultSet.getString("OWNER"));
                privilege.setTableName(resultSet.getString("TABLE_NAME"));
                privilege.setDesc(resultSet.getString("PRIVILEGE"));
                privilege.setRule(true);
                privilege.setToRule("YES".equalsIgnoreCase(resultSet.getString("GRANTABLE")));
                list.add(privilege);
            }
            return list;
        });
    }

    @Override
    public List<TableObjectRoleData> allObjectRole(Connection connection, String userName, String schemaName, String tableName) {
        String sql = String.format(getObjectRule, userName.replace("'", "''"), tableName.replace("'", "''"), schemaName.replace("'", "''"));
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            List<TableObjectRoleData> list = new ArrayList<>();
            while (resultSet.next()) {
                TableObjectRoleData tableObjectRoleData = new TableObjectRoleData();
                tableObjectRoleData.setDesc(resultSet.getString("PRIVILEGE"));
                tableObjectRoleData.setUserName(resultSet.getString("GRANTEE"));
                tableObjectRoleData.setOwner(resultSet.getString("OWNER"));
                tableObjectRoleData.setTableName(resultSet.getString("TABLE_NAME"));
                tableObjectRoleData.setRule(true);
                tableObjectRoleData.setToRule("YES".equals(resultSet.getString("GRANTABLE")));
                list.add(tableObjectRoleData);
            }
            return list;
        });
    }

    @Override
    public Map<String, List<TableObjectRoleData>> allObjectRoles(Connection connection, String userName,
                                                                  String schemaName, List<String> tableNames) {
        Map<String, List<TableObjectRoleData>> result = new LinkedHashMap<>();
        for (String name : tableNames) {
            result.put(name, new ArrayList<>());
        }
        if (result.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", Collections.nCopies(result.size(), "?"));
        String sql = "SELECT GRANTEE,OWNER,TABLE_NAME,PRIVILEGE,GRANTABLE FROM ALL_TAB_PRIVS " +
                "WHERE GRANTEE = ? AND OWNER = ? AND TABLE_NAME IN (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userName);
            statement.setString(2, schemaName);
            int index = 3;
            for (String name : result.keySet()) {
                statement.setString(index++, name);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    TableObjectRoleData role = new TableObjectRoleData();
                    role.setDesc(rows.getString("PRIVILEGE"));
                    role.setUserName(rows.getString("GRANTEE"));
                    role.setOwner(rows.getString("OWNER"));
                    role.setTableName(rows.getString("TABLE_NAME"));
                    role.setRule(true);
                    role.setToRule("YES".equalsIgnoreCase(rows.getString("GRANTABLE")));
                    List<TableObjectRoleData> roles = result.get(role.getTableName());
                    if (roles != null) {
                        roles.add(role);
                    }
                }
            }
        } catch (SQLException e) {
            throw new BusinessException("user.objectRole.query.failed", new Object[]{e.getMessage()});
        }
        return result;
    }

    @Override
    public Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName,
                           String triggerName) {

        String sql = String.format(TRIGGER_SQL, schemaName, triggerName);
        Trigger detail = SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            if (resultSet.next()) {
                trigger.setType(resultSet.getString("TRIGGERING_TYPE"));
                trigger.setStatus(resultSet.getString("STATUS"));
                trigger.setEvent(resultSet.getString("TRIGGERING_EVENT"));
            }
            return trigger;
        });
        String ddl = SQLExecutor.getInstance().execute(connection, buildTriggerDdlSql(schemaName, triggerName),
                resultSet -> resultSet.next() ? resultSet.getString("DDL") : null);
        detail.setTriggerBody(ddl);
        return detail;
    }

    static String buildTriggerDdlSql(String schemaName, String triggerName) {
        return "SELECT DBMS_METADATA.GET_DDL('TRIGGER', '" + triggerName.replace("'", "''")
                + "', '" + schemaName.replace("'", "''") + "') AS DDL FROM DUAL";
    }

    private static String VIEW_SQL
            = "SELECT OWNER, VIEW_NAME, TEXT FROM ALL_VIEWS WHERE OWNER = '%s' AND VIEW_NAME = '%s'";

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_SQL, schemaName, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            while (resultSet.next()) {
                table.setDatabaseName(databaseName);
                table.setSchemaName(schemaName);
                table.setName(viewName);
                table.setViewSql(resultSet.getString("TEXT"));
                final List<TableColumn> columns = this.columns(connection, databaseName, schemaName, viewName);
                table.setColumnList(columns);
                String columnNames = CollUtil.isEmpty(columns) ? "" : columns.stream()
                        .map(column -> format(column.getName()))
                        .collect(Collectors.joining(", ", " (", ")"));
                String str = "CREATE OR REPLACE VIEW " + format(schemaName) + "." + format(viewName)
                        + columnNames + " AS \n\t" + resultSet.getString("TEXT") + ";\n";
                for (TableColumn column : columns) {
                    if (StrUtil.isNotEmpty(column.getComment())) {
                        str += "\n\tCOMMENT ON COLUMN " + format(schemaName) + "." + format(viewName) + "." + format(column.getName()) + " IS '" + column.getComment() + "';";
                    }
                }
                table.setDdl(str);
            }
            return table;
        });
    }

    private static String TABLE_USERS_SQL_LIST = "select D.USER_ID, D.USERNAME, D.CREATED, \n" +
            "D.ACCOUNT_STATUS, D.DEFAULT_TABLESPACE, D.TEMPORARY_TABLESPACE, D.PROFILE, D.INITIAL_RSRC_CONSUMER_GROUP, D.EDITIONS_ENABLED, \n" +
            "D.AUTHENTICATION_TYPE, D.PASSWORD_VERSIONS, D.EXPIRY_DATE, D.LOCK_DATE \n" +
            "from DBA_USERS D ";

    @Override
    public List<Table> tableUsers(Connection connection, String databaseName) {
        List<Table> tableUsers = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, TABLE_USERS_SQL_LIST, resultSet -> {
            while (resultSet.next()) {
                Table tableUser = new Table();
                TableDetails tableDetails = new TableDetails();
                tableDetails.setName(resultSet.getString("USERNAME"));
                tableDetails.setCreated(resultSet.getString("CREATED"));
                tableDetails.setAccountStatus(resultSet.getString("ACCOUNT_STATUS"));
                tableDetails.setDefaultTableSpace(resultSet.getString("DEFAULT_TABLESPACE"));
                tableDetails.setTempTableSpace(resultSet.getString("TEMPORARY_TABLESPACE"));
                tableDetails.setDataBaseName(databaseName);
                tableUser.setName(resultSet.getString("USERNAME"));
                tableUser.setTableDetails(tableDetails);
                tableUsers.add(tableUser);
            }
            return tableUsers;
        });
    }


    @Override
    public Table tableUser(Connection connection, String databaseName, String userName) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(TABLE_USERS_SQL_LIST).append("WHERE D.USERNAME = '%s'");
        return SQLExecutor.getInstance().execute(connection, String.format(stringBuilder.toString(), userName), resultSet -> {
            Table tableUser = new Table();
            TableDetails tableDetails = new TableDetails();
            while (resultSet.next()) {
                tableDetails.setName(resultSet.getString("USERNAME"));
                tableDetails.setCreated(resultSet.getString("CREATED"));
                tableDetails.setAccountStatus(resultSet.getString("ACCOUNT_STATUS"));
                tableDetails.setDefaultTableSpace(resultSet.getString("DEFAULT_TABLESPACE"));
                tableDetails.setUserId(resultSet.getString("USER_ID"));
                tableDetails.setAuthenticationType(resultSet.getString("AUTHENTICATION_TYPE"));
                tableDetails.setConsumerGroup(resultSet.getString("INITIAL_RSRC_CONSUMER_GROUP"));
                tableDetails.setExpiryDate(resultSet.getString("EXPIRY_DATE"));
                tableDetails.setEditionsEnabled(resultSet.getString("EDITIONS_ENABLED"));
                tableDetails.setPasswordVersions(resultSet.getString("PASSWORD_VERSIONS"));
                tableDetails.setLockDate(resultSet.getString("LOCK_DATE"));
                tableDetails.setProfile(resultSet.getString("PROFILE"));
                tableDetails.setTempTableSpace(resultSet.getString("TEMPORARY_TABLESPACE"));
                tableDetails.setDataBaseName(databaseName);
                tableUser.setName(resultSet.getString("USERNAME"));
                tableUser.setTableDetails(tableDetails);
            }
            return tableUser;
        });
    }

    /**
     * 表空间文件路径列表
     */
    private static String TABLE_SPACE_FILE_SQL_LIST = "select b.file_id, b.tablespace_name as name, b.file_name as path \n" +
            "from dba_free_space a, dba_data_files b \n" +
            "where a.tablespace_name = b.tablespace_name";

    @Override
    public List<TableDetails> tableSpacesFile(Connection connection, Integer page, Integer size) {
        List<TableDetails> tableSpaces = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, TABLE_SPACE_FILE_SQL_LIST + SPACE_SPLIT_SQL.replace("'", ""), resultSet -> {
            while (resultSet.next()) {
                TableDetails tableDetails = new TableDetails();
                tableDetails.setPath(resultSet.getString("path"));
                tableDetails.setName(resultSet.getString("name"));
                tableSpaces.add(tableDetails);
            }
            return tableSpaces;
        });
    }


    /**
     * 单位是MB
     */
    private static String TABLE_SPACE_SQL_LIST = "select b.file_id, b.tablespace_name as name, b.file_name as path, b.bytes / 1024 as totalSize, \n" +
            "(b.bytes - sum(nvl(a.bytes, 0))) / (1024 * 1024) as useSize, sum(nvl(a.bytes, 0)) / (1024 * 1024) as freeSize, \n" +
            "sum(nvl(a.bytes, 0)) / (b.bytes) * 100 as usageRate,\n" +
            "b.autoextensible, b.maxbytes / (1024 * 1024) as maxbytes, b.online_status, b.increment_by as autoSize \n" +
            "from dba_free_space a, dba_data_files b \n" +
            "where a.tablespace_name = b.tablespace_name";

    private static String SPACE_SPLIT_SQL = "\n group by b.tablespace_name, b.file_name, b.file_id, b.bytes, b.autoextensible, b.maxbytes, b.online_status, b.increment_by \n" +
            "order by b.tablespace_name";

    @Override
    public List<Table> tableSpaces(Connection connection, String databaseName) {
        List<Table> tableSpaces = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder(TABLE_SPACE_SQL_LIST);
        stringBuilder.append(SPACE_SPLIT_SQL);
        return SQLExecutor.getInstance().execute(connection, stringBuilder.toString(), resultSet -> {
            while (resultSet.next()) {
                tableSpaces.add(buildSpace(resultSet, databaseName));
            }
            return tableSpaces;
        });
    }


    private Table buildSpace(ResultSet resultSet, String databaseName) {
        try {
            Table tableSpace = new Table();
            TableDetails tableDetails = new TableDetails();
            tableDetails.setTableSpace(resultSet.getString("name"));
            tableSpace.setName(resultSet.getString("name"));
            tableDetails.setPath(resultSet.getString("path"));
            tableDetails.setStatus(resultSet.getString("online_status"));
            String totalSize = new BigDecimal(resultSet.getString("totalSize")).setScale(2, RoundingMode.HALF_UP).toString();
            BigDecimal bigDecimal = new BigDecimal(totalSize);
            tableDetails.setTotalSize(bigDecimal.divide(new BigDecimal("1024"), RoundingMode.HALF_UP).toString());
            tableDetails.setFreeSize(new BigDecimal(resultSet.getString("freeSize")).setScale(2, RoundingMode.HALF_UP).toString());
            String useSize = resultSet.getString("useSize");
            tableDetails.setUseSize(new BigDecimal(useSize).setScale(2, RoundingMode.HALF_UP).toString());
            tableDetails.setUsageRate(new BigDecimal("100").subtract(new BigDecimal(resultSet.getString("usageRate")).setScale(2, RoundingMode.HALF_UP)).toString());
            tableDetails.setAutoScaling(resultSet.getString("autoextensible"));
            tableDetails.setAutoSize(resultSet.getString("autoSize"));
            String maxbytes = resultSet.getString("maxbytes");
            if (StringUtils.isNotBlank(maxbytes)) {
                tableDetails.setCheck(Boolean.FALSE);
            }
            if ("YES".equals(tableDetails.getAutoScaling())) {
                tableDetails.setAutoCheck(Boolean.TRUE);
            }
            BigDecimal bigDecimal1 = new BigDecimal(maxbytes);
            tableDetails.setExpandUpperLimit(bigDecimal1.setScale(0, RoundingMode.HALF_UP).toString());
            tableDetails.setUseOfMax(StringUtils.isNotBlank(maxbytes) ? new BigDecimal(useSize).divide(bigDecimal1, 2, RoundingMode.HALF_UP).toString() : "0");
            tableDetails.setDataBaseName(databaseName);
            tableSpace.setTableDetails(tableDetails);
            return tableSpace;
        } catch (SQLException e) {
            throw new RuntimeException("buildSpace失败" + e);
        }
    }

    @Override
    public Table tableSpace(Connection connection, String path) {
        StringBuilder stringBuilder = new StringBuilder(TABLE_SPACE_SQL_LIST);
        stringBuilder.append("\n and b.file_name = '%s'");
        stringBuilder.append(SPACE_SPLIT_SQL);
        Table tableSpace = new Table();
        return SQLExecutor.getInstance().execute(connection, String.format(stringBuilder.toString(), path), resultSet -> {
            while (resultSet.next()) {
                Table table = buildSpace(resultSet, null);
                BeanUtils.copyProperties(table, tableSpace);
            }
            return tableSpace;
        });
    }


    @Override
    public String showViewSql(String databaseName, String schemaName, String viewName, String viewSql, List<TableColumn> columnList) {
        String str = "CREATE OR REPLACE VIEW " + format(schemaName) + "." + format(viewName) + " AS \n\t" + viewSql + ";\n";
        for (TableColumn column : columnList) {
            if (StrUtil.isNotEmpty(column.getComment())) {
                str += "\n\tCOMMENT ON COLUMN " + format(schemaName) + "." + format(viewName) + "." + format(column.getName()) + " IS '" + column.getComment() + "';";
            }
        }
        return str;
    }

    @Override
    public DmpCommand exportDmp(ConnectInfo connectInfo, List<String> tableName, String schemaName, File logFileName, File dmpFileName, String exportDmp,Boolean isWin, String dmpUser, String dmpPassword) {
        //获取主机名
        String host = connectInfo.getHost();
        Integer port = connectInfo.getPort();
        String credential = "userid=" + dmpUser + "/" + dmpPassword + "@" + host + ":" + port;
        List<String> arguments = new ArrayList<>();
        MetaData metaData = Chat2DBContext.getMetaData();


        if (CollectionUtil.isNotEmpty(tableName)) {
            String tables = "";

            for (int i = 0; i < tableName.size(); i++) {
                if (i == tableName.size() - 1) {
                    tables = tables + metaData.getMetaDataName(schemaName) + "." + metaData.getMetaDataName(tableName.get(i));
                } else {
                    tables = tables + metaData.getMetaDataName(schemaName) + "." + metaData.getMetaDataName(tableName.get(i)) + ",";
                }
            }
            arguments.add("TABLESPACE=N");
            arguments.add("DROP=N");
            arguments.add("PARALLEL=10");
            arguments.add("DIRECTORY=" + dmpFileName.getParent());
            arguments.add("file=" + dmpFileName.getName());
            arguments.add("log=" + logFileName);
            arguments.add("tables=" + tables);
            arguments.add("LOG_WRITE=Y");
        }
        if (StrUtil.isNotEmpty(schemaName) && CollectionUtil.isEmpty(tableName)) {
            arguments.add("TABLESPACE=N");
            arguments.add("DROP=N");
            arguments.add("PARALLEL=10");
            arguments.add("DIRECTORY=" + dmpFileName.getParent());
            arguments.add("file=" + dmpFileName.getName());
            arguments.add("log=" + logFileName);
            if (schemaName.contains(",")) {
                String[] strings = schemaName.split(",");
                arguments.add("SCHEMAS=" + metaData.getMetaDmpDataName(strings));
            } else {
                arguments.add("SCHEMAS=" + metaData.getMetaDataName(schemaName));
            }
            arguments.add("LOG_WRITE=Y");
        }

        if (StrUtil.isEmpty(schemaName) && CollectionUtil.isEmpty(tableName)) {
            arguments.add("FULL=Y");
            arguments.add("TABLESPACE=N");
            arguments.add("DROP=N");
            arguments.add("PARALLEL=10");
            arguments.add("DIRECTORY=" + dmpFileName.getParent());
            arguments.add("file=" + dmpFileName.getName());
            arguments.add("log=" + logFileName);
            arguments.add("LOG_WRITE=Y");
        }

        return new DmpCommand(exportDmp, credential, arguments);
    }

    @Override
    public DmpCommand exportDmp(ConnectInfo connectInfo, List<String> tableName, String schemaName,
                                File logFileName, File dmpFileName, String exportDmp, Boolean isWin,
                                String dmpUser, String dmpPassword, DmpToolOptions options) {
        DmpCommand command = exportDmp(connectInfo, tableName, schemaName, logFileName, dmpFileName,
                exportDmp, isWin, dmpUser, dmpPassword);
        return withContentFilter(command, options);
    }

    @Override
    public DmpCommand importDmp(ConnectInfo connectInfo, List<String> tableName, String fromUser, String toUser, String schemaName, File file, File logFileName, String importDmp, List<String> fromUserList, Boolean isWin, String dmpUser, String dmpPassword) {
        //获取主机名
        String host = connectInfo.getHost();
        Integer port = connectInfo.getPort();
        String credential = dmpUser + "/" + dmpPassword + "@" + host + ":" + port;
        List<String> arguments = new ArrayList<>();
        MetaData metaData = Chat2DBContext.getMetaData();
        if (CollectionUtil.isNotEmpty(tableName) && StringUtils.isNotBlank(fromUser) && CollUtil.isEmpty(fromUserList)) {
            String tables = "";
            for (int i = 0; i < tableName.size(); i++) {
                if (i == tableName.size() - 1) {
                    tables = tables + metaData.getMetaDataName(fromUser) + "." + metaData.getMetaDataName(tableName.get(i));
                } else {
                    tables = tables + metaData.getMetaDataName(fromUser) + "." + metaData.getMetaDataName(tableName.get(i)) + ",";
                }
            }
            arguments.add("DIRECTORY=" + file.getParent());
            arguments.add("file=" + file.getName());
            arguments.add("log=" + logFileName);
            arguments.add("PARALLEL=10");
            arguments.add("tables=" + tables);
            arguments.add("REMAP_SCHEMA=" + fromUser + ":" + toUser);
            arguments.add("TABLE_EXISTS_ACTION=APPEND");
        }
        if (StringUtils.isNotBlank(fromUser) && CollectionUtil.isEmpty(tableName) && CollUtil.isEmpty(fromUserList)) {
            arguments.add("DIRECTORY=" + file.getParent());
            arguments.add("file=" + file.getName());
            arguments.add("IGNORE=Y");
            arguments.add("PARALLEL=10");
            arguments.add("log=" + logFileName);
            arguments.add("fromuser=" + fromUser);
            arguments.add("touser=" + toUser);
        }
        if (CollUtil.isNotEmpty(fromUserList)) {
            arguments.add("DIRECTORY=" + file.getParent());
            arguments.add("file=" + file.getName());
            arguments.add("log=" + logFileName);
            arguments.add("PARALLEL=10");
            arguments.add("SCHEMAS=" + fromUser);
            arguments.add("REMAP_SCHEMA=" + fromUser + ":" + toUser);
        }
        if (arguments.isEmpty()) {
            if (StrUtil.isEmpty(schemaName) && CollectionUtil.isEmpty(tableName) && StringUtils.isEmpty(fromUser)) {
                credential = "userid=" + credential;
                arguments.add("DIRECTORY=" + file.getParent());
                arguments.add("file=" + file.getName());
                arguments.add("IGNORE=Y");
                arguments.add("log=" + logFileName);
                arguments.add("FULL=Y");
                arguments.add("LOG_WRITE=N");
            }
        }
        return new DmpCommand(importDmp, credential, arguments);
    }

    @Override
    public DmpCommand importDmp(ConnectInfo connectInfo, List<String> tableName, String fromUser,
                                String toUser, String schemaName, File file, File logFileName,
                                String importDmp, List<String> fromUserList, Boolean isWin,
                                String dmpUser, String dmpPassword, DmpToolOptions options) {
        DmpCommand command = importDmp(connectInfo, tableName, fromUser, toUser, schemaName,
                file, logFileName, importDmp, fromUserList, isWin, dmpUser, dmpPassword);
        return withContentFilter(command, options);
    }

    private DmpCommand withContentFilter(DmpCommand command, DmpToolOptions options) {
        if (options == null) return command;
        List<String> excluded = DmpContentOptions.excluded(options.getContentOptions());
        if (excluded.isEmpty()) return command;
        return command.withOption("EXCLUDE=(" + String.join(",", excluded) + ")");
    }

    private static String INDEX_SQL = "SELECT i.TABLE_NAME, i.STATUS, i.INDEX_TYPE, i.INDEX_NAME, i.UNIQUENESS ,c.COLUMN_NAME, c.COLUMN_POSITION, c.DESCEND, cons.CONSTRAINT_TYPE, cons.CONSTRAINT_NAME FROM ALL_INDEXES i LEFT JOIN ALL_IND_COLUMNS c ON i.INDEX_NAME = c.INDEX_NAME AND i.TABLE_NAME = c.TABLE_NAME AND i.TABLE_OWNER = c.TABLE_OWNER LEFT JOIN ALL_CONSTRAINTS cons ON i.INDEX_NAME = cons.INDEX_NAME AND i.TABLE_NAME = cons.TABLE_NAME AND i.TABLE_OWNER = cons.OWNER WHERE i.TABLE_OWNER = '%s' AND i.TABLE_NAME = '%s' ORDER BY i.INDEX_NAME, c.COLUMN_POSITION;";

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(INDEX_SQL, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            LinkedHashMap<String, TableIndex> map = new LinkedHashMap();
            while (resultSet.next()) {
                String keyName = resultSet.getString("INDEX_NAME");
                TableIndex tableIndex = map.get(keyName);
                if (tableIndex != null) {
                    List<TableIndexColumn> columnList = tableIndex.getColumnList();
                    columnList.add(getTableIndexColumn(resultSet));
                    columnList = columnList.stream().sorted(Comparator.comparing(TableIndexColumn::getOrdinalPosition))
                            .collect(Collectors.toList());
                    tableIndex.setColumnList(columnList);
                } else {
                    TableIndex index = new TableIndex();
                    index.setDatabaseName(databaseName);
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(keyName);
                    index.setKeyName(resultSet.getString("CONSTRAINT_NAME"));
                    index.setStatus(getConStatus(connection, tableName, schemaName, resultSet.getString("CONSTRAINT_NAME")));
                    index.setUnique("UNIQUE".equalsIgnoreCase(resultSet.getString("UNIQUENESS")));
//                    index.setType(resultSet.getString("Index_type"));
//                    index.setComment(resultSet.getString("Index_comment"));
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableIndexColumn(resultSet));
                    index.setColumn(getTableIndexColumn(resultSet).getColumnName());
                    index.setColumnList(tableIndexColumns);
                    if ("P".equalsIgnoreCase(resultSet.getString("CONSTRAINT_TYPE"))) {
                        index.setType(DMIndexTypeEnum.PRIMARY_KEY.getName());
                    } else if (index.getUnique()) {
                        index.setType(DMIndexTypeEnum.UNIQUE.getName());
                    } else if ("BITMAP".equalsIgnoreCase(resultSet.getString("INDEX_TYPE"))) {
                        index.setType(DMIndexTypeEnum.BITMAP.getName());
                    } else if ("VIRTUAL".equalsIgnoreCase(resultSet.getString("INDEX_TYPE"))) {
                        // 外键
                        String columnName = getConstraintsIndex(Chat2DBContext.getConnection(), schemaName, tableName, resultSet.getString("COLUMN_NAME"));
                        index.setKeyName(columnName);
                        index.setType(DMIndexTypeEnum.VIRTUAL.getName());
                    } else {
                        index.setType(DMIndexTypeEnum.NORMAL.getName());
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
        });

    }
    private static final String REFERENCED_FOREIGN_KEY_SQL =
            "SELECT fk.OWNER AS F_OWNER, fk.TABLE_NAME AS F_TABLE, fk.CONSTRAINT_NAME AS F_KEY, " +
                    "fk.STATUS, pk.CONSTRAINT_NAME AS P_KEY FROM ALL_CONSTRAINTS pk " +
                    "JOIN ALL_CONSTRAINTS fk ON fk.R_OWNER = pk.OWNER " +
                    "AND fk.R_CONSTRAINT_NAME = pk.CONSTRAINT_NAME AND fk.CONSTRAINT_TYPE = 'R' " +
                    "WHERE pk.OWNER = '%s' AND pk.TABLE_NAME = '%s' AND pk.CONSTRAINT_TYPE IN ('P', 'U') " +
                    "ORDER BY fk.TABLE_NAME, fk.CONSTRAINT_NAME";

    static String buildReferencedForeignKeySql(String schemaName, String tableName) {
        return String.format(REFERENCED_FOREIGN_KEY_SQL,
                schemaName.replace("'", "''"), tableName.replace("'", "''"));
    }

    @Override
    public List<ForeignData> beiForeGnKey(Connection connection, String schemaName, String tableName) {
        // Resolve the small constraint set before reading columns, rather than joining four catalog views.
        String constraintsSql = buildReferencedForeignKeySql(schemaName, tableName);
        List<String[]> constraints = SQLExecutor.getInstance().execute(connection, constraintsSql, RELATION_QUERY_TIMEOUT_SECONDS, resultSet -> {
            List<String[]> result = new ArrayList<>();
            while (resultSet.next()) {
                result.add(new String[]{resultSet.getString("F_OWNER"), resultSet.getString("F_TABLE"),
                        resultSet.getString("F_KEY"), resultSet.getString("STATUS"), resultSet.getString("P_KEY")});
            }
            return result;
        });
        if (constraints.isEmpty()) return new ArrayList<>();
        Map<String, Set<String>> keysByOwner = new LinkedHashMap<>();
        for (String[] key : constraints) {
            keysByOwner.computeIfAbsent(key[0], ignored -> new LinkedHashSet<>()).add(key[2]);
            keysByOwner.computeIfAbsent(schemaName, ignored -> new LinkedHashSet<>()).add(key[4]);
        }
        String filters = keysByOwner.entrySet().stream().map(entry -> "(OWNER = '" + entry.getKey().replace("'", "''") +
                "' AND CONSTRAINT_NAME IN (" + entry.getValue().stream().map(name -> "'" + name.replace("'", "''") + "'")
                .collect(Collectors.joining(",")) + "))").collect(Collectors.joining(" OR "));
        Map<String, Map<Integer, String>> columns = SQLExecutor.getInstance().execute(connection,
                "SELECT OWNER, TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME, POSITION FROM ALL_CONS_COLUMNS WHERE " + filters,
                RELATION_QUERY_TIMEOUT_SECONDS, resultSet -> {
                    Map<String, Map<Integer, String>> result = new HashMap<>();
                    while (resultSet.next()) result.computeIfAbsent(resultSet.getString("OWNER") + "\u0000" + resultSet.getString("TABLE_NAME") + "\u0000" + resultSet.getString("CONSTRAINT_NAME"),
                            ignored -> new TreeMap<>()).put(resultSet.getInt("POSITION"), resultSet.getString("COLUMN_NAME"));
                    return result;
                });
        List<ForeignData> foreignKeys = new ArrayList<>();
        for (String[] key : constraints) {
            Map<Integer, String> child = columns.get(key[0] + "\u0000" + key[1] + "\u0000" + key[2]);
            Map<Integer, String> parent = columns.get(schemaName + "\u0000" + tableName + "\u0000" + key[4]);
            if (child == null || parent == null) continue;
            for (Map.Entry<Integer, String> column : child.entrySet()) {
                if (!parent.containsKey(column.getKey())) continue;
                ForeignData relation = new ForeignData();
                relation.setSchemaName(schemaName); relation.setTableName(tableName);
                relation.setForeignSchemaName(key[0]); relation.setForeignTableName(key[1]);
                relation.setConstraintName(key[2]); relation.setStatus(key[3]);
                relation.setColumn(parent.get(column.getKey())); relation.setForeignColumnName(column.getValue());
                foreignKeys.add(relation);
            }
        }
        return foreignKeys;
    }

    private static String getConstraintsIndex = "select CONSTRAINT_NAME from ALL_CONS_COLUMNS WHERE OWNER = '%s' AND TABLE_NAME = '%s' AND COLUMN_NAME = '%s';";

    public String getConstraintsIndex(Connection connection, String schemaName, String tableName, String column) {
        return SQLExecutor.getInstance().execute(connection, String.format(getConstraintsIndex, schemaName, tableName, column), resultSet -> {
            String name = "";
            while (resultSet.next()) {
                name = resultSet.getString("CONSTRAINT_NAME");
            }
            return name;
        });
    }



        private static String SQL_FOREIGN_KEY_SQL = "SELECT CONS.TABLE_NAME   AS \"forTableName\",\n" +
                "\t\t\t\tCONS.STATUS as \"status\",\n" +
                "               COLS.COLUMN_NAME  AS \"forTableColumn\",\n" +
                "               COLS.CONSTRAINT_NAME  CONSTRAINT_NAME,\n" +
                "               CONS.R_OWNER AS \"forSchema\",\n" +
                "               CONS_R.TABLE_NAME AS \"table\",\n" +
                "              \tCASE\n" +
                "              \tWHEN CONS_R.R_OWNER IS NULL\n" +
                "              \tTHEN CONS.R_OWNER\n" +
                "              \tELSE CONS_R.R_OWNER\n" +
                "                END AS \"schema\",\n" +
                "               (\n" +
                "                   SELECT COLS_R.COLUMN_NAME\n" +
                "                   FROM SYS.ALL_CONS_COLUMNS COLS_R\n" +
                "                   WHERE COLS_R.OWNER = CONS.R_OWNER\n" +
                "                     AND COLS_R.CONSTRAINT_NAME = CONS.R_CONSTRAINT_NAME\n" +
                "                     AND COLS_R.POSITION = COLS.POSITION\n" +
                "               )                 \"tableColumn\"\n" +
                "        FROM SYS.ALL_CONSTRAINTS CONS,\n" +
                "             SYS.ALL_CONS_COLUMNS COLS,\n" +
                "             SYS.ALL_CONSTRAINTS CONS_R\n" +
                "        WHERE COLS.OWNER(+) = CONS.OWNER\n" +
                "          AND COLS.TABLE_NAME(+) = CONS.TABLE_NAME\n" +
                "          AND COLS.CONSTRAINT_NAME(+) = CONS.CONSTRAINT_NAME\n" +
                "          AND CONS_R.OWNER(+) = CONS.R_OWNER\n" +
                "          AND CONS_R.CONSTRAINT_NAME(+) = CONS.R_CONSTRAINT_NAME\n" +
                "          AND CONS.OWNER = '%s'\n" +
                "          AND CONS.TABLE_NAME = '%s'\n" +
                "          AND CONS.CONSTRAINT_TYPE = 'R'\n" +
                "        ORDER BY CONS.CONSTRAINT_NAME, COLS.POSITION";
    /**
     * 获取外键信息
     * @param connection
     * @param schemaName
     * @param tableName
     * @return
     */
    @Override
    public List<ForeignData> getForeignKey(Connection connection, String tableName, String schemaName) {
        List<ForeignData> foreignKeys = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(SQL_FOREIGN_KEY_SQL, schemaName, tableName), RELATION_QUERY_TIMEOUT_SECONDS, resultSet -> {
            while (resultSet.next()) {
                ForeignData foreignData = new ForeignData();
                foreignData.setSchemaName(resultSet.getString("forSchema"));
                foreignData.setForeignSchemaName(resultSet.getString("schema"));
                foreignData.setForeignTableName(resultSet.getString("table"));
                foreignData.setTableName(resultSet.getString("forTableName"));
                foreignData.setConstraintName(resultSet.getString("CONSTRAINT_NAME"));
                foreignData.setStatus("ENABLED".equals(resultSet.getString("status")) ? "VALID" : "INVALID");
                foreignData.setColumn(resultSet.getString("forTableColumn"));
                foreignData.setForeignColumnName(resultSet.getString("tableColumn"));
                foreignData.setConstraintName(resultSet.getString("CONSTRAINT_NAME"));
                foreignKeys.add(foreignData);
            }
            return foreignKeys;
        });
    }

    private static String getConStatus = "SELECT STATUS FROM ALL_CONSTRAINTS WHERE CONSTRAINT_NAME = '%s' AND OWNER = '%s' AND TABLE_NAME = '%s'";

    /**
     * 获取外键状态
     * @param connection
     * @param schemaName
     * @param tableName
     * @return
     */
    public String getConStatus(Connection connection, String tableName, String schemaName, String conName) {
        if (StrUtil.isBlank(conName) || "NULL".equalsIgnoreCase(conName)) {
            return "VALID";
        }
        String sql = String.format(getConStatus, conName, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            String constraintName = "";
            while (resultSet.next()) {
                constraintName = "ENABLED".equals(resultSet.getString("STATUS")) ? "VALID": "INVALID";
            }
            if (StrUtil.isEmpty(constraintName)) {
                constraintName = "VALID";
            }
            return constraintName;
        });
    }

//
//    private static String SQL_UN_FOREIGN_KEY_SQL =
//            "SELECT a.owner as \"schema\", a.table_name \"table\", a.constraint_name, a.column_name as \"tableColumn\",c.constraint_name, c_pk.owner as \"forSchema\", c_pk.table_name \"forTableName\", b.column_name as \"forTableColumn\", c_pk.status \"status\" \n" +
//                    "FROM all_cons_columns a \n" +
//                    "join all_constraints c on a.owner = c.owner\n" +
//                    "and a.constraint_name = c.constraint_name \n" +
//                    "join all_constraints c_pk on c.r_owner = c_pk.owner\n" +
//                    "and c.r_constraint_name = c_pk.constraint_name\n" +
//                    "join all_cons_columns b on c_pk.owner = b.owner\n" +
//                    "and b.constraint_name = c_pk.constraint_name\n" +
//                    "and b.position = a.position\n" +
//                    "where c.CONSTRAINT_TYPE = 'R'\n" +
//                    "and a.owner = '%s' and a.table_name = '%s'\n" +
//                    "order by a.owner, a.constraint_name, a.position;";
    /**
     * 获取被引用信息
     * @param connection
     * @param schemaName
     * @param tableName
     * @return
     */
    @Override
    public List<ForeignData> getUnForeignKey(Connection connection, String schemaName, String tableName) {
        Set<ForeignData> set = new HashSet<>();
        return SQLExecutor.getInstance().execute(connection, String.format(SQL_FOREIGN_KEY_SQL, schemaName, tableName), resultSet -> {
            while (resultSet.next()) {
                ForeignData foreignData = new ForeignData();
                foreignData.setForeignSchemaName(resultSet.getString("schema"));
                foreignData.setSchemaName(resultSet.getString("forSchema"));
                foreignData.setTableName(resultSet.getString("forTableName"));
                foreignData.setForeignTableName(resultSet.getString("table"));
                foreignData.setStatus(resultSet.getString("status"));
                foreignData.setForeignColumnName(resultSet.getString("forTableColumn"));
                foreignData.setColumn(resultSet.getString("tableColumn"));
                foreignData.setConstraintName(resultSet.getString("CONSTRAINT_NAME"));
                set.add(foreignData);
            }
            return Lists.newArrayList(set);
        });
    }


    private TableIndexColumn getTableIndexColumn(ResultSet resultSet) throws SQLException {
        TableIndexColumn tableIndexColumn = new TableIndexColumn();
        tableIndexColumn.setColumnName(resultSet.getString("COLUMN_NAME"));
        tableIndexColumn.setOrdinalPosition(resultSet.getShort("COLUMN_POSITION"));
//        tableIndexColumn.setCollation(resultSet.getString("Collation"));
//        tableIndexColumn.setCardinality(resultSet.getLong("Cardinality"));
//        tableIndexColumn.setSubPart(resultSet.getLong("Sub_part"));
        String collation = resultSet.getString("DESCEND");
        if ("ASC".equalsIgnoreCase(collation)) {
            tableIndexColumn.setAscOrDesc("ASC");
        } else if ("DESC".equalsIgnoreCase(collation)) {
            tableIndexColumn.setAscOrDesc("DESC");
        }
        return tableIndexColumn;
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new DMSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(DMColumnTypeEnum.getTypes())
                .charsets(Lists.newArrayList())
                .collations(Lists.newArrayList())
                .indexTypes(DMIndexTypeEnum.getIndexTypes())
                .defaultValues(DMDefaultValueEnum.getDefaultValues())
                .build();
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining("."));
    }

    @Override
    public String getMetaDmpDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining(","));
    }


    @Override
    public List<String> getSystemSchemas() {
        return systemSchemas;
    }


    private static String CONSTRAINT_SQL = "SELECT CONSTRAINT_NAME FROM ALL_CONSTRAINTS WHERE  OWNER = '%s' AND TABLE_NAME = '%s' AND CONSTRAINT_TYPE = 'P'";

    @Override
    public String getConstraintName(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(CONSTRAINT_SQL, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            String constraintName = "";
            while (resultSet.next()) {
                constraintName = resultSet.getString("CONSTRAINT_NAME");
            }
            return constraintName;
        });
    }

    private static String SELECT_TAB_COLS = " SELECT /*+ RULE */ " +
            " ATC.OWNER, " +
            " ATC.TABLE_NAME                  , " +
            " ATC.COLUMN_ID                   , " +
            " ATC.COLUMN_NAME  , " +
            " ATC.DATA_TYPE    , " +
            " ATC.DATA_LENGTH  , " +
            " ATC.DATA_TYPE_MOD               , " +
            " ATC.NULLABLE                    , " +
            " ATC.DATA_DEFAULT, " +
            " ACC.COMMENTS                    , " +
            " ATC.DATA_PRECISION              , " +
            " ATC.DATA_SCALE                  , " +
            " ATC.CHAR_USED, PK.CONSTRAINT_NAME AS PK_NAME, PK.POSITION AS PK_POSITION " +
            " FROM ALL_TAB_COLUMNS ATC " +
            " LEFT JOIN ALL_COL_COMMENTS ACC ON ATC.OWNER = ACC.SCHEMA_NAME " +
            " AND ATC.TABLE_NAME = ACC.TABLE_NAME AND ATC.COLUMN_NAME = ACC.COLUMN_NAME " +
            " LEFT JOIN (SELECT CC.COLUMN_NAME, CC.CONSTRAINT_NAME, CC.POSITION " +
            " FROM ALL_CONSTRAINTS C JOIN ALL_CONS_COLUMNS CC " +
            " ON C.OWNER = CC.OWNER AND C.TABLE_NAME = CC.TABLE_NAME AND C.CONSTRAINT_NAME = CC.CONSTRAINT_NAME " +
            " WHERE C.OWNER = '%s' AND C.TABLE_NAME = '%s' AND C.CONSTRAINT_TYPE = 'P') PK " +
            " ON PK.COLUMN_NAME = ATC.COLUMN_NAME " +
            " WHERE ATC.OWNER = '%s' AND ATC.TABLE_NAME = '%s' " +
            " ORDER BY ATC.COLUMN_ID ";

    static String buildColumnsSql(String schemaName, String tableName) {
        String owner = schemaName.replace("'", "''");
        String table = tableName.replace("'", "''");
        return String.format(SELECT_TAB_COLS, owner, table, owner, table);
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = buildColumnsSql(schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            List<String> incrementColumns = null;
            boolean isFirst = true;
            List<TableColumn> tableColumns = new ArrayList<>();
            while (resultSet.next()) {
                TableColumn tableColumn = new TableColumn();
                tableColumn.setTableName(tableName);
                tableColumn.setSchemaName(schemaName);
                try {
                    //
                    // Fields of the LONG type cannot be retrieved using getObject. They need to be accessed using getCharacterStream, and must be read first in the sequence.
                    Reader reader = resultSet.getCharacterStream("DATA_DEFAULT");
                    if (reader != null) {
                        StringBuilder sb = new StringBuilder();
                        int charValue;
                        while ((charValue = reader.read()) != -1) {
                            sb.append((char) charValue);
                        }
                        tableColumn.setDefaultValue(sb.toString());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final String columnName = resultSet.getString("COLUMN_NAME");
                final String owner = resultSet.getString("OWNER");
                tableColumn.setName(columnName);
                tableColumn.setColumnType(resultSet.getString("DATA_TYPE"));
                Integer dataPrecision = resultSet.getInt("DATA_PRECISION");
                if (resultSet.getString("DATA_PRECISION") != null) {
                    tableColumn.setColumnSize(dataPrecision);
                } else {
                    if("DATE".equals(tableColumn.getColumnType())){
                        tableColumn.setColumnSize(13);
                    }else{
                        tableColumn.setColumnSize(resultSet.getInt("DATA_LENGTH"));
                    }

                }
                tableColumn.setComment(resultSet.getString("COMMENTS"));
                tableColumn.setNullable("Y".equalsIgnoreCase(resultSet.getString("NULLABLE")) ? 0 : 1);
                tableColumn.setOrdinalPosition(resultSet.getInt("COLUMN_ID"));
                tableColumn.setDecimalDigits(resultSet.getInt("DATA_SCALE"));
                String charUsed = resultSet.getString("CHAR_USED");
                if ("B".equalsIgnoreCase(charUsed)) {
                    tableColumn.setUnit("BYTE");
                } else if ("C".equalsIgnoreCase(charUsed)) {
                    tableColumn.setUnit("CHAR");
                }
                // 查询主键、自增序列
                if (isFirst) {
                    incrementColumns = getAutoIncrement(connection, owner, tableName);
                    isFirst = false;
                }
                tableColumn.setPrimaryKey(resultSet.getString("PK_NAME") != null);
                tableColumn.setPrimaryKeyName(resultSet.getString("PK_NAME"));
                tableColumn.setPrimaryKeyOrder(resultSet.getInt("PK_POSITION"));
                // 查询自增序列
                tableColumn.setAutoIncrement(incrementColumns.contains(columnName));
                tableColumns.add(tableColumn);
            }
            return tableColumns;
        });
    }

    private static final String SELECT_TAB_AUTO_INCREMENT = "SELECT\n" +
            "        B.OWNER        ,\n" +
            "        B.TABLE_NAME   ,\n" +
            "        A.NAME COL_NAME,\n" +
            "        A.TYPE$\n" +
            "FROM\n" +
            "        SYSCOLUMNS A,\n" +
            "        ALL_TABLES B    ,\n" +
            "        SYSOBJECTS C\n" +
            "WHERE\n" +
            "        (A.INFO2 & 1)=1\n" +
            "    AND A.ID         =C.ID\n" +
            "    AND C.NAME       = B.TABLE_NAME\n" +
            "    AND B.OWNER      = '%s'\n" +
            "    AND B.TABLE_NAME = '%s'\n" +
            "    AND C.SCHID = (SELECT ID FROM SYSOBJECTS WHERE TYPE$ = 'SCH' AND NAME = '%s')";

    private static final String SELECT_TABLE_DDL =
            "SELECT DBMS_METADATA.GET_DDL('TABLE', '%s', '%s') AS DDL FROM DUAL";
    private static final Pattern IDENTITY_COLUMN_PATTERN = Pattern.compile(
            "^\\s*\"((?:[^\"]|\"\")+)\"\\s+.*\\b(?:IDENTITY|AUTO_INCREMENT)\\b.*$",
            Pattern.CASE_INSENSITIVE);

    static String buildAutoIncrementSql(String owner, String tableName) {
        return String.format(SELECT_TAB_AUTO_INCREMENT,
                owner.replace("'", "''"), tableName.replace("'", "''"), owner.replace("'", "''"));
    }

    static String buildTableDdlSql(String owner, String tableName) {
        return String.format(SELECT_TABLE_DDL,
                tableName.replace("'", "''"), owner.replace("'", "''"));
    }

    static List<String> parseAutoIncrementColumns(String ddl) {
        List<String> incrementColumns = Lists.newArrayList();
        if (ddl == null) {
            return incrementColumns;
        }
        for (String line : ddl.split("\\r?\\n")) {
            Matcher matcher = IDENTITY_COLUMN_PATTERN.matcher(line);
            if (matcher.matches()) {
                incrementColumns.add(matcher.group(1).replace("\"\"", "\""));
            }
        }
        return incrementColumns;
    }

    private List<String> getAutoIncrement(Connection connection, String owner, String tableName) {
        String sql = buildAutoIncrementSql(owner, tableName);
        try {
            return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
                List<String> incrementColumns = Lists.newArrayList();
                while (resultSet.next()) {
                    final String colName = resultSet.getString("COL_NAME");
                    final String type = resultSet.getString("TYPE$");
                    if (DMColumnTypeEnum.INT.name().equals(type) ||
                            DMColumnTypeEnum.INTEGER.name().equals(type) ||
                            DMColumnTypeEnum.BIGINT.name().equals(type) ||
                            DMColumnTypeEnum.SMALLINT.name().equals(type) ||
                            DMColumnTypeEnum.TINYINT.name().equals(type)) {
                        incrementColumns.add(colName);
                    }
                }
                return incrementColumns;
            });
        } catch (RuntimeException catalogException) {
            try {
                String ddlSql = buildTableDdlSql(owner, tableName);
                return SQLExecutor.getInstance().execute(connection, ddlSql, resultSet ->
                        resultSet.next() ? parseAutoIncrementColumns(resultSet.getString("DDL")) : Lists.newArrayList());
            } catch (RuntimeException ddlException) {
                catalogException.addSuppressed(ddlException);
                log.warn("Unable to query DM identity metadata for {}.{}", owner, tableName, catalogException);
                return Lists.newArrayList();
            }
        }
    }


        private static String SELECT_TABLE_DETAILS_SQL = "select\n" +
                "        DISTINCT t.table_name     ,\n" +
                "        t.owner          ,\n" +
                "        t.tablespace_name,\n" +
                "        t.last_analyzed  ,\n" +
                "        t.num_rows       \n" +
                "from\n" +
                "        all_tables t\n" +
                "where t.owner = '%s'\n"+
                "order by t.table_name ASC";


    @Override
    public List<Table> tableDetails(Connection connection, String databaseName, String schemaName) {
        List<Table> tableSpaces = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(SELECT_TABLE_DETAILS_SQL, schemaName), resultSet -> {
            while (resultSet.next()) {
                Table tableSpace = new Table();
                TableDetails tableDetails = new TableDetails();
                tableDetails.setName(resultSet.getString("table_name"));
                tableDetails.setSchema(resultSet.getString("owner"));
                tableDetails.setTableSpace(resultSet.getString("tablespace_name"));
                tableDetails.setLastAnalyzed(resultSet.getString("last_analyzed"));
                tableDetails.setNumRows(resultSet.getString("num_rows"));
                tableDetails.setDataBaseName(databaseName);
                tableSpace.setName(resultSet.getString("table_name"));
                tableSpace.setTableDetails(tableDetails);
                tableSpaces.add(tableSpace);
            }
            return tableSpaces;
        });
    }


    private static String SELECT_TABLE_DETAILS_DATA_SQL = "select DISTINCT\n" +
            "        o.object_name  ,\n" +
            "        o.created      ,\n" +
            "        o.last_ddl_time,\n" +
            "        a.comments\n" +
            "from\n" +
            "        all_objects o\n" +
            "    \n" +
            "left join ALL_TAB_COMMENTS a\n" +
            "on\n" +
            "        o.object_name = a.table_name\n" +
            "    and o.object_type = 'TABLE'\n" +
            "    and a.TABLE_TYPE  = 'TABLE'\n" +
            "    and a.owner       = '%s'\n" +
            "where o.owner = '%s'\n" +
            "  and o.object_type = 'TABLE'";

    @Override
    public List<TableDetails> tableDetailsData(Connection connection, String databaseName, String schemaName) {
        List<TableDetails> tableSpaces = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(SELECT_TABLE_DETAILS_DATA_SQL, schemaName, schemaName), resultSet -> {
            while (resultSet.next()) {
                TableDetails tableDetails = new TableDetails();
                tableDetails.setName(resultSet.getString("object_name"));
                tableDetails.setCreated(resultSet.getString("created"));
                tableDetails.setLastDDL(resultSet.getString("last_ddl_time"));
                tableDetails.setComment(resultSet.getString("comments"));
                tableDetails.setDataBaseName(databaseName);
                tableSpaces.add(tableDetails);
            }
            return tableSpaces;
        });
    }


    private static String SELECT_TABLE_VIEW_SQL = "select object_name, owner, status, created, last_ddl_time, object_type " +
            "from sys.all_objects where object_type = 'VIEW' and owner = '%s' " +
            "union all select m.mview_name, s.name, o.status, o.created, o.last_ddl_time, 'MATERIALIZED VIEW' " +
            "from ALL_MVIEWS m join sys.sysobjects s on s.id = m.schid and s.type$ = 'SCH' " +
            "left join sys.all_objects o on o.owner = s.name " +
            "and o.object_name = m.mview_name and o.object_type = 'MATERIALIZED VIEW' where s.name = '%s' " +
            "order by object_name";

    @Override
    public List<Table> tableViews(Connection connection, String databaseName, String schemaName) {
        List<Table> tableSpaces = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(SELECT_TABLE_VIEW_SQL, sqlLiteral(schemaName), sqlLiteral(schemaName)), resultSet -> {
            while (resultSet.next()) {
                Table tableSpace = new Table();
                TableDetails tableDetails = new TableDetails();
                tableDetails.setName(resultSet.getString("object_name"));
                tableDetails.setSchema(resultSet.getString("owner"));
                tableDetails.setValid(resultSet.getString("status"));
                tableDetails.setCreated(resultSet.getString("created"));
                tableDetails.setLastDDL(resultSet.getString("last_ddl_time"));
                tableDetails.setType(resultSet.getString("object_type"));
                tableDetails.setDataBaseName(databaseName);
                tableSpace.setName(resultSet.getString("object_name"));
                tableSpace.setTableDetails(tableDetails);
                tableSpaces.add(tableSpace);
            }
            return tableSpaces;
        });
    }

    private static String SELECT_USER_ROLE_SQL = "select GRANTEE, GRANTED_ROLE, ADMIN_OPTION, DEFAULT_ROLE from DBA_ROLE_PRIVS WHERE GRANTEE=";

    @Override
    public List<TableUserRole> getUserRule(Connection connection, String username) {
        List<TableUserRole> tableUserRoles = new ArrayList<>();
        String sql = SELECT_USER_ROLE_SQL + "'" + username + "'";
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                TableUserRole tableUserRole = new TableUserRole();
                tableUserRole.setRole(resultSet.getString("GRANTED_ROLE"));
                tableUserRole.setGrantee(resultSet.getString("GRANTEE"));
                tableUserRole.setIsAdmin("N".equals(resultSet.getString("ADMIN_OPTION")) ? "否" : "是");
                tableUserRole.setIsDefault(resultSet.getString("DEFAULT_ROLE"));
                tableUserRoles.add(tableUserRole);
            }
            return tableUserRoles;
        });
    }



    public static String QUERY_ROLES_SQL = "SELECT GRANTEE, PRIVILEGE, GRANTABLE, GRANTOR, OWNER, TABLE_NAME FROM ALL_TAB_PRIVS \n" +
            "WHERE OWNER = '%s' AND TABLE_NAME = '%s'";

    @Override
    public List<TableRole> queryRoles(Connection connection, String schemaName, String tableName) {
        List<TableRole> tables = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(QUERY_ROLES_SQL, schemaName, tableName), resultSet -> {
            while (resultSet.next()) {
                TableRole tableRole = new TableRole();
                tableRole.setSchemaName(resultSet.getString("OWNER"));
                tableRole.setTableName(resultSet.getString("TABLE_NAME"));
                tableRole.setGrantor(resultSet.getString("GRANTOR"));
                tableRole.setGrantAble(resultSet.getString("GRANTABLE"));
                tableRole.setPrivilege(resultSet.getString("PRIVILEGE"));
                tableRole.setGrantee(resultSet.getString("GRANTEE"));
                tables.add(tableRole);
            }
            return tables;
        });
    }

    public static String QUERY_COUNT_SQL = "SELECT COUNT(*) as COUNT FROM \"'%s'\".\"'%s'\"";

    @Override
    public String getCount(Connection connection, String tableName, String schemaName) {
        if (systemSchemas.contains(schemaName)) {
            return "";
        }
        String format = String.format(QUERY_COUNT_SQL, schemaName, tableName).replace("'", "");
        StringBuilder stringBuilder = new StringBuilder(format);
        try {
            return SQLExecutor.getInstance().execute(connection, stringBuilder.toString(), resultSet -> {
                String count = "";
                while (resultSet.next()) {
                    count = resultSet.getString("COUNT");
                }
                return count;
            });
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Boolean executeSQL(Connection connection, String sql) {
        ExecuteResult executeResult = new ExecuteResult();
        try {
            executeResult = SQLExecutor.getInstance().execute(connection, sql);
            if (executeResult.getSuccess()) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void dropTablespace(Connection connection, String spaceName) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("DROP TABLESPACE \"%s\"");
//        stringBuilder.append("DROP TABLESPACE [%s] INCLUDING CONTENTS AND DATAFILES CASCADE CONSTRAINTS ;");
        try {
            SQLExecutor.getInstance().execute(connection, String.format(stringBuilder.toString(), spaceName));
        } catch (SQLException e) {
            throw new BusinessException("删除表空间信息失败");
        }
    }
}
