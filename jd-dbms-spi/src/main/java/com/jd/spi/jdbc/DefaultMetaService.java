package com.jd.spi.jdbc;

import com.google.common.collect.Lists;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.spi.CommandExecutor;
import com.jd.spi.MetaData;
import com.jd.spi.SqlBuilder;
import com.jd.spi.ValueHandler;
import com.jd.spi.model.*;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.sql.SQLExecutor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jipengfei
 * @version : DefaultMetaService.java
 */
public class DefaultMetaService implements MetaData {
    @Override
    public List<Database> databases(Connection connection) {
        return SQLExecutor.getInstance().databases(connection);
    }

    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        if (StringUtils.isNotBlank(databaseName) && CollectionUtils.isNotEmpty(schemas)) {
            for (Schema schema : schemas) {
                if (StringUtils.isBlank(schema.getDatabaseName())) {
                    schema.setDatabaseName(databaseName);
                }
            }
        }
        return schemas;
    }

    @Override
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        return null;
    }

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().tables(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, new String[]{"TABLE", "SYSTEM TABLE"});
    }

    @Override
    public List<TableIndex> getConstraintsData(Connection connection, String databaseName, String schemaName, String tableName) {
        return null;
    }

    @Override
    public PageResult<Table> tables(Connection connection, String databaseName, String schemaName, String tableNamePattern, int pageNo, int pageSize) {
        List<Table> tables = tables(connection, databaseName, schemaName, tableNamePattern);
        if (CollectionUtils.isEmpty(tables)) {
            return PageResult.of(tables, 0L, pageNo, pageSize);
        }
        List result = tables.stream().skip((pageNo - 1) * pageSize).limit(pageSize).collect(Collectors.toList());
        return PageResult.of(result, (long) tables.size(), pageNo, pageSize);
    }

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        return null;
    }

    @Override
    public List<Table> views(Connection connection, String databaseName, String schemaName) {
        return SQLExecutor.getInstance().tables(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, null, new String[]{"VIEW"});
    }

    @Override
    public List<Function> functions(Connection connection, String databaseName, String schemaName) {
        List<Function> functions = SQLExecutor.getInstance().functions(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName);
        if (CollectionUtils.isEmpty(functions)) {
            return functions;
        }
        return functions.stream().filter(function -> StringUtils.isNotBlank(function.getFunctionName())).collect(Collectors.toList());
    }

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName, String tableName) {
        return null;
    }

    @Override
    public List<Table> tableUsers(Connection connection, String databaseName) {
        return null;
    }


    @Override
    public Table tableUser(Connection connection, String userName, String databaseName) {
        return null;
    }

    @Override
    public List<Table> tableSpaces(Connection connection, String databaseName) {
        return null;
    }

    @Override
    public List<TableDetails> tableSpacesFile(Connection connection, Integer page, Integer size) {
        return null;
    }

    @Override
    public List<Procedure> procedures(Connection connection, String databaseName, String schemaName) {
        List<Procedure> procedures = SQLExecutor.getInstance().procedures(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName);

        if (CollectionUtils.isEmpty(procedures)) {
            return procedures;
        }
        return procedures.stream().filter(function -> StringUtils.isNotBlank(function.getProcedureName())).collect(Collectors.toList());
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().columns(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, null);
    }

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName,
                                     String columnName) {
        return SQLExecutor.getInstance().columns(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName, columnName);
    }

    @Override
    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
        return SQLExecutor.getInstance().indexes(connection, StringUtils.isEmpty(databaseName) ? null : databaseName, StringUtils.isEmpty(schemaName) ? null : schemaName, tableName);
    }

    @Override
    public List<ForeignData> getForeignKey(Connection connection, String schemaName, String tableName) {
        return null;
    }

    @Override
    public List<ForeignData> getUnForeignKey(Connection connection, String schemaName, String tableName) {
        return null;
    }

    private String VIEW_REFERENCED_SQL = "SELECT A.OWNER as OWNER,A.NAME as NAME,A.TYPE as TYPE,A.REFERENCED_NAME as REFERENCED_NAME,A.REFERENCED_TYPE as REFERENCED_TYPE,A.REFERENCED_OWNER as REFERENCED_OWNER, O.STATUS as STATUS FROM ALL_DEPENDENCIES A INNER JOIN SYS.ALL_OBJECTS O ON O.OBJECT_NAME = A.NAME  WHERE A.OWNER = '%s' AND A.NAME = '%s'";

    @Override
    public List<ViewReferencedData> getViewReferenced(Connection connection, String schemaName, String tableName) {
        List<ViewReferencedData> list = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(VIEW_REFERENCED_SQL, schemaName, tableName), resultSet -> {
            while (resultSet.next()) {
                ViewReferencedData viewReferencedData = new ViewReferencedData();
                viewReferencedData.setSchemaName(resultSet.getString("OWNER"));
                viewReferencedData.setViewName(resultSet.getString("NAME"));
                viewReferencedData.setViewType(resultSet.getString("TYPE"));
                viewReferencedData.setReferencedViewName(resultSet.getString("REFERENCED_NAME"));
                viewReferencedData.setReferencedViewType(resultSet.getString("REFERENCED_TYPE"));
                viewReferencedData.setReferencedSchemaName(resultSet.getString("REFERENCED_OWNER"));
                viewReferencedData.setStatus(resultSet.getString("STATUS"));
                list.add(viewReferencedData);
            }
            return list;
        });
    }

    private String VIEW_UPON_REFERENCED_SQL = "SELECT A.OWNER as OWNER,A.NAME as NAME,A.TYPE as TYPE,A.REFERENCED_NAME as REFERENCED_NAME,A.REFERENCED_TYPE as REFERENCED_TYPE,A.REFERENCED_OWNER as REFERENCED_OWNER, O.STATUS as STATUS FROM ALL_DEPENDENCIES A INNER JOIN SYS.ALL_OBJECTS O ON O.OBJECT_NAME = A.NAME  WHERE A.REFERENCED_OWNER = '%s' AND A.REFERENCED_NAME = '%s'";

    @Override
    public List<ViewReferencedData> getViewUponReferenced(Connection connection, String schemaName, String tableName) {
        List<ViewReferencedData> list = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(VIEW_UPON_REFERENCED_SQL, schemaName, tableName), resultSet -> {
            while (resultSet.next()) {
                ViewReferencedData viewReferencedData = new ViewReferencedData();
                viewReferencedData.setSchemaName(resultSet.getString("OWNER"));
                viewReferencedData.setViewName(resultSet.getString("NAME"));
                viewReferencedData.setViewType(resultSet.getString("TYPE"));
                viewReferencedData.setReferencedViewName(resultSet.getString("REFERENCED_NAME"));
                viewReferencedData.setReferencedViewType(resultSet.getString("REFERENCED_TYPE"));
                viewReferencedData.setReferencedSchemaName(resultSet.getString("REFERENCED_OWNER"));
                viewReferencedData.setStatus(resultSet.getString("STATUS"));
                list.add(viewReferencedData);
            }
            return list;
        });
    }

    private final String TEMP_TABLE_SPACE = " SELECT TABLESPACE_NAME, CONTENTS FROM DBA_TABLESPACES WHERE CONTENTS = '%s'";

    @Override
    public List<String> tempList(Connection connection, Integer type) {
        List<String> tableDetails = new ArrayList<>();
        String value = "";
        if (1 == type) {
            value = "TEMPORARY";
        } else {
            value = "PERMANENT";
        }
        return SQLExecutor.getInstance().execute(connection, String.format(TEMP_TABLE_SPACE, value), resultSet -> {
            while (resultSet.next()) {
                tableDetails.add(resultSet.getString("TABLESPACE_NAME"));
            }
            return tableDetails;
        });

    }

    private final String ALL_ROLES = "SELECT \"ROLE\" FROM DBA_ROLES";

    @Override
    public List<String> allRole(Connection connection) {
        List<String> tableDetails = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, ALL_ROLES, resultSet -> {
            while (resultSet.next()) {
                tableDetails.add(resultSet.getString("ROLE"));
            }
            return tableDetails;
        });
    }

    private final String FIND_GRANTEE_ROLES = "SELECT R.ROLE, P.ADMIN_OPTION ,P.DEFAULT_ROLE FROM DBA_ROLES R INNER JOIN DBA_ROLE_PRIVS P on R.ROLE = P.GRANTED_ROLE WHERE P.GRANTEE = '%s'";

    @Override
    public List<TableRoleData> findRole(Connection connection, String userName) {
        List<TableRoleData> tableDetails = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(FIND_GRANTEE_ROLES, userName), resultSet -> {
            while (resultSet.next()) {
                TableRoleData tableRoleData = new TableRoleData();
                tableRoleData.setIsAdmin(!"N".equals(resultSet.getString("ADMIN_OPTION")));
                tableRoleData.setRole(resultSet.getString("ROLE"));
                tableRoleData.setIsDefault(StringUtils.isNotBlank(resultSet.getString("DEFAULT_ROLE")));
                tableRoleData.setIsGranted(true);
                tableDetails.add(tableRoleData);
            }
            return tableDetails;
        });
    }

    @Override
    public List<TableDetails> rowCount(Connection connection, String databaseName, String schemaName) {
        return null;
    }


    @Override
    public String columnDefault(Connection connection, String name) {
        return null;
    }

    @Override
    public Set<TableIndex> getCheckSQl(Connection connection, String schemaName, String tableName) {
        return null;
    }

    @Override
    public List<TableObjectRoleData> allObjectRole(Connection connection, String userName, String schemaName, String tableName) {
        return null;
    }

    @Override
    public Function function(Connection connection, String databaseName, String schemaName, String functionName) {
        return null;
    }

    @Override
    public Trigger trigger(Connection connection, String databaseName, String schemaName, String triggerName) {
        return null;
    }

    @Override
    public Procedure procedure(Connection connection, String databaseName, String schemaName, String procedureName) {
        return null;
    }

    @Override
    public List<Type> types(Connection connection) {
        return SQLExecutor.getInstance().types(connection);
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new DefaultSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return null;
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).collect(Collectors.joining("."));
    }

    @Override
    public String getMetaDmpDataName(String... names) {
        return null;
    }

    @Override
    public List<ForeignData> beiForeGnKey(Connection connection, String schemaName, String tableName) {
        return null;
    }

    @Override
    public ValueHandler getValueHandler() {
        return new DefaultValueHandler();
    }

    @Override
    public CommandExecutor getCommandExecutor() {
        return SQLExecutor.getInstance();
    }

    @Override
    public List<String> getSystemDatabases() {
        return Lists.newArrayList();
    }

    @Override
    public List<String> getSystemSchemas() {
        return Lists.newArrayList();
    }

    @Override
    public String getConstraintName(Connection connection, String databaseName, String schemaName, String tableName) {
        return null;
    }

    @Override
    public String showViewSql(String databaseName, String schemaName, String viewName, String viewSql, List<TableColumn> columnList) {
        return "";
    }

    @Override
    public DmpCommand exportDmp(ConnectInfo connectInfo, List<String> tableName, String schemaName, File logFileName, File dmpFileName, String exportDmp,Boolean isWin, String dmpUser, String dmpPassword) {
        return null;
    }

    @Override
    public DmpCommand importDmp(ConnectInfo connectInfo, List<String> tableName, String fromName, String toName, String schemaName, File file, File logFileName, String importDmp, List<String> fromUserList, Boolean isWin, String dmpUser, String dmpPassword) {
        return null;
    }

    @Override
    public List<Table> tableDetails(Connection connection, String databaseName, String schemaName) {
        return null;
    }

    @Override
    public List<TableDetails> tableDetailsData(Connection connection, String databaseName, String schemaName) {
        return null;
    }


    @Override
    public List<Table> tableViews(Connection connection, String databaseName, String schemaName) {
        return null;
    }

    @Override
    public List<TableUserRole> getUserRule(Connection connection, String username) {
        return null;
    }

    @Override
    public List<TableIndex> notNullConstraints(Connection connection, String databaseName, String schemaName, String tableName) {
        return null;
    }

    @Override
    public Table tableSpace(Connection connection, String path) {
        return null;
    }

    @Override
    public List<TableRole> queryRoles(Connection connection, String schemaName, String tableName) {
        return null;
    }

    @Override
    public String getCount(Connection connection, String name, String schemaName) {
        return null;
    }

    @Override
    public Boolean executeSQL(Connection connection, String sql) {
        return null;
    }

    @Override
    public void dropTablespace(Connection connection, String spaceName) {
        return;
    }
}
