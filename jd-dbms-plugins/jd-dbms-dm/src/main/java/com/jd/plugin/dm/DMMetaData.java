package com.jd.plugin.dm;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
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
import org.springframework.beans.BeanUtils;

import javax.validation.constraints.NotEmpty;
import java.io.File;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class DMMetaData extends DefaultMetaService implements MetaData {

    private static final int RELATION_QUERY_TIMEOUT_SECONDS = 15;

    private List<String> systemSchemas = Arrays.asList("CTISYS", "SYS", "SYSDBA", "SYSSSO", "SYSAUDITOR");

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        return SortUtils.sortSchema(schemas, systemSchemas);
    }

    private String format(String tableName) {
        return "\"" + tableName + "\"";
    }

    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = " SELECT " +
                " (SELECT comments FROM all_tab_comments WHERE table_name = '%s' and owner='%s') AS comments, " +
                " (SELECT dbms_metadata.get_ddl('TABLE', '%s', '%s') FROM dual) AS ddl " +
                " FROM dual; ";
        String selectObjectDDLSQL = String.format(sql, tableName, schemaName, tableName, schemaName);
        String tableSql = SQLExecutor.getInstance().execute(connection, selectObjectDDLSQL, resultSet -> {
            String ddl = "";
            try {
                while (resultSet.next()) {
                    ddl = resultSet.getString("ddl");
                    String comment = resultSet.getString("comments");
                    if (StringUtils.isNotBlank(comment)) {
                        return ddl + "\n" + "COMMENT ON TABLE " + format(schemaName) + "." + format(tableName) +
                                " IS " + "'" + comment + "';";
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
        String columnCommentsDDLSQL = String.format(columnCommentsSql, schemaName, tableName);
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
                                "." + format(columnName) + " IS " + "'" + comments + "';";
                    }
                }
                return ddl;
            } catch (SQLException e) {
                return "";
            }
        });
        //TODO 索引SQL
        return tableSql + columnSql;
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

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, "PROC", schemaName, procedureName);
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

    private static String TRIGGER_SQL
            = "SELECT OWNER, TRIGGER_NAME, TABLE_OWNER, TABLE_NAME, TRIGGERING_TYPE, TRIGGERING_EVENT, STATUS, TRIGGER_BODY "
            + "FROM ALL_TRIGGERS WHERE OWNER = '%s' AND TRIGGER_NAME = '%s'";

    private static String TRIGGER_SQL_LIST = "SELECT OWNER, TRIGGER_NAME, TABLE_OWNER, TABLE_NAME, TRIGGERING_TYPE, TRIGGERING_EVENT, STATUS, TRIGGER_BODY FROM ALL_TRIGGERS WHERE OWNER = '%s' AND TABLE_NAME = '%s'";

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName, String tableName) {
        List<Trigger> triggers = new ArrayList<>();
        String sql = String.format(TRIGGER_SQL_LIST, schemaName, tableName);
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
    public List<TableObjectRoleData> allObjectRole(Connection connection, String userName, String schemaName, String tableName) {
        String sql = String.format(getObjectRule, userName, tableName, schemaName);
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
    public Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName,
                           String triggerName) {

        String sql = String.format(TRIGGER_SQL, schemaName, triggerName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            trigger.setTriggerBody(resultSet.getString("TRIGGER_BODY"));
            trigger.setType(resultSet.getString("TRIGGERING_TYPE"));
            trigger.setStatus(resultSet.getString("STATUS"));
            trigger.setEvent(resultSet.getString("TRIGGERING_EVENT"));
            return trigger;
        });
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
                String str = "CREATE OR REPLACE VIEW " + format(schemaName) + "." + format(viewName) + " AS \n\t" + resultSet.getString("TEXT") + ";\n";
                final List<TableColumn> columns = this.columns(connection, databaseName, schemaName, viewName);
                table.setColumnList(columns);
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
            arguments.add("file=" + dmpFileName);
            arguments.add("log=" + logFileName);
            arguments.add("tables=" + tables);
            arguments.add("LOG_WRITE=Y");
        }
        if (StrUtil.isNotEmpty(schemaName) && CollectionUtil.isEmpty(tableName)) {
            arguments.add("TABLESPACE=N");
            arguments.add("DROP=N");
            arguments.add("PARALLEL=10");
            arguments.add("file=" + dmpFileName);
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
            arguments.add("file=" + dmpFileName);
            arguments.add("log=" + logFileName);
            arguments.add("LOG_WRITE=Y");
        }

        return new DmpCommand(exportDmp, credential, arguments);
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
            arguments.add("file=" + file);
            arguments.add("log=" + logFileName);
            arguments.add("PARALLEL=10");
            arguments.add("tables=" + tables);
            arguments.add("REMAP_SCHEMA=" + fromUser + ":" + toUser);
        }
        if (StringUtils.isNotBlank(fromUser) && CollectionUtil.isEmpty(tableName) && CollUtil.isEmpty(fromUserList)) {
            arguments.add("file=" + file);
            arguments.add("IGNORE=Y");
            arguments.add("PARALLEL=10");
            arguments.add("log=" + logFileName);
            arguments.add("fromuser=" + fromUser);
            arguments.add("touser=" + toUser);
        }
        if (CollUtil.isNotEmpty(fromUserList)) {
            arguments.add("file=" + file);
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
            "WITH target_keys AS (" +
                    "SELECT OWNER, CONSTRAINT_NAME, TABLE_NAME FROM ALL_CONSTRAINTS " +
                    "WHERE OWNER = '%s' AND TABLE_NAME = '%s' AND CONSTRAINT_TYPE IN ('P', 'U')" +
                    ") " +
                    "SELECT fk.CONSTRAINT_NAME AS \"CONSTRAINT_NAME\", " +
                    "fk.TABLE_NAME AS \"forTableName\", fk.OWNER AS \"forSchema\", " +
                    "fk_col.COLUMN_NAME AS \"forTableColumn\", pk.TABLE_NAME AS \"table\", " +
                    "pk_col.COLUMN_NAME AS \"tableColumn\", pk.OWNER AS \"schema\", fk.STATUS AS \"status\" " +
                    "FROM target_keys pk " +
                    "JOIN ALL_CONSTRAINTS fk ON fk.R_OWNER = pk.OWNER " +
                    "AND fk.R_CONSTRAINT_NAME = pk.CONSTRAINT_NAME AND fk.CONSTRAINT_TYPE = 'R' " +
                    "JOIN ALL_CONS_COLUMNS fk_col ON fk_col.OWNER = fk.OWNER " +
                    "AND fk_col.CONSTRAINT_NAME = fk.CONSTRAINT_NAME AND fk_col.TABLE_NAME = fk.TABLE_NAME " +
                    "JOIN ALL_CONS_COLUMNS pk_col ON pk_col.OWNER = pk.OWNER " +
                    "AND pk_col.CONSTRAINT_NAME = pk.CONSTRAINT_NAME AND pk_col.TABLE_NAME = pk.TABLE_NAME " +
                    "AND pk_col.POSITION = fk_col.POSITION " +
                    "ORDER BY fk.TABLE_NAME, fk.CONSTRAINT_NAME, fk_col.POSITION";

    static String buildReferencedForeignKeySql(String schemaName, String tableName) {
        return String.format(REFERENCED_FOREIGN_KEY_SQL,
                schemaName.replace("'", "''"), tableName.replace("'", "''"));
    }

    @Override
    public List<ForeignData> beiForeGnKey(Connection connection, String schemaName, String tableName) {
        List<ForeignData> foreignKeys = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, buildReferencedForeignKeySql(schemaName, tableName), RELATION_QUERY_TIMEOUT_SECONDS, resultSet -> {
            while (resultSet.next()) {
                ForeignData foreignData = new ForeignData();
                foreignData.setSchemaName(resultSet.getString("schema"));
                foreignData.setForeignSchemaName(resultSet.getString("forSchema"));
                foreignData.setForeignTableName(resultSet.getString("forTableName"));
                foreignData.setTableName(resultSet.getString("table"));
                foreignData.setStatus(resultSet.getString("status"));
                foreignData.setColumn(resultSet.getString("tableColumn"));
                foreignData.setForeignColumnName(resultSet.getString("forTableColumn"));
                foreignData.setConstraintName(resultSet.getString("CONSTRAINT_NAME"));
                foreignKeys.add(foreignData);
            }
            return foreignKeys;
        });
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
                foreignData.setStatus(getConStatus(connection, tableName, schemaName, resultSet.getString("CONSTRAINT_NAME")));
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

    private static String SELECT_TAB_COLS = " SELECT " +
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
            " ATC.CHAR_USED " +
            " FROM " +
            " ALL_TAB_COLUMNS ATC, " +
            " ALL_COL_COMMENTS ACC " +
            " WHERE " +
            " ATC.OWNER       = ACC.SCHEMA_NAME " +
            " AND ATC.TABLE_NAME  = ACC.TABLE_NAME " +
            " AND ATC.COLUMN_NAME = ACC.COLUMN_NAME " +
            " AND ACC.SCHEMA_NAME = '%s' " +
            " AND ACC.TABLE_NAME  = '%s' " +
            " ORDER BY ATC.COLUMN_ID ";

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TAB_COLS, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            List<String> primaryKeyColumns = null;
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
                    primaryKeyColumns = getPrimaryKey(connection, owner, tableName);
                    incrementColumns = getAutoIncrement(connection, owner, tableName);
                    isFirst = false;
                }
                tableColumn.setPrimaryKey(primaryKeyColumns.contains(columnName));
                // 查询自增序列
                tableColumn.setAutoIncrement(incrementColumns.contains(columnName));
                tableColumns.add(tableColumn);
            }
            return tableColumns;
        });
    }

    private static String SELECT_TAB_AUTO_INCREMENT = "SELECT\n" +
            "        B.OWNER        ,\n" +
            "        B.TABLE_NAME   ,\n" +
            "        A.NAME COL_NAME,\n" +
            "        A.TYPE$\n" +
            "FROM\n" +
            "        SYS.SYSCOLUMNS A,\n" +
            "        ALL_TABLES B    ,\n" +
            "        SYS.SYSOBJECTS C\n" +
            "WHERE\n" +
            "        A.INFO2      =1\n" +
            "    AND A.ID         =C.ID\n" +
            "    AND C.NAME       = B.TABLE_NAME\n" +
            "    AND B.OWNER      = '%s'\n" +
            "    AND B.TABLE_NAME = '%s'";

    private List<String> getAutoIncrement(Connection connection, String owner, String tableName) {
        String sql = String.format(SELECT_TAB_AUTO_INCREMENT, owner, tableName);
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
    }

    private static String SELECT_TAB_PRIMARY_KEY = "SELECT\n" +
            "        COLS.TABLE_NAME,\n" +
            "        COLS.COLUMN_NAME\n" +
            "FROM\n" +
            "        ALL_CONS_COLUMNS COLS\n" +
            "JOIN ALL_CONSTRAINTS CONS\n" +
            "ON\n" +
            "        COLS.CONSTRAINT_NAME = CONS.CONSTRAINT_NAME\n" +
            "WHERE\n" +
            "        CONS.CONSTRAINT_TYPE = 'P'\n" +
            "    AND COLS.OWNER           = '%s'\n" +
            "    AND COLS.TABLE_NAME      = '%s'";

    private List<String> getPrimaryKey(Connection connection, String owner, String tableName) {
        String sql = String.format(SELECT_TAB_PRIMARY_KEY, owner, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            List<String> primaryKeyColumns = Lists.newArrayList();
            while (resultSet.next()) {
                final String colName = resultSet.getString("COLUMN_NAME");
                primaryKeyColumns.add(colName);
            }
            return primaryKeyColumns;
        });
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


    private static String SELECT_TABLE_VIEW_SQL = "select object_name, owner, status, created, last_ddl_time from sys.all_objects o where o.object_type = 'VIEW' and o.owner = '%s'";

    @Override
    public List<Table> tableViews(Connection connection, String databaseName, String schemaName) {
        List<Table> tableSpaces = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(SELECT_TABLE_VIEW_SQL, schemaName), resultSet -> {
            while (resultSet.next()) {
                Table tableSpace = new Table();
                TableDetails tableDetails = new TableDetails();
                tableDetails.setName(resultSet.getString("object_name"));
                tableDetails.setSchema(resultSet.getString("owner"));
                tableDetails.setValid(resultSet.getString("status"));
                tableDetails.setCreated(resultSet.getString("created"));
                tableDetails.setLastDDL(resultSet.getString("last_ddl_time"));
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
