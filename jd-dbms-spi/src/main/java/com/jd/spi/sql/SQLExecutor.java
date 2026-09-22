package com.jd.spi.sql;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.parser.ParserException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jd.common.constant.GenConstants;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.enums.DataSourceTypeEnum;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import com.jd.common.tools.common.util.I18nUtils;
import com.jd.spi.CommandExecutor;
import com.jd.spi.MetaData;
import com.jd.spi.ValueHandler;
import com.jd.spi.enums.DataTypeEnum;
import com.jd.spi.enums.SqlTypeEnum;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.Command;
import com.jd.spi.model.ConnectionVo;
import com.jd.spi.model.Database;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Function;
import com.jd.spi.model.Header;
import com.jd.spi.model.Procedure;
import com.jd.spi.model.Schema;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableIndex;
import com.jd.spi.model.TableIndexColumn;
import com.jd.spi.model.Type;
import com.jd.spi.util.JdbcUtils;
import com.jd.spi.util.ResultSetUtils;
import com.jd.spi.util.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.util.Assert;

import javax.validation.constraints.NotEmpty;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dbhub 统一数据库连接管理
 *
 * @author jipengfei
 */
@Slf4j
public class SQLExecutor implements CommandExecutor {

    private static final long SESSION_TIMEOUT_MILLIS = 30L * 60L * 1000L;
    private static final int QUERY_TIMEOUT_SECONDS = 60;
    private static final int MAX_QUERY_TIMEOUT_SECONDS = 300;


    // 存储所有活跃的事务（session_id → Connection）
    private static final Map<String, ConnectionVo> ACTIVE_SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, RunningStatement> RUNNING_STATEMENTS = new ConcurrentHashMap<>();
    private static final Set<String> CANCEL_REQUESTS = ConcurrentHashMap.newKeySet();

    /**
     * Singleton instance of SQLExecutor.
     */
    private static final SQLExecutor INSTANCE = new SQLExecutor();

    private static final class RunningStatement {
        private final Statement statement;
        private final ConnectionVo owner = new ConnectionVo();

        private RunningStatement(Statement statement, ConnectInfo connectInfo) {
            this.statement = statement;
            this.owner.bind(connectInfo);
        }

        private boolean ownedBy(ConnectInfo connectInfo) {
            return owner.ownedBy(connectInfo);
        }
    }



    public SQLExecutor() {
    }

    public static SQLExecutor getInstance() {
        return INSTANCE;
    }


