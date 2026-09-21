package com.jd.spi;

import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.*;
import com.jd.spi.sql.ConnectInfo;

import javax.validation.constraints.NotEmpty;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Get database metadata information.
 *
 * @author jipengfei
 * @version : MetaData.java
 */
public interface MetaData {
    /**
     * Query all databases.
     *
     * @param connection
     * @return
     */
    List<Database> databases(Connection connection);

    /**
     * Querying all schemas under a database
     *
     * @param connection
     * @param databaseName
     * @return
     */
    List<Schema> schemas(Connection connection, String databaseName);

    /**
     * Querying DDL information
     *
     * @param connection
     * @param databaseName
     * @param tableName
     * @return
     */
    String tableDDL(Connection connection, @NotEmpty String databaseName, @NotEmpty String schemaName,
                    @NotEmpty String tableName);

    /**
     * Querying all table under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @return
     */
    List<Table> tables(Connection connection, @NotEmpty String databaseName, String schemaName, String tableName);


    /**
     * oracle 查询无效约束信息
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @return List
     */
    List<TableIndex> getConstraintsData(Connection connection, String databaseName, String schemaName, String tableName);

    /**
     * Querying all table under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableNamePattern
     * @param pageNo
     * @param pageSize
     * @return
     */
    PageResult<Table> tables(Connection connection, String databaseName, String schemaName, String tableNamePattern, int pageNo, int pageSize);

    /**
     * Querying view information.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param viewName
     * @return
     */
    Table view(Connection connection, @NotEmpty String databaseName, String schemaName, String viewName);


    /**
     * Querying all views under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @return
     */
    List<Table> views(Connection connection, @NotEmpty String databaseName, String schemaName);


    /**
     * Querying all tableUsers under a schema.
     *
     * @param connection
     * @param databaseName
     * @return
     */
    List<Table> tableUsers(Connection connection, @NotEmpty String databaseName);

    /**
     * Querying tableUser under a schema.
     *
     * @param connection
     * @param databaseName
     * @return
     */
    Table tableUser(Connection connection, String databaseName, String username);

    /**
     * Querying all tableSpaces under a schema.
     *
     * @param connection
     * @param databaseName
     * @return
     */
    List<Table> tableSpaces(Connection connection, String databaseName);



    List<TableDetails> tableSpacesFile(Connection connection, Integer page, Integer size);

    /**
     * Querying all functions under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @return
     */
    List<Function> functions(Connection connection, @NotEmpty String databaseName, String schemaName);

    /**
     * Querying all triggers under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @return
     */
    List<Trigger> triggers(Connection connection, @NotEmpty String databaseName, String schemaName, String tableName);

    /**
     * Querying all procedures under a schema.
     *
     * @param connection
     * @param schemaName
     * @param databaseName
     * @return
     */
    List<Procedure> procedures(Connection connection, @NotEmpty String databaseName, String schemaName);

    /**
     * Querying all columns under a table.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @return
     */
    List<TableColumn> columns(Connection connection, @NotEmpty String databaseName, String schemaName,
                              @NotEmpty String tableName);

    /**
     * Querying all columns under a table.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @param columnName
     * @return
     */
    List<TableColumn> columns(Connection connection, @NotEmpty String databaseName, String schemaName, String tableName,
                              String columnName);

    /**
     * Querying all indexes under a table.
     *
     * @param connection
     * @param databaseName
     * @param databaseName
     * @return
     */
    List<TableIndex> indexes(Connection connection, @NotEmpty String databaseName, String schemaName,
                             @NotEmpty String tableName);

    /**
     * 获取外键信息
     *
     * @param connection
     * @param schemaName
     * @param tableName
     * @return
     */
    List<ForeignData> getForeignKey(Connection connection, String tableName, String schemaName);


    /**
     * 被引用外键信息
     * @param connection
     * @param schemaName
     * @param tableName
     * @return
     */
    List<ForeignData> getUnForeignKey(Connection connection, String schemaName, String tableName);


    /**
     * 获取视图股关联信息
     * @param connection
     * @param schemaName
     * @param tableName
     * @return
     */
    List<ViewReferencedData> getViewReferenced(Connection connection, String schemaName, String tableName);

