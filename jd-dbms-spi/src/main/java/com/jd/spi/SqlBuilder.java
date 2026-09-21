package com.jd.spi;

import com.jd.spi.model.BlobSqlResult;
import com.jd.spi.model.Database;
import com.jd.spi.model.OrderBy;
import com.jd.spi.model.QueryResult;
import com.jd.spi.model.Schema;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableObjectRoleData;

import javax.validation.constraints.NotEmpty;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

public interface SqlBuilder<T> {

    /**
     * Generate create table sql
     *
     * @param table
     * @return
     */
    String buildCreateTableSql(T table);


    /**
     * Generate modify table sql
     *
     * @param newTable
     * @param oldTable
     * @return
     */
    String buildModifyTaleSql(T oldTable, T newTable);


    /**
     * Generate page limit sql
     *
     * @param sql
     * @param offset
     * @param pageNo
     * @param pageSize
     * @return
     */
    String pageLimit(String sql, int offset, int pageNo, int pageSize);


    /**
     * Generate create database sql
     *
     * @param database
     * @return
     */
    String buildCreateDatabaseSql(Database database);


    /**
     * @param oldDatabase
     * @param newDatabase
     * @return
     */
    String buildModifyDatabaseSql(Database oldDatabase, Database newDatabase);


    /**
     * @param schemaName
     * @return
     */
    String buildCreateSchemaSql(Schema schemaName);


    /**
     * @param oldSchemaName
     * @param newSchemaName
     * @return
     */
    String buildModifySchemaSql(String oldSchemaName, String newSchemaName);

    /**
     * @param originSql
     * @param orderByList
     * @return
     */
    String buildOrderBySql(String originSql, List<OrderBy> orderByList);


    /**
     * generate sql based on results
     */
    String buildSqlByQuery(QueryResult queryResult);


    BlobSqlResult buildBlobSql(QueryResult queryResult);

    /**
     * DML SQL
     *
     * @param table
     * @param type
     * @return
     */
    String getTableDmlSql(T table, String type);

    /**
     * 新建存储过程模板
     *
     * @param databaseName
     * @param schemaName
     * @param procedureName 存储过程名称
     * @return
     */
    String createProcedureTemplate(@NotEmpty String databaseName, String schemaName, String procedureName);

    /**
     * 删除存储过程
     *
     * @param databaseName
     * @param schemaName
     * @param procedureName
     * @return
     */
    String deleteProcedure(@NotEmpty String databaseName, String schemaName, String procedureName);


    /**
     * 创建函数模板
     *
     * @param databaseName
     * @param schemaName
     * @param functionName
     * @return
     */
    String createFunctionTemplate(@NotEmpty String databaseName, String schemaName, String functionName);


    /**
     * 删除存储过程
     *
     * @param databaseName
     * @param schemaName
     * @param functionName
     * @return
     */
    String deleteFuction(@NotEmpty String databaseName, String schemaName, String functionName);


    /**
     * 创建触发器模板
     *
     * @param databaseName
     * @param schemaName
     * @param triggerName
     * @return
     */
    String createTriggersTemplate(String databaseName, String schemaName, String triggerName, String tableName,
                                  boolean before,boolean after);

    /**
     * 删除触发器
     *
     * @param databaseName
     * @param schemaName
     * @param triggerName
     * @return
     */
    String deleteTriggers(String databaseName, String schemaName, String triggerName);

    /**
     * 创建授权Sql模板
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @param toGrantUser 被授权用户
     * @return
     */
    String createGrantTemplate(String databaseName, String schemaName, String tableName, String toGrantUser,
    Boolean insert, Boolean update, Boolean delete);

    /**
     * 删除授权Sql模板
     * @param databaseName
     * @param schemaName
     * @param tableName
     * @param toGrantUser
     * @param insert
     * @param update
     * @param delete
     * @return
     */
    String deleteGrantTemplate(String databaseName, String schemaName, String tableName, String toGrantUser, Boolean insert, Boolean update, Boolean delete);

    /**
     * 创建表空间
     * @param fileName
     * @param spaceName
     * @param size
     * @param maxSize
     * @param autoSize
     * @return
     */
    String createSpace(String fileName, String spaceName, String size, String maxSize, String autoSize);

    /**
     * 修改数据库用户密码
     * @param userName
     * @param newPassword
     * @return
     */
    String manageUserPassword(String userName, String newPassword);

    /**
     * 加解锁用户
     * @param userName
     * @param isLock
     * @return
     */
    String lockOrUnlock(String userName, Boolean isLock);

    String createUser(String name);

    String dropUser(String username);

    String dropTableConstraint(String schema, String tableName, String keyName, String name);

    String createTableConstraint(String schemaName, String tableName, String columnName, String constraintName, String constraintType, String check, String forkSchema, String forkTableName, String forkColumn);

    String dropTableSql(String schemaName, String tableName);

    String deleteTable(T table);

    String selectTable(T table, List<List<Object>> dataAllList, List<String> fromColumnList, List<String> toColumnList);

    String addUserRole(String userName, String role, String adminRole);

    String delUserRole(String userName, String role);

    String createUser(String userName, String newPassWord, String defaultTableSpace, String temptableSpace);

    String updateOFFAutoSpace(Connection connection, String path,String tableSpace);

    String updateAutoSpace(Connection connection, String path, String autoSize, String maxSize,String tableSpace);

    String updateDefaultSpace(Connection connection, String tableSpace,  String path, String totalSize);

    String unEnabledConstraint(String schemaName, String tableName, String constraintName);

    List<String> excelDataIns(Table table, List<Map<String, Object>> insertDataList, List<String> columns);

    List<String> dropOrAddIdentityInsert(String schemaName, String table, List<String> columns, Boolean open);

    List<String> excelDataForUp(Table table, List<Map<String, Object>> updateDataList, List<String> columns);

    String getIndexWhere(String tableName, String schemaName, List<String> tableColumnList, List<String> primaryList, Map<String, Object> map);

    List<String> addObjectRole(String schemaName, String userName, String tableName, List<TableObjectRoleData> newObjectRoleData);

    List<String> delObjectRole(String schemaName, String userName, String tableName, List<TableObjectRoleData> newObjectRoleData);

    String copyTable(String copySchemaName, String tableName, String schemaName, Boolean isData);
}