    public <R> R execute(Connection connection, String sql, ResultSetFunction<R> function) {
        log.debug("Executing SQL (length={})", StringUtils.length(sql));
        try (Statement stmt = connection.createStatement();) {
            configureStatement(stmt);
            boolean query = stmt.execute(sql);
            // Represents the query
            if (query) {
                try (ResultSet rs = stmt.getResultSet();) {
                    return function.apply(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }


    /**
     * 设置blob数据在执行
     * @param connection
     * @param sql
     * @param blobValues
     * @return ExecuteResult
     */
    public ExecuteResult executeBlob(Connection connection, String sql, @NotEmpty List<String> blobValues) {
        // 注意 PreparedStatement中的SQL的VALUE 设置只支持对占位符[?]设置替换
        log.debug("Executing BLOB SQL (length={})", StringUtils.length(sql));
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            configureStatement(stmt);
            for (int i = 0; i < blobValues.size(); i++) {
                if (StrUtil.isNotBlank(blobValues.get(i))) {
                    stmt.setBytes(i + 1, Base64.getDecoder().decode(blobValues.get(i)));
                } else {
                    stmt.setBytes(i + 1, null);
                }
            }
            ExecuteResult executeResult = ExecuteResult.builder().sql(sql).success(Boolean.TRUE).build();
            int i = stmt.executeUpdate();
            // Represents the query
            if (i < 0) {
                executeResult.setSuccess(false);
            }
            return executeResult;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void execute(Connection connection, String sql, ResultSetConsumer consumer) {
        log.debug("Executing SQL (length={})", StringUtils.length(sql));
        try (Statement stmt = connection.createStatement()) {
            configureStatement(stmt);
            boolean query = stmt.execute(sql);
            // Represents the query
            if (query) {
                try (ResultSet rs = stmt.getResultSet();) {
                    consumer.accept(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void execute(Connection connection, String sql, Consumer<List<Header>> headerConsumer,
                        Consumer<List<String>> rowConsumer, ValueHandler valueHandler) {
        execute(connection, sql, headerConsumer, rowConsumer, true, valueHandler);
    }

    public void execute(Connection connection, String sql, Consumer<List<Header>> headerConsumer,
                        Consumer<List<String>> rowConsumer, boolean limitSize, ValueHandler valueHandler) {
        execute(connection, sql, headerConsumer, rowConsumer, limitSize, null, valueHandler);
    }

    public void execute(Connection connection, String sql, Consumer<List<Header>> headerConsumer,
                        Consumer<List<String>> rowConsumer, boolean limitSize, Integer maxRows,
                        ValueHandler valueHandler) {
        Assert.notNull(sql, "SQL must not be null");
        log.debug("Executing SQL (length={})", StringUtils.length(sql));
        try (Statement stmt = connection.createStatement();) {
            configureStatement(stmt);
            if (maxRows != null && maxRows > 0) {
                stmt.setMaxRows(maxRows);
            }
            boolean query = stmt.execute(sql);
            // Represents the query
            if (query) {
                ResultSet rs = null;
                try {
                    rs = stmt.getResultSet();
                    // Get how many columns
                    ResultSetMetaData resultSetMetaData = rs.getMetaData();
                    int col = resultSetMetaData.getColumnCount();

                    // Get header information
                    List<Header> headerList = Lists.newArrayListWithExpectedSize(col);
                    for (int i = 1; i <= col; i++) {
                        if (!"CAHT2DB_AUTO_ROW_ID".equals(ResultSetUtils.getColumnName(resultSetMetaData, i))) {
                            headerList.add(Header.builder()
                                    .dataType(JdbcUtils.resolveDataType(
                                            resultSetMetaData.getColumnTypeName(i), resultSetMetaData.getColumnType(i)).getCode())
                                    .name(ResultSetUtils.getColumnName(resultSetMetaData, i))
                                    .build());
                        }
                    }
                    headerConsumer.accept(headerList);

                    while (rs.next()) {
                        List<String> row = Lists.newArrayListWithExpectedSize(col);
                        for (int i = 1; i <= headerList.size(); i++) {
                            row.add(valueHandler.getString(rs, i, limitSize));
                        }
                        rowConsumer.accept(row);
                    }
                } finally {
                    JdbcUtils.closeResultSet(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean executeTable(Connection connection, String sql, Consumer<List<Header>> headerConsumer,
                                Consumer<List<String>> rowConsumer, boolean limitSize, ValueHandler valueHandler) {
        Assert.notNull(sql, "SQL must not be null");
        log.debug("Executing SQL (length={})", StringUtils.length(sql));
        boolean hasNext = false;
        try (Statement stmt = connection.createStatement();) {
            configureStatement(stmt);
            boolean query = stmt.execute(sql);
            // Represents the query
            if (query) {
                ResultSet rs = null;
                try {
                    rs = stmt.getResultSet();
                    // Get how many columns
                    ResultSetMetaData resultSetMetaData = rs.getMetaData();
                    int col = resultSetMetaData.getColumnCount();

                    // Get header information
                    List<Header> headerList = Lists.newArrayListWithExpectedSize(col);
                    for (int i = 1; i <= col; i++) {
                        headerList.add(Header.builder()
                                .dataType(JdbcUtils.resolveDataType(
                                        resultSetMetaData.getColumnTypeName(i), resultSetMetaData.getColumnType(i)).getCode())
                                .name(ResultSetUtils.getColumnName(resultSetMetaData, i))
                                .build());
                    }
                    headerConsumer.accept(headerList);

                    while (rs.next()) {
                        List<String> row = Lists.newArrayListWithExpectedSize(col);
                        for (int i = 1; i <= col; i++) {
                            row.add(valueHandler.getString(rs, i, limitSize));
                        }
                        rowConsumer.accept(row);
                        hasNext = true;
                    }
                } finally {
                    JdbcUtils.closeResultSet(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return hasNext;
    }

    /**
     * Execute SQL
     *
     * @param sql
     * @return
     * @throws SQLException
     */
    public ExecuteResult execute(final String sql, Connection connection, ValueHandler valueHandler)
            throws SQLException {
        return execute(sql, connection, true, null, null, valueHandler);
    }

    @Override
    public ExecuteResult executeUpdate(String sql, Connection connection, int n)
            throws SQLException {
        Assert.notNull(sql, "SQL must not be null");
        log.debug("Executing SQL (length={})", StringUtils.length(sql));
        // connection.setAutoCommit(false);
        ExecuteResult executeResult = ExecuteResult.builder().sql(sql).success(Boolean.TRUE).build();
        try (Statement stmt = connection.createStatement()) {
            configureStatement(stmt);
            int affectedRows = stmt.executeUpdate(sql);
            if (affectedRows != n) {
                executeResult.setSuccess(false);
                executeResult.setMessage("Update error " + sql + " update affectedRows = " + affectedRows
                        + ", Each SQL statement should update no more than one record. Please use a unique key for "
                        + "updates.");
                // connection.rollback();
            }
        }
        return executeResult;
    }

    @Override
    public List<ExecuteResult> executeSelectTable(Command command) {
        MetaData metaData = Chat2DBContext.getMetaData();
        String tableName = metaData.getMetaDataName(command.getDatabaseName(), command.getSchemaName(),
                command.getTableName());
        String sql = "select * from " + tableName;
        command.setScript(sql);
        log.debug("Executing table query (length={})", StringUtils.length(sql));
        return execute(command);
    }


    /**
     * Executes the given SQL query using the provided connection.
     *
     * @param sql          The SQL query to be executed.
     * @param connection   The database connection to use for the query.
     * @param limitRowSize Flag to indicate if row size should be limited.
     * @param offset       The starting point of rows to fetch in the result set.
     * @param count        The number of rows to fetch from the result set.
     * @param valueHandler Handles the processing of the result set values.
     * @return ExecuteResult containing the result of the execution.
     * @throws SQLException If there is any SQL related error.
     */
    public ExecuteResult execute(final String sql, Connection connection, boolean limitRowSize, Integer offset,
                                 Integer count, ValueHandler valueHandler)
            throws SQLException {
        return execute(sql, connection, limitRowSize, offset, count, valueHandler, null, null);
    }

    public ExecuteResult execute(final String sql, Connection connection, boolean limitRowSize, Integer offset,
                                 Integer count, ValueHandler valueHandler, String executionId,
                                 Integer queryTimeoutSeconds)
            throws SQLException {
        Assert.notNull(sql, "SQL must not be null");
        log.debug("Executing SQL (length={})", StringUtils.length(sql));

        String type = Chat2DBContext.getConnectInfo().getDbType();
        ExecuteResult executeResult = ExecuteResult.builder().sql(sql).success(Boolean.TRUE).build();
//        log.info("之前事务提交状态==> {},", connection.getAutoCommit());
        try (Statement stmt = connection.createStatement()) {
            stmt.setFetchSize(EasyToolsConstant.MAX_PAGE_SIZE);
            configureStatement(stmt, queryTimeoutSeconds);
            if (offset != null && count != null) {
                stmt.setMaxRows(offset + count);
            }

            RunningStatement runningStatement = registerRunningStatement(executionId, stmt);
            try {

            TimeInterval timeInterval = new TimeInterval();
            boolean query = stmt.execute(sql);
//            log.info("之后事务提交状态==> {},", connection.getAutoCommit());
            executeResult.setDescription(I18nUtils.getMessage("sqlResult.success"));
            // Represents the query
            if (query) {
                ResultSet rs = null;
                try {
                    rs = stmt.getResultSet();
                    // Get how many columns
                    ResultSetMetaData resultSetMetaData = rs.getMetaData();
                    int col = resultSetMetaData.getColumnCount();

                    // Get header information
                    List<Header> headerList = Lists.newArrayListWithExpectedSize(col);
                    executeResult.setHeaderList(headerList);
                    int chat2dbAutoRowIdIndex = -1;// Row paging ID automatically generated by chat2db

                    boolean isMongoMap = false;
                    for (int i = 1; i <= col; i++) {
                        String name = ResultSetUtils.getColumnName(resultSetMetaData, i);
                        // The returned map is from mongodb, and you need to parse the map yourself
                        if (DataSourceTypeEnum.MONGODB.getCode().equals(type) && i == 1 && "map".equals(name)) {
                            isMongoMap = true;
                            break;
                        }
                        if ("CAHT2DB_AUTO_ROW_ID".equals(name)) {
                            chat2dbAutoRowIdIndex = i;
                            continue;
                        }
                        String dataType = JdbcUtils.resolveDataType(
                                resultSetMetaData.getColumnTypeName(i), resultSetMetaData.getColumnType(i)).getCode();
                        headerList.add(Header.builder()
                                .dataType(dataType)
                                .name(name)
                                .build());
                    }

                    // Get data information
                    List<List<String>> dataList = Lists.newArrayList();
                    executeResult.setDataList(dataList);

                    Map<String, Header> headerListMap = null;
                    List<Map<String, String>> dataListMap = null;
                    if (isMongoMap) {
                        headerListMap = Maps.newLinkedHashMap();
                        dataListMap = Lists.newArrayList();
                    }

                    if (offset == null || offset < 0) {
                        offset = 0;
                    }
                    int rowNumber = 0;
                    int rowCount = 1;
                    while (rs.next()) {
                        if (rowNumber++ < offset) {
                            continue;
                        }
                        if (!isMongoMap) {
                            List<String> row = Lists.newArrayListWithExpectedSize(col);
                            dataList.add(row);
                            for (int i = 1; i <= col; i++) {
                                if (chat2dbAutoRowIdIndex == i) {
                                    continue;
                                }
                                row.add(valueHandler.getString(rs, i, limitRowSize));
                            }
                        } else {
                            for (int i = 1; i <= col; i++) {
                                Object o = rs.getObject(i);
                                Map<String, String> row = Maps.newHashMap();
                                dataListMap.add(row);
                                if (o instanceof Document) {
                                    Document document = (Document) o;
                                    for (String string : document.keySet()) {
                                        headerListMap.computeIfAbsent(string, k -> Header.builder()
                                                .dataType("string")
                                                .name(string)
                                                .build());
                                        row.put(string, Objects.toString(document.get(string)));
                                    }
                                } else {
                                    headerListMap.computeIfAbsent("_unknown", k -> Header.builder()
                                            .dataType("string")
                                            .name("_unknown")
                                            .build());
                                    row.put("_unknown", Objects.toString(o));
                                }
                            }
                        }
                        if (count != null && count > 0 && rowCount++ >= count) {
                            break;
                        }
                    }

                    if (isMongoMap) {
                        headerList.addAll(new ArrayList<>(headerListMap.values()));
                        for (Map<String, String> stringStringMap : dataListMap) {
                            List<String> dataTempList = Lists.newArrayList();
                            dataList.add(dataTempList);
                            for (Header value : headerListMap.values()) {
                                dataTempList.add(stringStringMap.get(value.getName()));
                            }
                        }
                    }

                    executeResult.setDuration(timeInterval.interval());
                } finally {
                    JdbcUtils.closeResultSet(rs);
                }
            } else {
                executeResult.setDuration(timeInterval.interval());
                // Modification or other
                executeResult.setUpdateCount(stmt.getUpdateCount());
            }
            } finally {
                unregisterRunningStatement(executionId, runningStatement);
            }
        }
        return executeResult;
    }

    @Override
    public ExecuteResult JDBCExecute(Command command) {
        ExecuteResult result = ExecuteResult.builder()
                .sql(command.getScript())
                .originalSql(command.getScript())
                .success(Boolean.FALSE)
                .build();
        Connection connection = Chat2DBContext.getConnection();
        Statement statement = null;
        try {
            statement = connection.createStatement();
            statement.execute(command.getScript());
            result.setSuccess(Boolean.TRUE);
            result.setMessage("操作成功！");
        } catch (SQLException e) {
            String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            result.setMessage(message);
        }finally {
            if(statement!=null){
                try{
                    statement.close();
                    if(connection!=null){
                        try{
                            if(connection!=null){
                                connection.close();
                            }
                        }catch (SQLException e){
                            log.error("close connection error,error info:{}",e);
                        }
                    }
                }catch (SQLException e){
                    log.error("close statement error,error info:{}",e);
                }
            }
        }
        return result;
    }


    /**
     * Execute SQL
     *
     * @param connection
     * @param sql
     * @return
     * @throws SQLException
     */
    public ExecuteResult execute(Connection connection, String sql, ValueHandler valueHandler) throws SQLException {
        return execute(sql, connection, true, null, null, valueHandler);
    }

    public ExecuteResult execute(Connection connection, String sql) throws SQLException {
        return execute(sql, connection, true, null, null, new DefaultValueHandler());
    }

    /**
     * Get all databases
     *
     * @param connection
     * @return
     */
    public List<Database> databases(Connection connection) {
        try (ResultSet resultSet = connection.getMetaData().getCatalogs();) {
            List<Database> databases = ResultSetUtils.toObjectList(resultSet, Database.class);
            if (CollectionUtils.isEmpty(databases)) {
                return databases;
            }
            return databases.stream().filter(database -> database.getName() != null).collect(Collectors.toList());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the schema names available in this database. The results are ordered by TABLE_CATALOG and TABLE_SCHEM.
     * The schema columns are:
     * TABLE_SCHEM String => schema name
     * TABLE_CATALOG String => catalog name (may be null)
     * Params:
     * catalog – a catalog name; must match the catalog name as it is stored in the database;"" retrieves those without
     * a catalog; null means catalog name should not be used to narrow down the search. schemaPattern – a schema name;
     * must match the schema name as it is stored in the database; null means schema name should not be used to narrow
     * down the search.
     * Returns:
     * a ResultSet object in which each row is a schema description
     * Throws:
     * SQLException – if a database access error occurs
     * Since:
     * 1.6
     * See Also:
     * getSearchStringEscape
     */
    public List<Schema> schemas(Connection connection, String databaseName, String schemaName) {
        if (StringUtils.isEmpty(databaseName) && StringUtils.isEmpty(schemaName)) {
            try (ResultSet resultSet = connection.getMetaData().getSchemas()) {
                return ResultSetUtils.toObjectList(resultSet, Schema.class);
            } catch (SQLException e) {
                throw new RuntimeException("Get schemas error", e);
            }
        }
        try (ResultSet resultSet = connection.getMetaData().getSchemas(databaseName, schemaName)) {
            return ResultSetUtils.toObjectList(resultSet, Schema.class);
        } catch (SQLException e) {
            throw new RuntimeException("Get schemas error", e);
        }
    }

    /**
     * Get all database tables
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @param types
     * @return
     */
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName,
                              String types[]) {

        try {
            DatabaseMetaData metadata = connection.getMetaData();
            ResultSet resultSet = metadata.getTables(databaseName, schemaName, tableName,
                    types);
            // If connection is mysql
            if ("MySQL".equalsIgnoreCase(metadata.getDatabaseProductName())) {
                // Get the comment of mysql table
                List<Table> tables = ResultSetUtils.toObjectList(resultSet, Table.class);
                if (CollectionUtils.isNotEmpty(tables)) {
                    for (Table table : tables) {
                        String sql = "show table status where name = '" + table.getName() + "'";
                        try (Statement stmt = connection.createStatement()) {
                            boolean query = stmt.execute(sql);
                            if (query) {
                                try (ResultSet rs = stmt.getResultSet();) {
                                    while (rs.next()) {
                                        table.setComment(rs.getString("Comment"));
                                    }
                                }
                            }
                        }
                    }

                    return tables;
                }
            }
            return ResultSetUtils.toObjectList(resultSet, Table.class);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get all database table columns
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @param columnName
     * @return
     */
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String
            tableName,
                                     String columnName) {
        try (ResultSet resultSet = connection.getMetaData().getColumns(databaseName, schemaName, tableName,
                columnName)) {
            return ResultSetUtils.toObjectList(resultSet, TableColumn.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * get all table index info
     *
     * @param connection   connection
     * @param databaseName databaseName of the index
     * @param schemaName   schemaName of the index
     * @param tableName    tableName of the index
     * @return List<TableIndex> table index list
     */
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        List<TableIndex> tableIndices = Lists.newArrayList();
        try (ResultSet resultSet = connection.getMetaData().getIndexInfo(databaseName, schemaName, tableName,
                false,
                false)) {
            List<TableIndexColumn> tableIndexColumns = ResultSetUtils.toObjectList(resultSet, TableIndexColumn.class);
            tableIndexColumns.stream().filter(c -> c.getIndexName() != null).collect(
                            Collectors.groupingBy(TableIndexColumn::getIndexName)).entrySet()
                    .stream().forEach(entry -> {
                        TableIndex tableIndex = new TableIndex();
                        TableIndexColumn column = entry.getValue().get(0);
                        tableIndex.setName(entry.getKey());
                        tableIndex.setTableName(column.getTableName());
                        tableIndex.setSchemaName(column.getSchemaName());
                        tableIndex.setDatabaseName(column.getDatabaseName());
                        tableIndex.setUnique(!column.getNonUnique());
                        tableIndex.setColumnList(entry.getValue());
                        tableIndices.add(tableIndex);
                    });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return tableIndices;
    }

    /**
     * Get all functions available in a catalog.
     *
     * @param connection   connection
     * @param databaseName databaseName of the function
     * @param schemaName   schemaName of the function
     * @return List<Function>
     */
    public List<Function> functions(Connection connection, String databaseName,
                                    String schemaName) {
        try (ResultSet resultSet = connection.getMetaData().getFunctions(databaseName, schemaName, null);) {
            return ResultSetUtils.toObjectList(resultSet, Function.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves a description of all the data types supported by this database. They are ordered by DATA_TYPE and then
     * by how closely the data type maps to the corresponding JDBC SQL type.
     * If the database supports SQL distinct types, then getTypeInfo() will return a single row with a TYPE_NAME of
     * DISTINCT and a DATA_TYPE of Types.DISTINCT. If the database supports SQL structured types, then getTypeInfo()
     * will return a single row with a TYPE_NAME of STRUCT and a DATA_TYPE of Types.STRUCT.
     * If SQL distinct or structured types are supported, then information on the individual types may be obtained from
     * the getUDTs() method.
     *
     * @param connection connection
     * @return List<Function>
     */
    public List<Type> types(Connection connection) {
        try (ResultSet resultSet = connection.getMetaData().getTypeInfo();) {
            return ResultSetUtils.toObjectList(resultSet, Type.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * procedure list
     *
     * @param connection   connection
     * @param databaseName databaseName
     * @param schemaName   schemaName
     * @return List<Procedure>
     */
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        try (ResultSet resultSet = connection.getMetaData().getProcedures(databaseName, schemaName, null)) {
            return ResultSetUtils.toObjectList(resultSet, Procedure.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getDbVersion(Connection connection) {
        try {
            String dbVersion = connection.getMetaData().getDatabaseProductVersion();
            return dbVersion;
        } catch (Exception e) {
            log.error("get db version error", e);
        }
        return "";
    }

    /**
     * 删除会话信息
     * @param command 入参
     * @return boolean
     */
    @Override
    public boolean delSession(Command command) {
        if (Boolean.TRUE.equals(command.getIsAllDelete())) {
            log.info("回话删除开始,数量：" + ACTIVE_SESSIONS.size());
            ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
            for (Map.Entry<String, ConnectionVo> entry : ACTIVE_SESSIONS.entrySet()) {
                ConnectionVo connectionVo = entry.getValue();
                if (null != connectionVo && connectionVo.ownedBy(connectInfo) && null != connectionVo.getConnection()) {
                    try {
                        connectionVo.getConnection().close();
                        ACTIVE_SESSIONS.remove(entry.getKey(), connectionVo);
                    } catch (SQLException e) {
                        log.error("回话删除失败");
                        return false;
                    }
                }
            }
        } else {
            if (StrUtil.isNotBlank(command.getSessionId())) {
                ConnectionVo connectionVo = getOwnedSession(command.getSessionId());
                if (connectionVo.getConnection() != null) {
                    try {
                        log.info("回话删除,SESSION_ID：" + connectionVo.getSessionId());
                        connectionVo.getConnection().close();
                        ACTIVE_SESSIONS.remove(command.getSessionId());
                    } catch (SQLException e) {
                        log.error("回话删除失败");
                        return false;
                    }
                }
            }
        }
        return true;
    }


    /**
     * 提交会话信息
     * @param command 入参
     * @return boolean
     */
    @Override
    public boolean commitSession(Command command) {
        if (StrUtil.isNotBlank(command.getSessionId()) && command.getIsCommit()) {
            ConnectionVo connectionVo = getOwnedSession(command.getSessionId());
            if (null != connectionVo && null != connectionVo.getConnection()) {
                try {
                    connectionVo.getConnection().commit();
                    connectionVo.setSign(false);
                    ACTIVE_SESSIONS.put(command.getSessionId(), connectionVo);
                } catch (SQLException e) {
                    log.error("提交会话失败");
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * 回退会话信息
     * @param command 入参
     * @return boolean
     */
    @Override
    public boolean rollbackSession(Command command) {
        if (StrUtil.isNotBlank(command.getSessionId()) && command.getIsRollback()) {
            ConnectionVo connectionVo = getOwnedSession(command.getSessionId());
            if (null != connectionVo && null != connectionVo.getConnection()) {
                try {
                    connectionVo.getConnection().rollback();
                    connectionVo.setSign(false);
                    ACTIVE_SESSIONS.put(command.getSessionId(), connectionVo);
                } catch (SQLException e) {
                    log.error("回话回滚失败");
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public List<ExecuteResult> execute(Command command) {
        // parse sql
        String sql = command.getScript();
        if (StringUtils.isNotBlank(command.getOrderByColumn()) && command.getIsAsc()!=null) {
            String px = command.getIsAsc() ? "\" ASC" : "\" DESC";
            sql = "SELECT * FROM (" + sql + ") A order by A.\"" + command.getOrderByColumn() + px;
        }
        sql = sql.replace("&gt;", ">");
        sql = sql.replace("&lt;", "<");
        command.setScript(sql);
        List<ExecuteResult> result = new ArrayList<>();
        String type = Chat2DBContext.getConnectInfo().getDbType();
        DbType dbType = JdbcUtils.parse2DruidDbType(type);
        List<String> sqlList = new ArrayList<>();
        if(command.getIsExecuteCompile()){
            try {
                ExecuteResult executeResult =null;
                if(sql.toUpperCase(Locale.ROOT).contains("END;")){
                    String[] split =null;
                    if (sql.contains("end;")){
                        split =sql.split("end");
                    }else if(sql.contains("END;")){
                        split =sql.split("END;");
                    }
                    if (Objects.isNull(split)){
                        throw new BusinessException("过程编译SQL不合法");
                    }
                    for (int i=0;i < split.length;i++){
                        if (i==0){
                            executeResult = SQLExecutor.getInstance().execute(split[i] + " END;",Chat2DBContext.getConnection(),new DefaultValueHandler());
                        }else{
                            sqlList.add(split[i]);
                        }
                    }
                }
                result.add(executeResult);
            }catch (Exception e){
                log.warn("SQL解析失败（长度={}）", StringUtils.length(sql), e);
                ExecuteResult executeResult = new ExecuteResult();
                executeResult.setSuccess(Boolean.FALSE);
                executeResult.setMessage(e.getMessage());
                result.add(executeResult);
            }
        }else{
            //切分sql
            sqlList = SqlUtils.parse(command.getScript(),dbType);
        }
        if (CollectionUtils.isEmpty(sqlList)) {
            throw new BusinessException("dataSource.sqlAnalysisError");
        }
        // 解析SQL
        Boolean sign = parseSqlCommit(sqlList);
        command.setSign(sign);
        // Execute SQL
        try {
            for (String originalSql : sqlList) {
                try {
                    ExecuteResult executeResult = executeSQL(originalSql.replaceAll("\\. \\.", ".."), dbType, command);
                    // session会话ID
                    if (StrUtil.isNotBlank(executeResult.getSessionId())) {
                        command.setSessionId(executeResult.getSessionId());
                    }
                    result.add(executeResult);
                    if (Boolean.TRUE.equals(executeResult.getCancelled())) {
                        break;
                    }
                } catch (Exception e) {
                    log.warn("SQL执行失败（长度={}）", StringUtils.length(originalSql), e);
                    ExecuteResult executeResult = new ExecuteResult();
                    executeResult.setSuccess(Boolean.FALSE);
                    executeResult.setSql(originalSql);
                    executeResult.setOriginalSql(originalSql);
                    executeResult.setMessage(e.getMessage());
                    executeResult.setSign(false);
                    executeResult.setSessionId(command.getSessionId());
                    result.add(executeResult);
                    if (command.getIsErrorExecute()) {
                        break;
                    }
                }
            }
        } finally {
            if (StrUtil.isNotBlank(command.getExecutionId())) {
                CANCEL_REQUESTS.remove(command.getExecutionId());
            }
        }
        return result;
    }

    private Boolean parseSqlCommit(List<String> sqlList) {
        for (String originalSql : sqlList) {
            String sql = originalSql.trim().toLowerCase(Locale.ROOT);
            if (Pattern.matches("^insert\\s+.*", sql)) {
                return true;
            } else
            if (Pattern.matches("^update\\s+.*", sql)) {
                return true;
            } else
            if (Pattern.matches("^delete\\s+.*", sql)) {
                return true;
            }
        }
        return false;
    }

    private ExecuteResult executeSQL(String originalSql, DbType dbType, Command param) {
        int pageNo = 1;
        int pageSize = 0;
        Integer offset = null;
        Integer count = null;
        String sqlType = SqlTypeEnum.UNKNOWN.getCode();
        // 解析sql
        String type = Chat2DBContext.getConnectInfo().getDbType();
        boolean supportDruid = !DataSourceTypeEnum.MONGODB.getCode().equals(type);
        // 解析sql分页
        SQLStatement sqlStatement = null;
        if (supportDruid) {
            try {
                sqlStatement = SQLUtils.parseSingleStatement(originalSql, dbType);
            } catch (ParserException e) {
                log.warn("分页SQL执行失败（长度={}）", StringUtils.length(originalSql), e);
            }
        }
        if (!supportDruid || (sqlStatement instanceof SQLSelectStatement)) {
            pageNo = normalizePageNo(param.getPageNo());
            pageSize = Boolean.TRUE.equals(param.getPageSizeAll())
                    ? EasyToolsConstant.MAX_PAGE_SIZE
                    : normalizePageSize(param.getPageSize());
            long calculatedOffset = (long) (pageNo - 1) * pageSize;
            if (calculatedOffset > Integer.MAX_VALUE) {
                throw new BusinessException("分页页码过大");
            }
            offset = (int) calculatedOffset;
            count = pageSize + 1;
            sqlType = SqlTypeEnum.SELECT.getCode();
        }

        ExecuteResult executeResult = null;
        if (SqlTypeEnum.SELECT.getCode().equals(sqlType) && !SqlUtils.hasPageLimit(originalSql, dbType)) {
            String pageLimit = Chat2DBContext.getSqlBuilder().pageLimit(originalSql, offset, pageNo, count);
            if (StringUtils.isNotBlank(pageLimit)) {
//                executeResult = execute(originalSql, offset, count);
                if (param.getQueryTemplate()) {
                    executeResult = execute(pageLimit, 0, count, param.getIsCommit(), param.getSessionId(),
                            param.getSign(), param.getExecutionId(), param.getQueryTimeoutSeconds());
                } else {
                    executeResult = execute(originalSql, offset, count, param.getExecutionId(),
                            param.getQueryTimeoutSeconds());
                }
            }
        }
        if (executeResult == null || (!executeResult.getSuccess()
                && !Boolean.TRUE.equals(executeResult.getCancelled())
                && !Boolean.TRUE.equals(executeResult.getTimedOut()))) {
//            executeResult = execute(originalSql, offset, count);
            if (param.getQueryTemplate()) {
                executeResult = execute(originalSql, offset, count, param.getIsCommit(), param.getSessionId(),
                        param.getSign(), param.getExecutionId(), param.getQueryTimeoutSeconds());
            } else {
                executeResult = execute(originalSql, offset, count, param.getExecutionId(),
                        param.getQueryTimeoutSeconds());
            }
        }
        executeResult.setSqlType(sqlType);
        executeResult.setOriginalSql(originalSql);
        boolean supportJsqlParser = !DataSourceTypeEnum.MONGODB.getCode().equals(type);
        if (supportJsqlParser) {
            try {
                SqlUtils.buildCanEditResult(originalSql, dbType, executeResult);
            } catch (Exception e) {
                log.warn("buildCanEditResult error", e);
            }
        }
        if (StrUtil.isNotBlank(param.getTableName())) {
            executeResult.setTableName("\"" + param.getSchemaName() + "\".\"" + param.getTableName() + "\"");
        }
        if (SqlTypeEnum.SELECT.getCode().equals(sqlType)) {
            boolean hasNextPage = CollectionUtils.size(executeResult.getDataList()) > pageSize;
            if (hasNextPage) {
                executeResult.getDataList().remove(executeResult.getDataList().size() - 1);
            }
            executeResult.setPageNo(pageNo);
            executeResult.setPageSize(pageSize);
            executeResult.setHasNextPage(hasNextPage);
        } else {
            executeResult.setPageNo(pageNo);
            executeResult.setPageSize(CollectionUtils.size(executeResult.getDataList()));
            executeResult.setHasNextPage(Boolean.FALSE);
        }
        List<Header> headers = executeResult.getHeaderList();
        Header rowNumberHeader = Header.builder()
                .name(I18nUtils.getMessage("sqlResult.rowNumber"))
                .dataType(DataTypeEnum.CHAT2DB_ROW_NUMBER
                        .getCode()).build();
        executeResult.setHeaderList(EasyCollectionUtils.union(Arrays.asList(rowNumberHeader), headers));
        if (CollUtil.isNotEmpty(executeResult.getHeaderList()) && executeResult.getHeaderList().size() > 1 && SqlTypeEnum.UNKNOWN.getCode().equals(executeResult.getSqlType())) {
            executeResult.setSqlType(SqlTypeEnum.SELECT.getCode());
        }
        if (executeResult.getDataList() != null) {
            int rowNumberIncrement = 1 + Math.max(pageNo - 1, 0) * pageSize;
            for (int i = 0; i < executeResult.getDataList().size(); i++) {
                List<String> row = executeResult.getDataList().get(i);
                List<String> newRow = Lists.newArrayListWithExpectedSize(row.size() + 1);
                newRow.add(Integer.toString(i + rowNumberIncrement));
                newRow.addAll(row);
                executeResult.getDataList().set(i, newRow);
            }
        }
        // 仅对 SELECT 统计总数；控制台可通过 skipCount 跳过额外的全量查询。
        if (SqlTypeEnum.SELECT.getCode().equals(sqlType)) {
            if (Boolean.TRUE.equals(param.getSkipCount())) {
                long lowerBound = (long) Math.max(pageNo - 1, 0) * pageSize
                        + CollectionUtils.size(executeResult.getDataList())
                        + (Boolean.TRUE.equals(executeResult.getHasNextPage()) ? 1 : 0);
                executeResult.setTotal(Long.toString(lowerBound));
                executeResult.setTotalExact(!Boolean.TRUE.equals(executeResult.getHasNextPage()));
            } else {
                String sql = "select count(*) as COUNT FROM (" +  originalSql + ") a";
                executeResult.setTotal(getCount(sql));
                executeResult.setTotalExact(StrUtil.isNotBlank(executeResult.getTotal()));
            }
        }
        if (GenConstants.ZERO_STR.equals(executeResult.getTotal()) || StrUtil.isBlank(executeResult.getTotal())) {
            if (CollUtil.isEmpty(executeResult.getDataList())) {
                executeResult.setTotal(null);
            } else {
                executeResult.setTotal(executeResult.getDataList().size()+"");
                executeResult.setTotalExact(Boolean.FALSE);
            }
        }
        executeResult.setFuzzyTotal(executeResult.getTotal());
        return executeResult;
    }

    public String getCount(String sql) {
        try {
            return SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql, resultSet -> {
                String count = "";
                while (resultSet.next()) {
                    count =  resultSet.getString("COUNT");
                }
                return count;
            });
        } catch (Exception e) {
            return null;
        }
    }

    private String calculateFuzzyTotal(int pageNo, int pageSize, ExecuteResult executeResult) {
        int dataSize = CollectionUtils.size(executeResult.getDataList());
        if (pageSize <= 0) {
            return Integer.toString(dataSize);
        }
        int fuzzyTotal = Math.max(pageNo - 1, 0) * pageSize + dataSize;
        if (dataSize < pageSize) {
            return Integer.toString(fuzzyTotal);
        }
        return executeResult.getTotal();
    }

    private ExecuteResult execute(String sql, Integer offset, Integer count) {
        return execute(sql, offset, count, null, null);
    }

    private ExecuteResult execute(String sql, Integer offset, Integer count, String executionId,
                                  Integer queryTimeoutSeconds) {
        ExecuteResult executeResult;
        try {
            ValueHandler valueHandler = Chat2DBContext.getMetaData().getValueHandler();
            executeResult = SQLExecutor.getInstance().execute(sql, Chat2DBContext.getConnection(), true, offset,
                    count, valueHandler, executionId, queryTimeoutSeconds);
        } catch (SQLException e) {
            log.warn("SQL执行失败（长度={}）", StringUtils.length(sql), e);
            executeResult = buildFailureResult(sql, e, executionId, null);
        }
        return executeResult;
    }



    private ExecuteResult execute(String sql, Integer offset, Integer count, Boolean isCommit, String sessionId,
                                  Boolean sign, String executionId, Integer queryTimeoutSeconds) {
        ExecuteResult executeResult;
        // 会话ID
        String session_id = UUID.fastUUID() + "@" + System.currentTimeMillis();
        ConnectionVo connectionVo = null;
        Connection connection = null;
        boolean newSession = StrUtil.isBlank(sessionId);
        try {
            if (StrUtil.isNotBlank(sessionId)) {
                connectionVo = getOwnedSession(sessionId);
                connection = connectionVo.getConnection();
                session_id = sessionId;
                if (connectionVo.getSign()) {
                    sign = true;
                }
                if (sign) {
                    connectionVo.setSign(true);
                    ACTIVE_SESSIONS.put(sessionId, connectionVo);
                }
            } else {
                connection = Chat2DBContext.getConnection();
                connectionVo = new ConnectionVo();
                connectionVo.setConnection(connection);
                connectionVo.setSign(sign);
                connectionVo.setSessionId(session_id);
                connectionVo.bind(Chat2DBContext.getConnectInfo());
                ACTIVE_SESSIONS.put(session_id, connectionVo);
            }
            // 清除无效的connection
            removeTimeoutConnection();
            connection.setAutoCommit(false);
            ValueHandler valueHandler = Chat2DBContext.getMetaData().getValueHandler();
            executeResult = SQLExecutor.getInstance().execute(sql, connection, true, offset, count,
                    valueHandler, executionId, queryTimeoutSeconds);
            executeResult.setSessionId(session_id);
            executeResult.setSign(sign);
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    log.warn("Rollback failed session after execute error", ex);
                }
            }
            log.warn("事务SQL执行失败（长度={}）", StringUtils.length(sql), e);
            executeResult = buildFailureResult(sql, e, executionId, session_id);
            if (connectionVo != null) {
                connectionVo.setSign(false);
            }
            if (newSession) {
                closeSession(session_id, connectionVo);
                executeResult.setSessionId(null);
            }
        }
        return executeResult;
    }

    private ExecuteResult buildFailureResult(String sql, SQLException exception, String executionId,
                                             String sessionId) {
        boolean cancelled = StrUtil.isNotBlank(executionId) && CANCEL_REQUESTS.contains(executionId);
        boolean timedOut = !cancelled && isQueryTimeout(exception);
        String message = cancelled
                ? "SQL 执行已取消"
                : timedOut ? "SQL 执行超时，请缩小查询范围或调整超时时间" : exception.getMessage();
        return ExecuteResult.builder()
                .sql(sql)
                .success(Boolean.FALSE)
                .message(message)
                .sessionId(sessionId)
                .cancelled(cancelled)
                .timedOut(timedOut)
                .build();
    }

    private boolean isQueryTimeout(SQLException exception) {
        if (exception instanceof SQLTimeoutException) {
            return true;
        }
        String message = StringUtils.defaultString(exception.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("timeout") || message.contains("timed out") || message.contains("超时");
    }

    static int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    static int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return EasyToolsConstant.MAX_PAGE_SIZE;
        }
        return Math.max(1, Math.min(pageSize, EasyToolsConstant.MAX_PAGE_SIZE));
    }

    static int normalizeQueryTimeout(Integer queryTimeoutSeconds) {
        if (queryTimeoutSeconds == null) {
            return QUERY_TIMEOUT_SECONDS;
        }
        return Math.max(1, Math.min(queryTimeoutSeconds, MAX_QUERY_TIMEOUT_SECONDS));
    }

    private void closeSession(String sessionId, ConnectionVo connectionVo) {
        if (connectionVo == null) {
            return;
        }
        ACTIVE_SESSIONS.remove(sessionId, connectionVo);
        try {
            if (connectionVo.getConnection() != null) {
                connectionVo.getConnection().close();
            }
        } catch (SQLException e) {
            log.warn("关闭失败的 SQL 会话异常", e);
        }
    }

    private RunningStatement registerRunningStatement(String executionId, Statement statement) {
        if (StrUtil.isBlank(executionId)) {
            return null;
        }
        RunningStatement runningStatement = new RunningStatement(statement, Chat2DBContext.getConnectInfo());
        RunningStatement previous = RUNNING_STATEMENTS.putIfAbsent(executionId, runningStatement);
        if (previous != null) {
            throw new BusinessException("执行标识已在使用");
        }
        return runningStatement;
    }

    private void unregisterRunningStatement(String executionId, RunningStatement runningStatement) {
        if (StrUtil.isNotBlank(executionId) && runningStatement != null) {
            RUNNING_STATEMENTS.remove(executionId, runningStatement);
        }
    }

    @Override
    public boolean cancel(String executionId) {
        RunningStatement runningStatement = RUNNING_STATEMENTS.get(executionId);
        if (runningStatement == null) {
            return false;
        }
        if (!runningStatement.ownedBy(Chat2DBContext.getConnectInfo())) {
            throw new BusinessException("无权取消该 SQL 执行");
        }
        CANCEL_REQUESTS.add(executionId);
        try {
            runningStatement.statement.cancel();
            return true;
        } catch (SQLException e) {
            CANCEL_REQUESTS.remove(executionId);
            throw new BusinessException("取消 SQL 执行失败: " + e.getMessage());
        }
    }

    /**
     * 清除无效的connection
     */
    public void removeTimeoutConnection() {
        log.info("存在会话数==" + ACTIVE_SESSIONS.size());
        for (Map.Entry<String, ConnectionVo> entry : ACTIVE_SESSIONS.entrySet()) {
            ConnectionVo connectionVo = entry.getValue();
            if (connectionVo != null
                    && System.currentTimeMillis() - connectionVo.getLastAccessTime() > SESSION_TIMEOUT_MILLIS) {
                if (null != connectionVo && null != connectionVo.getConnection()) {
                    try {
                        connectionVo.getConnection().close();
                        ACTIVE_SESSIONS.remove(entry.getKey(), connectionVo);
                    } catch (SQLException e) {
                        log.error("关闭数据库连接会话失败:" + e.getMessage());
                    }
                }
            }
        }
    }

    private ConnectionVo getOwnedSession(String sessionId) {
        ConnectionVo connectionVo = ACTIVE_SESSIONS.get(sessionId);
        if (connectionVo == null) {
            throw new BusinessException("事务会话不存在或已过期");
        }
        if (!connectionVo.ownedBy(Chat2DBContext.getConnectInfo())) {
            throw new BusinessException("无权访问该事务会话");
        }
        connectionVo.touch();
        return connectionVo;
    }

    private void configureStatement(Statement statement) throws SQLException {
        configureStatement(statement, null);
    }

    private void configureStatement(Statement statement, Integer queryTimeoutSeconds) throws SQLException {
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
        if (connectInfo == null || !DataSourceTypeEnum.MONGODB.getCode().equals(connectInfo.getDbType())) {
            statement.setQueryTimeout(normalizeQueryTimeout(queryTimeoutSeconds));
        }
    }
}
