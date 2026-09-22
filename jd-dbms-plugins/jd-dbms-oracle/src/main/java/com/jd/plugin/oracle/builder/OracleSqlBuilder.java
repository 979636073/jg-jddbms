package com.jd.plugin.oracle.builder;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Lists;
import com.jd.common.constant.Constants;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.plugin.oracle.type.OracleColumnTypeEnum;
import com.jd.plugin.oracle.type.OracleIndexTypeEnum;
import com.jd.spi.MetaData;
import com.jd.spi.SqlBuilder;
import com.jd.spi.enums.DataTypeEnum;
import com.jd.spi.jdbc.DefaultSqlBuilder;
import com.jd.spi.model.*;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.util.SqlUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class OracleSqlBuilder extends DefaultSqlBuilder {
    private String format(String tableName) {
        return "\"" + tableName + "\"";
    }

    private static final String spot = ".";

    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();

        script.append("CREATE TABLE ").append("\"").append(table.getSchemaName()).append("\".\"").append(table.getName()).append("\" (").append("\n");

        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            OracleColumnTypeEnum typeEnum = OracleColumnTypeEnum.getByType(column.getColumnType());
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }

        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n);");

        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            OracleIndexTypeEnum indexTypeEnum = OracleIndexTypeEnum.getByType(tableIndex.getType());
            script.append("\n").append("").append(indexTypeEnum.buildIndexScript(tableIndex)).append(";");
        }

        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType()) || StringUtils.isBlank(column.getComment())) {
                continue;
            }
            script.append("\n").append(buildComment(column)).append(";");
        }

        if (StringUtils.isNotBlank(table.getComment())) {
            script.append("\n").append(buildTableComment(table)).append(";");
        }


        return script.toString();
    }

    private String buildTableComment(Table table) {
        StringBuilder script = new StringBuilder();
        script.append("COMMENT ON TABLE ").append("\"").append(table.getSchemaName()).append("\".\"").append(table.getName()).append("\" IS '").append(table.getComment()).append("'");
        return script.toString();
    }

    private String buildComment(TableColumn column) {
        StringBuilder script = new StringBuilder();
        if (StrUtil.isEmpty(column.getComment())) {
            if (Objects.isNull(column.getOldColumn())) {
                return "";
            } else {
                if (StrUtil.isEmpty(column.getOldColumn().getComment())) {
                    return "";
                }
            }
        }
        if (StrUtil.isNotEmpty(column.getComment())) {
            script.append("COMMENT ON COLUMN ").append("\"").append(column.getSchemaName()).append("\".\"").append(column.getTableName()).append("\".\"").append(column.getName()).append("\" IS '").append(column.getComment()).append("'");
        } else {
            script.append("COMMENT ON COLUMN ").append("\"").append(column.getSchemaName()).append("\".\"").append(column.getTableName()).append("\".\"").append(column.getName()).append("\" IS ''");
        }
        script.append(";\n");
        return script.toString();
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();

        if (!StringUtils.equalsIgnoreCase(oldTable.getName(), newTable.getName())) {
            script.append("ALTER TABLE ").append("\"").append(oldTable.getSchemaName()).append("\".\"").append(oldTable.getName()).append("\"");
            script.append(" ").append("RENAME TO ").append("\"").append(newTable.getName()).append("\"").append(";\n");
        }
        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            script.append("").append(buildTableComment(newTable)).append(";\n");
        }

        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                OracleIndexTypeEnum mysqlIndexTypeEnum = OracleIndexTypeEnum.getByType(tableIndex.getType());
                script.append("\t").append(mysqlIndexTypeEnum.buildModifyIndex(tableIndex)).append(";\n");
            }
        }
        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus())) {
                OracleColumnTypeEnum typeEnum = OracleColumnTypeEnum.getByType(tableColumn.getColumnType());
                script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(";\n");
                if (StringUtils.isNotBlank(tableColumn.getComment())) {
                    script.append("\n").append(buildComment(tableColumn)).append(";\n");
                }
            }
        }


        if (script.length() > 2) {
            script = new StringBuilder(script.substring(0, script.length() - 2));
            script.append(";");
        }
        return script.toString();
    }

    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        int startRow = offset;
        int endRow = offset + pageSize;
        StringBuilder sqlBuilder = new StringBuilder(sql.length() + 120);
        if (startRow > 0) {
            sqlBuilder.append("SELECT * FROM ( ");
        }
        if (endRow > 0) {
            sqlBuilder.append(" SELECT TMP_PAGE.*, ROWNUM CAHT2DB_AUTO_ROW_ID FROM ( ");
        }
        sqlBuilder.append("\n");
        sqlBuilder.append(sql);
        sqlBuilder.append("\n");
        if (endRow > 0) {
            sqlBuilder.append(" ) TMP_PAGE WHERE ROWNUM <= ");
            sqlBuilder.append(endRow);
        }
        if (startRow > 0) {
            sqlBuilder.append(" ) WHERE CAHT2DB_AUTO_ROW_ID > ");
            sqlBuilder.append(startRow);
        }
        return sqlBuilder.toString();
    }

    /**
     * 创建存储过程模板
     *
     * @param databaseName
     * @param schemaName
     * @param procedureName 存储过程名称
     * @return
     */
    @Override
    public String createProcedureTemplate(String databaseName, String schemaName, String procedureName) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("CREATE OR REPLACE PROCEDURE " + format(schemaName != null ? schemaName : databaseName) + spot + procedureName + " IS tmpVar NUMBER ")
                .append(" BEGIN \n")
                .append(" tmpVar := 0;\n")
                .append(" EXCEPTION \n")
                .append(" WHEN NO_DATA_FOUND THEN \n")
                .append(" NULL \n")
                .append(" WHEN OTHERS THEN \n")
                .append(" RAISE; \n")
                .append(" END " + procedureName + ";");
        return stringBuilder.toString();
    }

    /**
     * 创建函数模板
     *
     * @param databaseName
     * @param schemaName
     * @param functionName
     * @return
     */
    @Override
    public String createFunctionTemplate(String databaseName, String schemaName, String functionName) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("CREATE OR REPLACE FUNCTION " + format(schemaName != null ? schemaName : databaseName) +
                        spot + functionName + " RETURN NUMBER IS tmpVar NUMBER ")
                .append(" BEGIN \n")
                .append(" tmpVar := 0; \n")
                .append(" RETURN tmpVar;\n")
                .append(" EXCEPTION \n")
                .append(" WHEN NO_DATA_FOUND THEN \n")
                .append(" NULL \n")
                .append(" WHEN OTHERS THEN \n")
                .append(" RAISE; \n")
                .append(" END " + functionName + ";");
        return stringBuilder.toString();
    }

    /**
     * 创建触发器模板
     *
     * @param databaseName
     * @param schemaName
     * @param triggerName
     * @return
     */
    @Override
    public String createTriggersTemplate(String databaseName, String schemaName, String triggerName, String tableName,
                                         boolean before, boolean after) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("CREATE OR REPLACE TRIGGER " + triggerName)
                .append(before ? " BEFORE" : after ? " AFTER" : "")
                .append("  INSERT " +
                        ("OR UPDATE  ") +
                        ("OR DELETE "))
                .append(
                        " ON " +
                                format(schemaName != null ? schemaName : databaseName) +
                                spot + tableName + "\n")
                .append("DECLARE \n")
                .append("tmpVar NUMBER; \n")
                .append(" BEGIN \n")
                .append(" END " + triggerName + ";");
        return stringBuilder.toString();
    }

    @Override
    public String deleteProcedure(String databaseName, String schemaName, String procedureName) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("DROP PROCEDURE " + format(schemaName != null ? schemaName : databaseName) + spot + procedureName);
        return stringBuilder.toString();
    }

    @Override
    public String deleteFuction(String databaseName, String schemaName, String functionName) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("DROP FUNCTION " + format(schemaName != null ? schemaName : databaseName) + spot + functionName);
        return stringBuilder.toString();
    }

    @Override
    public String deleteTriggers(String databaseName, String schemaName, String triggerName) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("DROP TRIGGER " + format(schemaName != null ? schemaName : databaseName) + spot + triggerName);
        return stringBuilder.toString();
    }


    @Override
    public String createGrantTemplate(String databaseName, String schemaName, String tableName, String toGrantUser,
                                      Boolean insert, Boolean update, Boolean delete) {
        return buildTablePrivilegeSql(true, databaseName, schemaName, tableName, toGrantUser,
                insert, update, delete);
    }

    @Override
    public String deleteGrantTemplate(String databaseName, String schemaName, String tableName, String toGrantUser, Boolean insert, Boolean update, Boolean delete) {
        return buildTablePrivilegeSql(false, databaseName, schemaName, tableName, toGrantUser,
                insert, update, delete);
    }