    /**
     * Querying function detail under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param functionName
     * @return
     */
    Function function(Connection connection, @NotEmpty String databaseName, String schemaName, String functionName);

    /**
     * Querying  trigger  under a schema.
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param triggerName
     * @return
     */
    Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName, String triggerName);

    /**
     * Querying all procedures under a schema.
     *
     * @param connection
     * @param schemaName
     * @param databaseName
     * @param procedureName
     * @return
     */
    Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName, String procedureName);


    /**
     * @param connection
     * @return
     */
    List<Type> types(Connection connection);


    /**
     * Get sql builder.
     *
     * @return
     */
    SqlBuilder getSqlBuilder();


    /**
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @return
     */
    TableMeta getTableMeta(String databaseName, String schemaName, String tableName);


    /**
     * Get meta data name.
     */
    String getMetaDataName(String... names);

    String getMetaDmpDataName(String... names);


    List<ForeignData> beiForeGnKey(Connection connection, String schemaName, String tableName);


    /**
     * Get column builder.
     */
    ValueHandler getValueHandler();


    /**
     * Get command executor.
     */
    CommandExecutor getCommandExecutor();

    /**
     * Get system databases.
     *
     * @return
     */
    List<String> getSystemDatabases();

    /**
     * Get system schemas.
     *
     * @return
     */
    List<String> getSystemSchemas();

    /**
     * Get table key.
     *
     * @return
     */
    String getConstraintName(Connection connection, String databaseName, String schemaName, String tableName);

    String showViewSql(String databaseName, String schemaName, String viewName, String viewSql, List<TableColumn> columnList);


    /**
     * 导出dmp文件
     *
     * @param tableName
     * @param schemaName
     * @param logFileName
     * @param dmpFileName
     * @return
     */
    DmpCommand exportDmp(ConnectInfo connectInfo, List<String> tableName, String schemaName, File logFileName, File dmpFileName, String exportDmp,Boolean isWin, String dmpUser, String dmpPassword);


    /**
     * 导入dmp文件
     *
     * @param tableName
     * @param schemaName
     * @param file
     * @param logFileName
     * @return
     */
    DmpCommand importDmp(ConnectInfo connectInfo, List<String> tableName, String fromName, String toName, String schemaName, File file, File logFileName, String importDmp, List<String> FromUsers, Boolean isWin, String dmpUser, String dmpPassword);

    /**
     * 表详情信息
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @return
     */
    List<Table> tableDetails(Connection connection, String databaseName, String schemaName);

    /**
     * 表详情信息
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @return
     */
    List<TableDetails> tableDetailsData(Connection connection, String databaseName, String schemaName);

    /**
     * 视图列表详细信息
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @return
     */
    List<Table> tableViews(Connection connection, String databaseName, String schemaName);

    /**
     * 获取用户数据库权限
     *
     * @param connection
     * @return
     */
    List<TableUserRole> getUserRule(Connection connection, String username);

    /**
     * 获取非空约束
     *
     * @param connection
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @return
     */

    List<TableIndex> notNullConstraints(Connection connection, String databaseName, String schemaName, String tableName);

    /**
     * 表空间查询
     *
     * @param connection
     * @param path
     * @return
     */
    Table tableSpace(Connection connection, String path);

    /**
     * 表权限
     *
     * @param connection
     * @param schemaName
     * @param tableName
     * @return
     */
    List<TableRole> queryRoles(Connection connection, String schemaName, String tableName);

    /**
     * 表行数
     *
     * @param connection
     * @param name
     * @param schemaName
     * @return
     */
    String getCount(Connection connection, String name, String schemaName);

    public Boolean executeSQL(Connection connection, String sql);

    void dropTablespace(Connection connection, String spaceName);

    List<ViewReferencedData> getViewUponReferenced(Connection connection, String schemaName, String tableName);

    List<String> tempList(Connection connection, Integer type);

    List<String> allRole(Connection connection);

    List<TableRoleData> findRole(Connection connection, String userName);

    List<TableDetails> rowCount(Connection connection, String databaseName, String schemaName);

    String columnDefault(Connection connection, String name);

    Set<TableIndex> getCheckSQl(Connection connection, String schemaName, String tableName);

    List<TableObjectRoleData> allObjectRole(Connection connection, String userName, String schemaName, String tableName);
}
