package com.jd.spi.jdbc;

import cn.hutool.core.util.StrUtil;
import com.jd.common.constant.Constants;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.spi.MetaData;
import com.jd.spi.SqlBuilder;
import com.jd.spi.enums.ConstraintTypeEnum;
import com.jd.spi.enums.DmlType;
import com.jd.spi.model.*;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.util.SqlUtils;
import com.google.common.collect.Lists;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultSqlBuilder implements SqlBuilder<Table> {


    @Override
    public String buildCreateTableSql(Table table) {
        return null;
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        return null;
    }

    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        return null;
    }

    public static String CREATE_DATABASE_SQL = "CREATE DATABASE IF NOT EXISTS `%s` DEFAULT CHARACTER SET %s COLLATE %s";

    @Override
    public String buildCreateDatabaseSql(Database database) {
        return null;
    }

    @Override
    public String buildModifyDatabaseSql(Database oldDatabase, Database newDatabase) {
        return null;
    }

    @Override
    public String buildCreateSchemaSql(Schema schema) {
        return null;
    }

    @Override
    public String buildModifySchemaSql(String oldSchemaName, String newSchemaName) {
        return null;
    }

    @Override
    public String buildOrderBySql(String originSql, List<OrderBy> orderByList) {
        if (CollectionUtils.isEmpty(orderByList)) {
            return originSql;
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(originSql);
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                PlainSelect plainSelect = (PlainSelect) selectStatement.getSelectBody();

                // Create a new ORDER BY clause
                List<OrderByElement> orderByElements = new ArrayList<>();

                for (OrderBy orderBy : orderByList) {
                    OrderByElement orderByElement = new OrderByElement();
                    orderByElement.setExpression(CCJSqlParserUtil.parseExpression(orderBy.getColumnName()));
                    orderByElement.setAsc(orderBy.isAsc()); // Set to ascending order, use setAsc(false) to set to descending order
                    orderByElements.add(orderByElement);
                }
                // Replace the original ORDER BY clause
                plainSelect.setOrderByElements(orderByElements);
                // Output the modified SQL
                return plainSelect.toString();
            }
        } catch (Exception e) {
        }
        return originSql;
    }

    @Override
    public String buildSqlByQuery(QueryResult queryResult) {
        List<Header> headerList = queryResult.getHeaderList();
        List<ResultOperation> operations = queryResult.getOperations();
        String tableName = queryResult.getTableName();
        StringBuilder stringBuilder = new StringBuilder();
        MetaData metaSchema = Chat2DBContext.getMetaData();
        List<String> keyColumns = getPrimaryColumns(headerList);
        for (int i = 0; i < operations.size(); i++) {
            ResultOperation operation = operations.get(i);
            List<String> row = operation.getDataList();
            List<String> odlRow = operation.getOldDataList();
            String sql = "";
            if ("UPDATE".equalsIgnoreCase(operation.getType())) {
                sql = getUpdateSql(tableName, headerList, row, odlRow, metaSchema, keyColumns, false);
            } else if ("CREATE".equalsIgnoreCase(operation.getType())) {
                sql = getInsertSql(tableName, headerList, row, metaSchema, queryResult.getIsView());
            } else if ("DELETE".equalsIgnoreCase(operation.getType())) {
                sql = getDeleteSql(tableName, headerList, odlRow, metaSchema, keyColumns);
            } else if ("UPDATE_COPY".equalsIgnoreCase(operation.getType())) {
                sql = getUpdateSql(tableName, headerList, row, row, metaSchema, keyColumns, true);
            }

            stringBuilder.append(sql + ";\n");
        }
        return stringBuilder.toString();
    }


    @Override
    public BlobSqlResult buildBlobSql(QueryResult queryResult) {
        List<Header> headerList = queryResult.getHeaderList();
        List<ResultOperation> operations = queryResult.getOperations();
        String tableName = queryResult.getTableName();
        StringBuilder stringBuilder = new StringBuilder();
        MetaData metaSchema = Chat2DBContext.getMetaData();
        List<String> keyColumns = getPrimaryColumns(headerList);
        BlobSqlResult blobSqlResult1 = new BlobSqlResult();
        List<String> blobValues = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            ResultOperation operation = operations.get(i);
            List<String> row = operation.getDataList();
            List<String> odlRow = operation.getOldDataList();
            if ("UPDATE".equalsIgnoreCase(operation.getType())) {
                BlobSqlResult blobSqlResult = getUpdateBlobSql(tableName, headerList, row, odlRow, metaSchema, keyColumns, false);
                if (null != blobSqlResult && StrUtil.isNotBlank(blobSqlResult.getDataSql())) {
                    stringBuilder.append(blobSqlResult.getDataSql()).append(";\n");
                    blobValues.addAll(blobSqlResult.getBlobValues());
                }
            } else if ("CREATE".equalsIgnoreCase(operation.getType())) {
                BlobSqlResult blobSqlResult = getInsertBlobSql(tableName, headerList, row, metaSchema, queryResult.getIsView());
                if (null != blobSqlResult && StrUtil.isNotBlank(blobSqlResult.getDataSql())) {
                    stringBuilder.append(blobSqlResult.getDataSql()).append(";\n");
                    blobValues.addAll(blobSqlResult.getBlobValues());
                }
            }
        }
        blobSqlResult1.setDataSql(stringBuilder.toString());
        blobSqlResult1.setBlobValues(blobValues);
        return blobSqlResult1;
    }

    @Override
    public String getTableDmlSql(Table table, String type) {
        if (table == null || CollectionUtils.isEmpty(table.getColumnList()) || StringUtils.isBlank(type)) {
            return "";
        }
        if(DmlType.INSERT.name().equalsIgnoreCase(type)) {
            return getInsertSql(table.getName(), table.getColumnList());
        } else if(DmlType.UPDATE.name().equalsIgnoreCase(type)) {
            return getUpdateSql(table.getName(), table.getColumnList());
        } else if(DmlType.DELETE.name().equalsIgnoreCase(type)) {
            return getDeleteSql(table.getName(), table.getColumnList());
        }else if(DmlType.SELECT.name().equalsIgnoreCase(type)) {
            return getSelectSql(table.getName(), table.getColumnList());
        }
        return "";
    }

    @Override
    public String createProcedureTemplate(String databaseName, String schemaName, String procedureName) {
        return null;
    }

    @Override
    public String deleteProcedure(String databaseName, String schemaName, String procedureName) {
        return null;
    }

    @Override
    public String createFunctionTemplate(String databaseName, String schemaName, String functionName) {
        return null;
    }

    @Override
    public String deleteFuction(String databaseName, String schemaName, String functionName) {
        return null;
    }

    @Override
    public String createTriggersTemplate(String databaseName, String schemaName, String triggerName,String tableName,
        boolean before,boolean after) {
        return null;
    }

    @Override
    public String deleteTriggers(String databaseName, String schemaName, String triggerName) {
        return null;
    }

    @Override
    public String dropTableSql(String schemaName, String tableName) {
        return null;
    }

    @Override
    public String createGrantTemplate(String databaseName, String schemaName, String tableName, String toGrantUser,
                                      Boolean insert, Boolean update, Boolean delete) {
        return null;
    }

    @Override
    public String deleteGrantTemplate(String databaseName, String schemaName, String tableName, String toGrantUser, Boolean insert, Boolean update, Boolean delete) {
        return null;
    }

    /**
     * 构建 Oracle 兼容的表级授权/撤权语句。
     */
    protected String buildTablePrivilegeSql(boolean grant, String databaseName, String schemaName,
                                            String tableName, String toGrantUser, Boolean insert,
                                            Boolean update, Boolean delete) {
        String ownerName = StrUtil.isNotBlank(schemaName) ? schemaName : databaseName;
        if (StrUtil.isBlank(ownerName) || StrUtil.isBlank(tableName) || StrUtil.isBlank(toGrantUser)) {
            throw new BusinessException("授权对象参数缺失");
        }

        List<String> privileges = new ArrayList<>();
        privileges.add("SELECT");
        if (Boolean.TRUE.equals(insert)) {
            privileges.add("INSERT");
        }
        if (Boolean.TRUE.equals(update)) {
            privileges.add("UPDATE");
        }
        if (Boolean.TRUE.equals(delete)) {
            privileges.add("DELETE");
        }

        return (grant ? "GRANT " : "REVOKE ")
                + String.join(", ", privileges)
                + " ON " + quoteIdentifier(ownerName) + "." + quoteIdentifier(tableName)
                + (grant ? " TO " : " FROM ") + quoteIdentifier(toGrantUser);
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String getSelectSql(String name, List<TableColumn> columnList) {
        StringBuilder script = new StringBuilder();
        script.append("SELECT ");
        for (TableColumn column : columnList) {
            script.append(column.getName())
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(" FROM where").append(name);
        return script.toString();
    }

    private String getDeleteSql(String name, List<TableColumn> columnList) {
        StringBuilder script = new StringBuilder();
        script.append("DELETE FROM ").append(name)
                .append(" where ");
        return script.toString();
    }

    private String getUpdateSql(String name, List<TableColumn> columnList) {
        StringBuilder script = new StringBuilder();
        script.append("UPDATE ").append(name)
                .append(" set ");
        for (TableColumn column : columnList) {
            script.append(column.getName())
                    .append(" = ")
                    .append(" ")
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(" where ");
        return script.toString();
    }

    private String getInsertSql(String name, List<TableColumn> columnList) {
        StringBuilder script = new StringBuilder();
        script.append("INSERT INTO ").append(name)
                .append(" (");
        for (TableColumn column : columnList) {
            script.append(column.getName())
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(") VALUES (");
        for (TableColumn column : columnList) {
            script.append(" ")
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(")");
        return script.toString();
    }

    private List<String> getPrimaryColumns(List<Header> headerList) {
        if (CollectionUtils.isEmpty(headerList)) {
            return Lists.newArrayList();
        }
        List<String> keyColumns = Lists.newArrayList();
        for (Header header : headerList) {
            if (header.getPrimaryKey() != null && header.getPrimaryKey()) {
                keyColumns.add(header.getName());
            }
        }
        return keyColumns;
    }

    private String getDeleteSql(String tableName, List<Header> headerList, List<String> row, MetaData metaSchema,
                                List<String> keyColumns) {
        StringBuilder script = new StringBuilder();
        script.append("DELETE FROM ").append(tableName).append("");
        script.append(buildWhere(headerList, row, metaSchema, keyColumns));
        return script.toString();
    }

    private String buildBlobWhere(List<String> blobValues, List<Header> headerList, List<String> row, MetaData metaSchema, List<String> keyColumns) {
        // todo 物理数据行ID
        int index = 0;
        for (int i = 0; i < headerList.size(); i++) {
            Header header = headerList.get(i);
            if (Constants.ROW_ID.equals(header.getName())) {
                index = i;
            }
        }
        if (0 == index) {
            throw new BusinessException("数据解析失败");
        }
        String value = null;
        try {
            value = row.get(index);
        } catch (Exception e) {
            throw new BusinessException("数据解析失败");
        }
        boolean ref = Boolean.FALSE;
        try {
            Integer.parseInt(value);
            ref = Boolean.TRUE;
        } catch (Exception ignored) {}
        if (ref) {
            return String.format(" where " + Constants.ROW_ID + " = %s", value);
        } else {
            return String.format(" where " + Constants.ROW_ID + " = '%s'", value);
        }
//        if (CollectionUtils.isEmpty(keyColumns)) {
//            for (int i = 1; i < row.size(); i++) {
//                String oldValue = row.get(i);
//                Header header = headerList.get(i);
//                String value = SqlUtils.getSqlValue(oldValue, header.getDataType());
//                if (value == null) {
//                    script.append(metaSchema.getMetaDataName(header.getName()))
//                            .append(" is null and ");
//                } else if ("?".equals(value)) {
//                    continue;
////                    blobValues.add(oldValue);
////                    script.append(metaSchema.getMetaDataName(header.getName())).append(" = ").append("? and ");
//                } else {
//                    script.append(metaSchema.getMetaDataName(header.getName()))
//                            .append(" = ")
//                            .append(value)
//                            .append(" and ");
//                }
//            }
//        } else {
//            for (int i = 1; i < row.size(); i++) {
//                String oldValue = row.get(i);
//                Header header = headerList.get(i);
//                String columnName = header.getName();
//                if (keyColumns.contains(columnName)) {
//                    String value = SqlUtils.getSqlValue(oldValue, header.getDataType());
//                    if (value == null) {
//                        script.append(metaSchema.getMetaDataName(columnName))
//                                .append(" is null and ");
//                    } else if ("?".equals(value)) {
//                        blobValues.add(oldValue);
//                        script.append(metaSchema.getMetaDataName(columnName)).append(" = ").append("? and");
//                    } else {
//                        script.append(metaSchema.getMetaDataName(columnName))
//                                .append(" = ")
//                                .append(value)
//                                .append(" and ");
//                    }
//                }
//            }
//        }
//        script.delete(script.length() - 4, script.length());
//        return script.toString();
    }

    private String buildWhere(List<Header> headerList, List<String> row, MetaData metaSchema, List<String> keyColumns) {
        // todo!!!!!! 物理数据行ID
        int index = 0;
        for (int i = 0; i < headerList.size(); i++) {
            Header header = headerList.get(i);
            if (Constants.ROW_ID.equals(header.getName())) {
                index = i;
            }
        }
        if (0 == index) {
            throw new BusinessException("数据解析失败");
        }
        String value = null;
        try {
            value = row.get(index);
        } catch (Exception e) {
            throw new BusinessException("数据解析失败");
        }
        boolean ref = Boolean.FALSE;
        try {
            Integer.parseInt(value);
            ref = Boolean.TRUE;
        } catch (Exception ignored) {}
        if (ref) {
            return String.format(" where " + Constants.ROW_ID + " = %s", value);
        } else {
            return String.format(" where " + Constants.ROW_ID + " = '%s'", value);
        }

//        if (CollectionUtils.isEmpty(keyColumns)) {
//            for (int i = 1; i < row.size(); i++) {
//                String oldValue = row.get(i);
//                Header header = headerList.get(i);
//                String value = SqlUtils.getSqlValue(oldValue, header.getDataType());
//                if (value == null) {
//                    script.append(metaSchema.getMetaDataName(header.getName()))
//                            .append(" is null and ");
//                } else {
//                    script.append(metaSchema.getMetaDataName(header.getName()))
//                            .append(" = ")
//                            .append(value)
//                            .append(" and ");
//                }
//            }
//        } else {
//            for (int i = 1; i < row.size(); i++) {
//                String oldValue = row.get(i);
//                Header header = headerList.get(i);
//                String columnName = header.getName();
//                if (keyColumns.contains(columnName)) {
//                    String value = SqlUtils.getSqlValue(oldValue, header.getDataType());
//                    if (value == null) {
//                        script.append(metaSchema.getMetaDataName(columnName))
//                                .append(" is null and ");
//                    } else {
//                        script.append(metaSchema.getMetaDataName(columnName))
//                                .append(" = ")
//                                .append(value)
//                                .append(" and ");
//                    }
//                }
//            }
//        }
//        script.delete(script.length() - 4, script.length());
//        return script.toString();
    }

    private String getInsertSql(String tableName, List<Header> headerList, List<String> row, MetaData metaSchema, Boolean isView) {
        if (CollectionUtils.isEmpty(row) || ObjectUtils.allNull(row.toArray())) {
            return "";
        }
        StringBuilder script = new StringBuilder();
        script.append("INSERT INTO ").append(tableName)
                .append(" (");
        for (int i = 1; i < row.size(); i++) {
            Header header = headerList.get(i);
            //String newValue = row.get(i);
            //if (newValue != null) {
            if (Constants.ROW_ID.equals(header.getName())) {
                continue;
            }
            //解决达梦自增序列问题
            if(Objects.nonNull(header.getAutoIncrement()) && header.getAutoIncrement().equals(1)){
                continue;
            }
            if (isView && i == 1) {
                continue;
            }
            script.append(metaSchema.getMetaDataName(header.getName()))
                    .append(",");
            // }
        }
        script.deleteCharAt(script.length() - 1);
        script.append(") VALUES (");
        for (int i = 1; i < row.size(); i++) {
            String newValue = row.get(i);
            //if (newValue != null) {
            Header header = headerList.get(i);
            //解决达梦自增序列问题
            if(Objects.nonNull(header.getAutoIncrement()) && header.getAutoIncrement().equals(1)){
                continue;
            }
            if (Constants.ROW_ID.equals(header.getName())) {
                continue;
            }
            if (isView && i == 1) {
                continue;
            }
            script.append(SqlUtils.getSqlValue(newValue, header.getDataType()))
                    .append(",");
            //}
        }
        script.deleteCharAt(script.length() - 1);
        script.append(")");
        return script.toString();

    }

    private String getUpdateSql(String tableName, List<Header> headerList, List<String> row, List<String> odlRow,
                                MetaData metaSchema,
                                List<String> keyColumns, boolean copy) {
        StringBuilder script = new StringBuilder();
        if (CollectionUtils.isEmpty(row) || CollectionUtils.isEmpty(odlRow)) {
            return "";
        }
        script.append("UPDATE ").append(tableName).append(" set ");
        for (int i = 1; i < row.size(); i++) {
            String newValue = row.get(i);
            String oldValue = odlRow.get(i);
            if (StringUtils.equals(newValue, oldValue) && !copy) {
                continue;
            }
            Header header = headerList.get(i);
            if (Constants.ROW_ID.equals(header.getName())) {
                continue;
            }
            String newSqlValue = SqlUtils.getSqlValue(newValue, header.getDataType());
            script.append(metaSchema.getMetaDataName(header.getName()))
                    .append(" = ")
                    .append(newSqlValue)
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(buildWhere(headerList, odlRow, metaSchema, keyColumns));
        return script.toString();
    }


    private BlobSqlResult getUpdateBlobSql(String tableName, List<Header> headerList, List<String> row, List<String> odlRow,
                                MetaData metaSchema,
                                List<String> keyColumns, boolean copy) {

        StringBuilder script = new StringBuilder();
        if (CollectionUtils.isEmpty(row) || CollectionUtils.isEmpty(odlRow)) {
            return null;
        }
        BlobSqlResult blobSqlResult = new BlobSqlResult();
        List<String> blobValues = new ArrayList<>();
        script.append("UPDATE ").append(tableName).append(" set ");
        for (int i = 1; i < row.size(); i++) {
            String newValue = row.get(i);
            String oldValue = odlRow.get(i);
            if (StringUtils.equals(newValue, oldValue) && !copy) {
                continue;
            }
            Header header = headerList.get(i);
            String newSqlValue = SqlUtils.getSqlValue(newValue, header.getDataType());
            if ("?".equals(newSqlValue)) {
                blobValues.add(newValue);
                script.append(metaSchema.getMetaDataName(header.getName())).append(" = ").append(" ? ,");
            } else {
                script.append(metaSchema.getMetaDataName(header.getName()))
                        .append(" = ")
                        .append(newSqlValue)
                        .append(",");
            }
        }
        script.deleteCharAt(script.length() - 1);
        script.append(buildBlobWhere(blobValues, headerList, odlRow, metaSchema, keyColumns));
        String sql = script.toString();
        blobSqlResult.setDataSql(sql);
        blobSqlResult.setBlobValues(blobValues);
        return blobSqlResult;
    }



    private BlobSqlResult getInsertBlobSql(String tableName, List<Header> headerList, List<String> row, MetaData metaSchema, Boolean isView) {
        if (CollectionUtils.isEmpty(row) || ObjectUtils.allNull(row.toArray())) {
            return null;
        }
        BlobSqlResult blobSqlResult = new BlobSqlResult();
        List<String> blobValues = new ArrayList<>();
        StringBuilder script = new StringBuilder();
        script.append("INSERT INTO ").append(tableName)
                .append(" (");
        for (int i = 1; i < row.size(); i++) {
            Header header = headerList.get(i);
            //String newValue = row.get(i);
            //if (newValue != null) {
            //解决达梦自增序列问题
            if(header.getAutoIncrement().equals(1)){
                continue;
            }
            if (isView && i == 1) {
                continue;
            }
            script.append(metaSchema.getMetaDataName(header.getName()))
                    .append(",");
            // }
        }
        script.deleteCharAt(script.length() - 1);
        script.append(") VALUES (");
        for (int i = 1; i < row.size(); i++) {
            String newValue = row.get(i);
            Header header = headerList.get(i);
            //解决达梦自增序列问题
            if(header.getAutoIncrement().equals(1)){
                continue;
            }
            if (isView && i == 1) {
                continue;
            }
            String value = SqlUtils.getSqlValue(newValue, header.getDataType());
            if ("?".equals(value)) {
                blobValues.add(newValue);
                script.append(" ? ,");
            } else {
                script.append(value)
                        .append(",");
            }
        }
        script.deleteCharAt(script.length() - 1);
        script.append(")");
        String sql = script.toString();
        blobSqlResult.setDataSql(sql);
        blobSqlResult.setBlobValues(blobValues);
        return blobSqlResult;

    }

    @Override
    public String createSpace(String fileName, String spaceName, String size, String maxSize, String autoSize) {
        return null;
    }

    @Override
    public String updateOFFAutoSpace(Connection connection, String path,String tableSpace) {
        StringBuilder stringBuilder = new StringBuilder("ALTER TABLESPACE \"").append(tableSpace).append("\" DATAFILE '%s' AUTOEXTEND OFF;");
        return String.format(stringBuilder.toString(), path);
    }

    @Override
    public String updateAutoSpace(Connection connection, String path, String autoSize, String maxSize,String tableSpace) {
        StringBuilder stringBuilder = new StringBuilder("ALTER TABLESPACE \"").append(tableSpace).append("\" DATAFILE '%s' AUTOEXTEND ON");
        if(!StringUtils.equals("0",autoSize)){
            stringBuilder.append(" NEXT ").append(autoSize.replace(".00", ""));
        }
        if (StringUtils.isNotBlank(maxSize)&&!StringUtils.equals("0",maxSize)) {
            stringBuilder.append(" MAXSIZE ").append(maxSize.replace(".00", ""));
        }
        stringBuilder.append(";");
        return String.format(stringBuilder.toString(), path);
    }

    @Override
    public String updateDefaultSpace(Connection connection, String tableSpace, String path, String totalSize) {
        StringBuilder stringBuilder = new StringBuilder("ALTER TABLESPACE \"").append(tableSpace).append("\" resize DATAFILE '%s' to ").append(totalSize.replace(".00", "")).append(";");
        return String.format(stringBuilder.toString(), path);
    }

    @Override
    public String unEnabledConstraint(String schemaName, String tableName, String constraintName) {
        StringBuilder stringBuilder = new StringBuilder("ALTER TABLE \"").append(schemaName).append("\".\"").append(tableName).append("\" DISABLE CONSTRAINT \"").append(constraintName).append("\"");
        return stringBuilder.toString();
    }

    @Override
    public List<String> excelDataIns(Table table, List<Map<String, Object>> insertDataList, List<String> columns) {
        return null;
    }

    @Override
    public List<String> dropOrAddIdentityInsert(String schemaName, String table, List<String> column, Boolean open) {
        return null;
    }

    @Override
    public String getIndexWhere(String tableName, String schemaName, List<String> tableColumnList, List<String> primaryKeyColumnList, Map<String, Object> map) {
        return null;
    }

    @Override
    public List<String> addObjectRole(String schemaName, String userName, String tableName, List<TableObjectRoleData> newObjectRoleData) {
        return null;
    }

    @Override
    public List<String> delObjectRole(String schemaName, String userName, String tableName, List<TableObjectRoleData> newObjectRoleData) {
        return null;
    }

    @Override
    public String copyTable(String copySchemaName, String tableName, String schemaName, Boolean isData) {
        return null;
    }

    @Override
    public List<String> excelDataForUp(Table table, List<Map<String, Object>> dataList, List<String> columns) {
       return null;
    }

    @Override
    public String manageUserPassword(String userName, String newPassword) {
        return null;
    }

    @Override
    public String lockOrUnlock(String userName, Boolean isLock) {
        return null;
    }

    @Override
    public String createUser(String name) {
        return null;
    }

    @Override
    public String dropUser(String username) {
        return null;
    }

    @Override
    public String deleteTable(Table table) {
        return null;
    }

    @Override
    public String selectTable(Table table,List<List<Object>> dataAllList, List<String> fromColumnList, List<String> toColumnList) {
        return null;
    }

    @Override
    public String addUserRole(String userName, String role, String adminRole) {
        StringBuilder stringBuilder = new StringBuilder(" GRANT \"%s\" TO \"%s\"");
        if (StringUtils.isNotBlank(adminRole)) {
            stringBuilder.append(" WITH ADMIN OPTION ");
        }
        return String.format(stringBuilder.toString(), role, userName);
    }
//
//    @Override
//    public String addDefaultUserRole(String userName, List<String> role) {
//        StringBuilder stringBuilder = new StringBuilder(" ALTER USER \"'%s'\" DEFAULT ROLE \"'%s'\";");
//        return String.format(stringBuilder.toString(), userName, String.join("\",\"", role));
//    }
//
//    @Override
//    public String delDefaultUserRole(String userName) {
//        StringBuilder stringBuilder = new StringBuilder(" ALTER USER \"'%s'\" DEFAULT ROLE NONE;");
//        return String.format(stringBuilder.toString(), userName);
//    }

    @Override
    public String delUserRole(String userName, String role) {
        StringBuilder stringBuilder = new StringBuilder(" REVOKE \"%s\" FROM \"%s\"");
        return String.format(stringBuilder.toString(), role, userName);
    }

    @Override
    public String createUser(String userName, String password, String defaultTableSpace, String temptableSpace) {
        StringBuilder stringBuilder = new StringBuilder(" CREATE USER \"%s\" IDENTIFIED  BY \"%s\" DEFAULT TABLESPACE \"%s\" TEMPORARY TABLESPACE \"%s\"");
        return String.format(stringBuilder.toString(), userName, password, defaultTableSpace, temptableSpace, defaultTableSpace);
    }

    @Override
    public String dropTableConstraint(String schema, String tableName, String keyName, String name) {
        StringBuilder stringBuilder = new StringBuilder();
        if (StringUtils.isNotBlank(keyName)) {
            stringBuilder.append("ALTER TABLE \"%s\".\"%s\" DROP CONSTRAINT \"%s\"");
            return String.format(stringBuilder.toString(), schema, tableName, keyName);
        } else {
            stringBuilder.append("DROP INDEX \"%s\".\"%s\"");
            return String.format(stringBuilder.toString(), schema, name);
        }
    }


    @Override
    public String createTableConstraint(String schemaName, String tableName, String columnName, String constraintName, String constraintType, String check, String forkSchema, String forkTableName, String forkColumn) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ALTER TABLE \"%s\".\"%s\" ");
        String s = null;
        String[] split = columnName.split(",");
        if (split.length > 1) {
            s = String.join("\",\"", split);
        } else {
            s = columnName;
        }
        String forkColumnValue = null;
        if (StrUtil.isNotBlank(forkColumn)) {
            String[] forkColumnList = columnName.split(",");
            if (forkColumnList.length > 1) {
                forkColumnValue = String.join("\",\"", forkColumnList);
            } else {
                forkColumnValue = forkColumn;
            }
        }
        switch (Objects.requireNonNull(ConstraintTypeEnum.getByType(constraintType))) {
            case PRIMARY_KEY:
                stringBuilder.append("ADD CONSTRAINT \"%s\" PRIMARY KEY (\"%s\")");
                return String.format(stringBuilder.toString(), schemaName, tableName, constraintName ,s);
            case UNIQUE:
                stringBuilder.append("ADD CONSTRAINT \"%s\" UNIQUE (\"%s\")");
                return String.format(stringBuilder.toString(), schemaName, tableName, constraintName ,s);
            case VIRTUAL:
            case FOREIGN_KEY:
                stringBuilder.append("ADD CONSTRAINT \"%s\" FOREIGN KEY (\"%s\") REFERENCES \"%s\".\"%s\" (\"%s\")");
                return String.format(stringBuilder.toString(), schemaName, tableName, constraintName ,columnName, forkSchema, forkTableName, forkColumnValue);
            case CHECK:
                stringBuilder.append("ADD CONSTRAINT \"%s\" CHECK (%s)");
                return String.format(stringBuilder.toString(), schemaName, tableName, constraintName , check);
            default:
                return null;
        }
    }
}