//    @Override
//    public String buildCreateSchemaSql(Schema schema){
//        StringBuilder sqlBuilder = new StringBuilder();
//        sqlBuilder.append("CREATE SCHEMA \""+schema.getName()+"\"");
//        if(StringUtils.isNotBlank(schema.getOwner())){
//            sqlBuilder.append(" AUTHORIZATION ").append(schema.getOwner());
//        }
//        if(StringUtils.isNotBlank(schema.getComment())){
//            sqlBuilder.append("; COMMENT ON SCHEMA \"").append(schema.getName()).append("\" IS '").append(schema.getComment()).append("';");
//        }
//        return sqlBuilder.toString();
//    }

    @Override
    public String dropTableSql(String schemaName, String tableName) {
        return "DROP TABLE \"" + schemaName + "\".\"" + tableName + "\"";
    }


    @Override
    public String createSpace(String fileName, String spaceName, String size, String maxSize, String autoSize) {
        StringBuilder stringBuilder = new StringBuilder();
        String sql = "";
        stringBuilder.append(String.format("CREATE TABLESPACE \"%s\"  datafile '%s' size %s", spaceName, fileName, size));
        if (StringUtils.isNotBlank(autoSize) && StringUtils.isNotBlank(maxSize)) {
            stringBuilder.append(" AUTOEXTEND ON NEXT %s MAXSIZE ").append(maxSize);
            sql = String.format(stringBuilder.toString(), autoSize);
        } else {
            if (StringUtils.isNotBlank(autoSize)) {
                stringBuilder.append(" AUTOEXTEND ON NEXT %s");
                sql = String.format(stringBuilder.toString(), autoSize);
            }
            if (StringUtils.isNotBlank(maxSize)) {
                stringBuilder.append(" AUTOEXTEND ON NEXT 1m MAXSIZE ").append(maxSize);
                sql = stringBuilder.toString();
            }
            if (StrUtil.isEmpty(sql)) {
                sql = stringBuilder.toString();
            }
        }
        return sql;
    }


    @Override
    public String manageUserPassword(String userName, String newPassword) {
        return "ALTER USER " + quoteIdentifier(userName) + " IDENTIFIED BY " + quoteIdentifier(newPassword);
    }

    @Override
    public String lockOrUnlock(String userName, Boolean isLock) {
        StringBuilder stringBuilder = new StringBuilder("ALTER USER ")
                .append(quoteIdentifier(userName))
                .append(" ACCOUNT ");
        if (!isLock) {
            stringBuilder.append("LOCK");
        }else {
            stringBuilder.append("UNLOCK");
        }
        return stringBuilder.toString();
    }

    @Override
    public String createUser(String name) {
        return "CREATE USER " + quoteIdentifier(name) + " IDENTIFIED BY <PASSWORD>";
    }

    @Override
    public String dropUser(String username) {
        return "DROP USER " + quoteIdentifier(username) + " CASCADE";
    }

    @Override
    public String dropTableConstraint(String schema, String tableName, String keyName, String name) {
        StringBuilder stringBuilder = new StringBuilder();
        if (StringUtils.isNotBlank(keyName)) {
            stringBuilder.append("ALTER TABLE \"%s\".\"%s\" DROP CONSTRAINT \"%s\"");
            return String.format(stringBuilder.toString(), schema, tableName, keyName);
        } else {
            stringBuilder.append("ALTER TABLE \"%s\".\"%s\" DROP CONSTRAINT \"%s\"");
//            stringBuilder.append("DROP INDEX \"%s\".\"%s\"");
            return String.format(stringBuilder.toString(), schema, tableName, name);
        }
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

    private List<String> assayColumn(Map<String, String> map) {
        List<String> list = new ArrayList<>();
        for (String s : map.keySet()) {
            if (map.get(s).contains(OracleColumnTypeEnum.TIMESTAMP.name()) || map.get(s).contains(OracleColumnTypeEnum.DATE.name())) {
                list.add(s);
            }
        }
        return list;
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
            String newSqlValue = SqlUtils.getSqlValue(newValue, header.getDataType());
            if(DataTypeEnum.DATETIME == DataTypeEnum.getByCode(header.getDataType())){
                String to_date = "TO_TIMESTAMP(%s, 'yyyy-mm-dd hh24:mi:ss.ff3')";
                if (StrUtil.isNotBlank(newSqlValue) && !"DEFAULT".equals(newSqlValue)) {
                    newSqlValue = String.format(to_date, newSqlValue);
                }
            }
            script.append(metaSchema.getMetaDataName(header.getName()))
                    .append(" = ")
                    .append(newSqlValue)
                    .append(",");
        }
        script.deleteCharAt(script.length() - 1);
        script.append(buildWhere(headerList, odlRow, metaSchema, keyColumns));
        return script.toString();
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
//        StringBuilder script = new StringBuilder();
//        script.append(" where ");
//        if (CollectionUtils.isEmpty(keyColumns)) {
//            for (int i = 1; i < row.size(); i++) {
//                String oldValue = row.get(i);
//                Header header = headerList.get(i);
//                String value = SqlUtils.getSqlValue(oldValue, header.getDataType());
//                if (value == null) {
//                    script.append(metaSchema.getMetaDataName(header.getName()))
//                            .append(" is null and ");
//                } else {
//                    if(DataTypeEnum.DATETIME == DataTypeEnum.getByCode(header.getDataType())){
//                        String to_date = "TO_TIMESTAMP(%s, 'yyyy-mm-dd hh24:mi:ss.ff3')";
//                        value = String.format(to_date, value);
//                    }
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
            //if (newValue != null) {
            Header header = headerList.get(i);
            //解决达梦自增序列问题
            if(header.getAutoIncrement().equals(1)){
                continue;
            }
            if (isView && i == 1) {
                continue;
            }
            String newSqlValue = SqlUtils.getSqlValue(newValue, header.getDataType());
            if(DataTypeEnum.DATETIME == DataTypeEnum.getByCode(header.getDataType())){
                String to_date = "TO_TIMESTAMP(%s, 'yyyy-mm-dd hh24:mi:ss.ff3')";
                if (StrUtil.isNotBlank(newSqlValue) && !"DEFAULT".equals(newSqlValue)) {
                    newSqlValue = String.format(to_date, newSqlValue);
                }
            }
            script.append(newSqlValue)
                    .append(",");
            //}
        }
        script.deleteCharAt(script.length() - 1);
        script.append(")");
        return script.toString();

    }

    @Override
    public String deleteTable(Table table) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("DROP TABLE \"" + table.getSchemaName() + "\"" + spot + "\"" + table.getName() + "\"");
        return stringBuilder.toString();
    }

    @Override
    public String selectTable(Table table, List<List<Object>> dataAllList, List<String> fromColumnList, List<String> toColumnList) {
        String demoTableSqlString = getDataTableString(dataAllList, table.getColumnList());
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" SELECT ");
        stringBuilder.append(getTableColmunString(table.getColumnList()));
        stringBuilder.append("  FROM ");
        stringBuilder.append(" ( ").append(demoTableSqlString).append(" ) ");
        stringBuilder.append("  A ");
        stringBuilder.append("  LEFT JOIN ");
        stringBuilder.append("  \"" + table.getSchemaName() + "\"" + spot + "\"" + table.getName() + "\"").append(" B ");
        stringBuilder.append(" ON  ");
        //根据主键进行封装约束条件
        String whereString = getWhereString(table.getColumnList());
        if (StringUtils.isEmpty(whereString)) {//如果返回的是空值，代表没有设置主键。没有主键代表数据全部都插入
            return "";
        } else {
            stringBuilder.append(whereString);
        }
        return stringBuilder.toString();
    }


    /**
     * 根据字段的主键约束封装数据比对条件
     *
     * @param tableColumnList
     * @return
     */
    private String getWhereString(List<TableColumn> tableColumnList) {
        StringBuilder stringBuilder = new StringBuilder();
        List<TableColumn> columnList = new ArrayList<>();
        for (TableColumn tableColumns : tableColumnList) {
            if (Objects.nonNull(tableColumns.getPrimaryKey()) && tableColumns.getPrimaryKey()) {
                columnList.add(tableColumns);
            }
        }
        if (columnList.size() > 0) {
            for (int i = 0; i < columnList.size(); i++) {
                stringBuilder.append("A.").append(columnList.get(i).getName()).append(" = ").append("B.").append(columnList.get(i).getName());
                if (i < columnList.size() - 1) {
                    stringBuilder.append(" AND ");
                }
            }
        }
        return stringBuilder.toString();
    }

    private String getTableColmunString(List<TableColumn> columnList) {
        StringBuffer stringBuffer = new StringBuffer();
        StringBuffer BstringBuffer = new StringBuffer();//用来拼接显示数据是否更新的状态值，1代表新增 ，0代表更新
        for (int i = 0; i < columnList.size(); i++) {
            stringBuffer.append("A.\"").append(columnList.get(i).getName()).append("\"");
            if (i < columnList.size() - 1) {
                stringBuffer.append(",");
            }
            if (i == 0) {
                BstringBuffer.append(" CASE WHEN B.\"").append(columnList.get(i).getName()).append("\"");
                BstringBuffer.append(" IS NULL THEN 1 ELSE 0 END AS ISUPDATE ");
            }
        }
        stringBuffer.append(",").append(BstringBuffer);
        return stringBuffer.toString();
    }

    /**
     * 根据字段的主键约束封装数据比对条件
     *
     * @param tableColumnList
     * @return
     */
    @Override
    public String getIndexWhere(String tableName, String schemaName, List<String> tableColumnList, List<String> primaryKeyColumnList, Map<String, Object> map) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" SELECT \"").append(String.join("\",\"", tableColumnList)).append("\" FROM \"").append(schemaName).append("\".\"")
                .append(tableName).append("\" WHERE ");
        for (String s : map.keySet()) {
            for (String key : primaryKeyColumnList) {
                if (s.equals(key)) {
                    stringBuilder.append("\"").append(key).append("\" = '").append(map.get(s)).append("' AND ");
                }
            }
        }
        return stringBuilder.substring(0, stringBuilder.length() - 4);
    }


    private String getDataTableString(List<List<Object>> dataAllList, List<TableColumn> columnList) {
        StringBuilder stringBuilder = new StringBuilder();
        for (List<Object> list : dataAllList) {
            StringBuilder chStringBuilder = new StringBuilder();
            chStringBuilder.append(" SELECT ");
            for (int i = 0; i < columnList.size(); i++) {
                TableColumn tableColumn = columnList.get(i);
                if (i > list.size() - 1) {
                    chStringBuilder.append("'' AS ").append(tableColumn.getName());
                } else {
                    if (Objects.isNull(list.get(i))) {
                        chStringBuilder.append("'null'").append(" AS ").append(tableColumn.getName());
                    } else {
                        chStringBuilder.append("'").append(list.get(i).toString().replace("'", "''")).append("'").append(" AS ").append(tableColumn.getName());
                    }
                }
                if (i < columnList.size() - 1) {
                    chStringBuilder.append(",");
                }
            }
            chStringBuilder.append(" FROM DUAL ");
            int intList = dataAllList.indexOf(list);
            if (intList < dataAllList.size() - 1) {
                chStringBuilder.append(" UNION ALL ");
            }
            stringBuilder.append(chStringBuilder);
        }
        return stringBuilder.toString();

    }
    private String getDeleteSql(String tableName, List<Header> headerList, List<String> row, MetaData metaSchema,
                                List<String> keyColumns) {
        StringBuilder script = new StringBuilder();
        script.append("DELETE FROM ").append(tableName).append("");
        script.append(buildWhere(headerList, row, metaSchema, keyColumns));
        return script.toString();
    }

    private String getColumnString(List<String> list) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < list.size(); i++) {
            stringBuffer.append("\"").append(list.get(i)).append("\"");
            if (i < list.size() - 1) {
                stringBuffer.append(",");
            }
        }
        return stringBuffer.toString();
    }

    /**
     * 根据EXCEL数据插入表
     *
     * @param dataList
     * @return
     */
    public List<String> excelDataIns(Table table, List<Map<String, Object>> dataList, List<String> columns) {
        String to_date = "TO_TIMESTAMP('%s', 'yyyy-mm-dd hh24:mi:ss.ff3')";
//        List<String> columns = new ArrayList<>(dataList.get(0).keySet());
        List<String> sqlList = new ArrayList<>();
        List<List<Object>> valueList = new ArrayList<>();
        for (Map<String, Object> stringObjectMap : dataList) {
            List<Object> list = new ArrayList<>(stringObjectMap.values());
            valueList.add(list);
        }
        Map<String, String> ColumnMaps = table.getColumnList().stream().collect(Collectors.toMap(TableColumn::getName, TableColumn::getColumnType, (ONE, TEO) -> ONE));
        List<String> dateColumnValues = assayColumn(ColumnMaps);
        List<Integer> indexList = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            if (dateColumnValues.contains(columns.get(i))) {
                indexList.add(i);
            }
        }
        //获取需要插入的字段的信息
        String columnString = getColumnString(columns);
        for (List<Object> s : valueList) {
            StringBuilder stringBuffer = new StringBuilder();
            stringBuffer.append(" INSERT INTO \"").append(table.getSchemaName()).append("\"").append(spot).append("\"").append(table.getName()).append("\"");
            stringBuffer.append(" ( ");
            stringBuffer.append(columnString);
            stringBuffer.append(" ) VALUES (");
            for (int i = 0; i < s.size(); i++) {
                String value = s.get(i).toString().trim();
                if (indexList.contains(i)) {
                    if (StringUtils.isBlank(value) || "NULL".equals(value.toUpperCase(Locale.ROOT))) {
                        stringBuffer.append(" NULL ,");
                    } else {
                        stringBuffer.append(String.format(to_date, value)).append(",");
                    }
                } else {
                    if (StringUtils.isBlank(value) || "NULL".equals(value.toUpperCase(Locale.ROOT))) {
                        stringBuffer.append(" NULL ,");
                    } else {
                        stringBuffer.append("'").append(value.replaceAll("'", "''")).append("'").append(",");
                    }
                }
            }
            stringBuffer = new StringBuilder(stringBuffer.substring(0, stringBuffer.length() - 1));
            stringBuffer.append(")");
            sqlList.add(stringBuffer.toString());
        }
        return sqlList;
    }

    @Override
    public String copyTable(String copySchemaName, String tableName, String schemaName, Boolean isData) {
        String sql = "CREATE TABLE \"" + copySchemaName + "\".\"" + tableName + "_COPY\" AS SELECT * FROM \"" + schemaName +"\".\"" + tableName + "\"";
        if (!isData) {
            sql = sql + " WHERE 1 = 0";
        }
        return sql;
    }


    /**
     * 根据EXCEL数据更新表
     *
     * @param dataList
     * @return
     */
    public List<String> excelDataForUp(Table table, List<Map<String, Object>> dataList, List<String> columns) {
        String to_date = "TO_TIMESTAMP('%s', 'yyyy-mm-dd hh24:mi:ss.ff3')";
//        List<String> columns = new ArrayList<>(dataList.get(0).keySet());
        List<String> sqlList = new ArrayList<>();
        List<List<Object>> valueList = new ArrayList<>();
        for (Map<String, Object> stringObjectMap : dataList) {
            List<Object> list = new ArrayList<>(stringObjectMap.values());
            valueList.add(list);
        }
        Map<String, String> ColumnMaps = table.getColumnList().stream().collect(Collectors.toMap(TableColumn::getName, TableColumn::getColumnType, (ONE, TEO) -> ONE));
        List<String> dateColumnValues = assayColumn(ColumnMaps);
        List<String> columnKey = table.getColumnList().stream().filter(TableColumn::getPrimaryKey).map(TableColumn::getName).collect(Collectors.toList());
        Map<String, Integer> keys = new HashMap<>();
        List<Integer> collect = new ArrayList<>(keys.values());
        for (int i = 0; i < columns.size(); i++) {
            for (String s : columnKey) {
                if (columns.get(i).equals(s)) {
                    keys.put(s, i);
                }
            }
        }
        for (List<Object> objects : valueList) {
            StringBuilder stringBuffer = new StringBuilder();
            stringBuffer.append(" UPDATE  \"").append(table.getSchemaName()).append("\"").append(spot).append("\"").append(table.getName()).append("\"");
            stringBuffer.append(" SET ");
            for (int i = 0; i < columns.size(); i++) {
                if (collect.contains(i)) {
                    continue;
                }
                String value = objects.get(i).toString().trim();
                if (dateColumnValues.contains(columns.get(i))) {
                    if (StringUtils.isBlank(value) || "NULL".equals(value.toUpperCase(Locale.ROOT))) {
                        stringBuffer.append("\"").append(columns.get(i)).append("\"").append(" = NULL ,");
                    } else {
                        stringBuffer.append("\"").append(columns.get(i)).append("\"").append(" = ").append(String.format(to_date, value)).append(",");
                    }
                } else {
                    if (StringUtils.isBlank(value) || "NULL".equals(value.toUpperCase(Locale.ROOT))) {
                        stringBuffer.append("\"").append(columns.get(i)).append("\"").append(" = NULL ,");
                    } else {
                        stringBuffer.append("\"").append(columns.get(i)).append("\"").append(" = '").append(value.replaceAll("'", "''")).append("',");
                    }
                }
            }
            stringBuffer = new StringBuilder(stringBuffer.substring(0, stringBuffer.length() - 1));
            stringBuffer.append(" WHERE ");
            for (int i = 0; i < columnKey.size(); i++) {
                if (i >= columnKey.size() - 1) {
                    stringBuffer.append("\"").append(columnKey.get(i)).append("\"").append(" = '").append(objects.get(keys.get(columnKey.get(i))).toString().trim()).append("'");
                } else {
                    stringBuffer.append("\"").append(columnKey.get(i)).append("\"").append(" = '").append(objects.get(keys.get(columnKey.get(i))).toString().trim()).append("'").append(" AND ");
                }
            }
            sqlList.add(stringBuffer.toString());
        }
        return sqlList;
    }


    @Override
    public List<String> addObjectRole(String schemaName, String userName, String tableName, List<TableObjectRoleData> newObjectRoleData) {
        List<TableObjectRoleData> collect = newObjectRoleData.stream()
                .filter(data -> Boolean.TRUE.equals(data.getRule()))
                .collect(Collectors.toList());
        List<String> list = new ArrayList<>();
        String sql = "grant %s on \"%s\".\"%s\" to \"%s\"";
        for (TableObjectRoleData data : collect) {
            String s = String.format(sql, data.getDesc(), schemaName, tableName, userName);
            StringBuilder stringBuilder = new StringBuilder(s);
            if (Boolean.TRUE.equals(data.getToRule())) {
                stringBuilder.append(" with grant option");
            }
            list.add(stringBuilder.toString());
        }
        return list;
    }

    @Override
    public List<String> delObjectRole(String schemaName, String userName, String tableName, List<TableObjectRoleData> newObjectRoleData) {
        List<String> list = new ArrayList<>();
        List<String> collect = newObjectRoleData.stream()
                .filter(data -> Boolean.TRUE.equals(data.getRule()))
                .map(TableObjectRoleData::getDesc)
                .collect(Collectors.toList());
        String sql = "REVOKE %s ON \"%s\".\"%s\" FROM \"%s\"";
        for (String s : collect) {
            list.add(String.format(sql, s,schemaName, tableName, userName));
        }

        return list;
    }
}
