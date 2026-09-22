package com.jd.plugin.dm.builder;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.jd.plugin.dm.type.DMColumnTypeEnum;
import com.jd.plugin.dm.type.DMIndexTypeEnum;
import com.jd.spi.enums.EditStatus;
import com.jd.spi.jdbc.DefaultSqlBuilder;
import com.jd.spi.model.Schema;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableIndex;
import com.jd.spi.model.TableObjectRoleData;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class DMSqlBuilder extends DefaultSqlBuilder {

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
            DMColumnTypeEnum typeEnum = DMColumnTypeEnum.getByType(column.getColumnType());
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }
        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n);");

        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            DMIndexTypeEnum indexTypeEnum = DMIndexTypeEnum.getByType(tableIndex.getType());
            script.append("\n").append("").append(indexTypeEnum.buildIndexScript(tableIndex)).append(";");
        }

        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType()) || StringUtils.isBlank(column.getComment())) {
                continue;
            }
            script.append("\n").append(buildComment(column));
        }

        if (StringUtils.isNotBlank(table.getComment())) {
            script.append("\n").append(buildTableComment(table)).append(";");
        }
        script.append("\n");
        for (TableColumn column : table.getColumnList()) {
            if (column != null && column.getAutoIncrement() != null && column.getAutoIncrement().equals(true)) {
                script.append("ALTER TABLE ").append("\"").append(table.getSchemaName()).append("\".\"").append(table.getName()).append("\"")
                        .append(" ADD COLUMN ").append("\"").append(column.getName()).append("\"").append(" identity(1, 1);\n");
            }
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
                DMIndexTypeEnum mysqlIndexTypeEnum = DMIndexTypeEnum.getByType(tableIndex.getType());
                script.append("\t").append(mysqlIndexTypeEnum.buildModifyIndex(tableIndex, newTable.getConstraintName())).append(";\n");
            }
        }
        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus())) {
                tableColumn.setOldColumn(getOldColumn(tableColumn, oldTable.getColumnList()));
                DMColumnTypeEnum typeEnum = DMColumnTypeEnum.getByType(tableColumn.getColumnType());
                script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(";\n");
                if (!EditStatus.DELETE.name().equals(tableColumn.getEditStatus())) {
                    script.append("\t").append(buildComment(tableColumn));
                }
            }
        }



        String[] split = script.toString().split(";");
        List<String> sql = new ArrayList<>();
        for (String s : split) {
            if (StringUtils.isNotBlank(s) && !sql.contains(s)) {
                sql.add(s);
            }
        }
        script = new StringBuilder(String.join(";", sql)).append(";");
        return script.toString();
    }

    private TableColumn getOldColumn(TableColumn tableColumn, List<TableColumn> columnList) {
        for (TableColumn column : columnList) {
            if (StringUtils.equalsIgnoreCase(column.getName(), tableColumn.getOldName())) {
                return column;
            }
        }
        return null;
    }


    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        StringBuilder sqlStr = new StringBuilder(sql.length() + 17);
        sqlStr.append(sql);
        if (offset == 0) {
            sqlStr.append(" LIMIT ");
            sqlStr.append(pageSize);
        } else {
            sqlStr.append(" LIMIT ");
            sqlStr.append(pageSize);
            sqlStr.append(" OFFSET ");
            sqlStr.append(offset);
        }
        return sqlStr.toString();
    }

    @Override
    public String buildCreateSchemaSql(Schema schema) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE SCHEMA \"" + schema.getName() + "\"");
        if (StringUtils.isNotBlank(schema.getOwner())) {
            sqlBuilder.append(" AUTHORIZATION ").append(schema.getOwner());
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
        stringBuilder.append("CREATE OR REPLACE TRIGGER ")
                .append(triggerName)
                .append(before ? " BEFORE" : after ? " AFTER" : "")
                .append("\n INSERT OR UPDATE OR DELETE")
                .append("\n ON \n")
                .append(format(schemaName != null ? schemaName : databaseName))
                .append(spot).append(tableName)
                .append("\n BEGIN PRINT '一段描述'; END;");
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
    public String deleteGrantTemplate(String databaseName, String schemaName, String tableName, String toGrantUser,
                                      Boolean insert, Boolean update, Boolean delete) {
        return buildTablePrivilegeSql(false, databaseName, schemaName, tableName, toGrantUser,
                insert, update, delete);
    }

    @Override
    public String dropTableSql(String schemaName, String tableName) {
        return "DROP TABLE IF EXISTS \"" + schemaName + "\".\"" + tableName + "\"";
    }

    @Override
    public String createSpace(String fileName, String spaceName, String size, String maxSize, String autoSize) {
        StringBuilder stringBuilder = new StringBuilder();
        String sql = "";
        stringBuilder.append("CREATE TABLESPACE ").append("\"").append(spaceName).append("\"").append(" datafile ").append("'").append(fileName).append("'").append(" size ").append(size);
        if (StringUtils.isNotBlank(autoSize) && StringUtils.isNotBlank(maxSize)) {
            stringBuilder.append(" AUTOEXTEND ON NEXT ").append(autoSize).append(" MAXSIZE ").append(maxSize).append(";");
            sql = String.format(stringBuilder.toString(), autoSize);
        } else {
            if (StringUtils.isNotBlank(autoSize)) {
                stringBuilder.append(" AUTOEXTEND ON NEXT ").append(autoSize).append(";");
            }
            if (StringUtils.isNotBlank(maxSize)) {
                stringBuilder.append(" AUTOEXTEND ON NEXT 1m MAXSIZE ").append(maxSize).append(";");
            }
            sql = stringBuilder.toString();
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
        } else {
            stringBuilder.append("UNLOCK");
        }
        return stringBuilder.toString();
    }

    @Override
    public String createUser(String name) {
        return "CREATE USER " + quoteIdentifier(name) + " IDENTIFIED BY <PASSWORD>";
    }


    @Override
    public String deleteTable(Table table) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("DROP TABLE IF EXISTS \"" + table.getSchemaName() + "\"" + spot + "\"" + table.getName() + "\"");
        return stringBuilder.toString();
    }

    /**
     * Excel预览数据时分类处理数据的类型
     * @param table
     * @param dataAllList
     * @param fromColumnList
     * @param toColumnList
     * @return
     */
    @Override
    public String selectTable(Table table, List<List<Object>> dataAllList, List<String> fromColumnList, List<String> toColumnList) {
        String demoTableSqlString = getDataTableString(dataAllList, toColumnList);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" SELECT ");
        stringBuilder.append(getTableColmunString(toColumnList));
        stringBuilder.append("  FROM ");
        stringBuilder.append(" ( ").append(demoTableSqlString).append(" ) ");
        stringBuilder.append("  A ");
        stringBuilder.append("  LEFT JOIN ");
        stringBuilder.append("  \"" + table.getSchemaName() + "\"" + spot + "\"" + table.getName() + "\"").append(" B ");
        stringBuilder.append(" ON  ");
        //根据主键进行封装约束条件
        String whereString = getWhereString(table.getColumnList());
        stringBuilder.append(whereString);
        return stringBuilder.toString();
    }

    @Override
    public String dropUser(String username) {
        return "DROP USER " + quoteIdentifier(username) + " CASCADE";
    }

    private String getTableColmunString(List<String> columnList) {
        StringBuffer stringBuffer = new StringBuffer();
        StringBuffer BstringBuffer = new StringBuffer();//用来拼接显示数据是否更新的状态值，1代表新增 ，0代表更新
        for (int i = 0; i < columnList.size(); i++) {
            stringBuffer.append("A.\"").append(columnList.get(i)).append("\"");
            if (i < columnList.size() - 1) {
                stringBuffer.append(",");
            }
            if (i == 0) {
                BstringBuffer.append(" CASE WHEN B.\"").append(columnList.get(i)).append("\"");
                BstringBuffer.append(" IS NULL THEN 1 ELSE 0 END AS ISUPDATE ");
            }
        }
        stringBuffer.append(",").append(BstringBuffer);
        return stringBuffer.toString();
    }

    private String getDataTableString(List<List<Object>> dataAllList, List<String> columnList) {
        StringBuilder stringBuilder = new StringBuilder();
        for (List<Object> list : dataAllList) {
            StringBuilder chStringBuilder = new StringBuilder();
            chStringBuilder.append(" SELECT ");
            for (int i = 0; i < columnList.size(); i++) {
                String column = columnList.get(i);
                if (i > list.size() - 1) {
                    chStringBuilder.append("'' AS \"").append(column).append("\"");
                } else {
                    if (Objects.nonNull(list.get(i))) {
                        chStringBuilder.append("'").append(list.get(i).toString().replace("'", "''")).append("'").append(" AS \"").append(column).append("\"");
                    } else {
                        chStringBuilder.append("'null'").append(" AS \"").append(column).append("\"");
                    }
                }

                if (i < columnList.size() - 1) {
                    chStringBuilder.append(",");
                }
            }
            int intList = dataAllList.indexOf(list);
            if (intList < dataAllList.size() - 1) {
                chStringBuilder.append(" UNION ALL ");
            }
            stringBuilder.append(chStringBuilder);
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
                stringBuilder.append("A.\"").append(columnList.get(i).getName()).append("\" = ").append("B.\"").append(columnList.get(i).getName()).append("\"");
                if (i < columnList.size() - 1) {
                    stringBuilder.append(" AND ");
                }
            }
        }
        return stringBuilder.toString();
    }

    /**
     * 根据字段的主键约束封装数据比对条件
     *
     * @param tableColumnList
     * @return
     */
    @Override
    public String getIndexWhere(String tableName, String schemaName, List<String> tableColumnList, List<String> primaryList, Map<String, Object> map) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" SELECT \"").append(String.join("\",\"", tableColumnList)).append("\" FROM \"").append(schemaName).append("\".\"")
                .append(tableName).append("\" WHERE ");
        for (String s : map.keySet()) {
            for (String key : primaryList) {
                if (s.equals(key)) {
                    stringBuilder.append("\"").append(key).append("\" = '").append(map.get(s)).append("' AND ");
                }
            }
        }
        return stringBuilder.substring(0, stringBuilder.length() - 4);
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
        String sql = "REVOKE %s ON TABLE \"%s\".\"%s\" FROM \"%s\"";
        for (String s : collect) {
            list.add(String.format(sql, s,schemaName, tableName, userName));
        }

        return list;
    }


    @Override
    public String copyTable(String copySchemaName, String tableName, String schemaName, Boolean isData) {
        String sql = "CREATE TABLE \"" + copySchemaName + "\".\"" + tableName + "_COPY\" AS SELECT * FROM \"" + schemaName +"\".\"" + tableName + "\"";
        if (!isData) {
            sql = sql + " LIMIT 0";
        }
        return sql;
    }


    /**
     * 根据EXCEL数据插入表
     *
     * @param dataList
     * @return
     */
    public List<String> excelDataIns(Table table, List<Map<String, Object>> dataList, List<String> columns) {
        List<String> sqlList = new ArrayList<>();
        List<List<Object>> valueList = new ArrayList<>();
        for (Map<String, Object> stringObjectMap : dataList) {
            List<Object> list = new ArrayList<>(stringObjectMap.values());
            valueList.add(list);
        }
        //获取需要插入的字段的信息
        String columnString = getColumnString(columns);
        for (List<Object> s : valueList) {
            StringBuilder stringBuffer = new StringBuilder();
            stringBuffer.append(" INSERT INTO \"").append(table.getSchemaName()).append("\"").append(spot).append("\"").append(table.getName()).append("\"");
            stringBuffer.append(" ( ");
            stringBuffer.append(columnString);
            stringBuffer.append(" ) VALUES (");
            for (Object o : s) {
                String value = o.toString().trim();
                if (StringUtils.isBlank(value) || "NULL".equals(value.toUpperCase(Locale.ROOT))) {
                    stringBuffer.append(" NULL ,");
                } else {
                    stringBuffer.append("'").append(value.replaceAll("'", "''")).append("'").append(",");
                }
            }
            stringBuffer = new StringBuilder(stringBuffer.substring(0, stringBuffer.length() - 1));
            stringBuffer.append(");");
            sqlList.add(stringBuffer.toString());
        }
        return sqlList;
    }

    @Override
    public List<String> dropOrAddIdentityInsert(String schemaName, String table, List<String> columns, Boolean open) {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> sqlList = new ArrayList<>();
        if (open) {
            for (String column : columns) {
                String addIdentityInsert = "alter table \"'%s'\".\"'%s'\" add column \"'%s'\" identity(1, 1);";
                stringBuilder.append(addIdentityInsert);
                String sql = String.format(stringBuilder.toString(), schemaName, table, column);
                sqlList.add(sql.replace("'", ""));
            }

        } else {
            String dropIdentityInsert = "alter table \"'%s'\".\"'%s'\" drop identity;";
            stringBuilder.append(dropIdentityInsert);
            String sql = String.format(stringBuilder.toString(), schemaName, table);
            sqlList.add(sql.replace("'", ""));
        }
        return sqlList;
    }


    /**
     * 根据EXCEL数据更新表
     *
     * @param dataList
     * @return
     */
    public List<String> excelDataForUp(Table table, List<Map<String, Object>> dataList, List<String> columns) {
        List<String> sqlList = new ArrayList<>();
        List<List<Object>> valueList = new ArrayList<>();
        for (Map<String, Object> stringObjectMap : dataList) {
            List<Object> list = new ArrayList<>(stringObjectMap.values());
            valueList.add(list);
        }
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
                if (StringUtils.isBlank(value) || "NULL".equals(value.toUpperCase(Locale.ROOT))) {
                    stringBuffer.append("\"").append(columns.get(i)).append("\"").append(" = NULL ,");
                } else {
                    stringBuffer.append("\"").append(columns.get(i)).append("\"").append(" = '").append(value.replaceAll("'", "''")).append("',");
                }
            }
            stringBuffer = new StringBuilder(stringBuffer.substring(0, stringBuffer.length() - 1));
            stringBuffer.append(" WHERE ");
            for (int i = 0; i < columnKey.size(); i++) {
                if (i >= columnKey.size() - 1) {
                    stringBuffer.append("\"").append(columnKey.get(i)).append("\"").append(" = '").append(objects.get(keys.get(columnKey.get(i))).toString().trim()).append("';");
                } else {
                    stringBuffer.append("\"").append(columnKey.get(i)).append("\"").append(" = '").append(objects.get(keys.get(columnKey.get(i))).toString().trim()).append("'").append(" AND ");
                }
            }
            sqlList.add(stringBuffer.toString());
        }
        return sqlList;
    }

    private String getUpdateONString(List<TableColumn> columnList) {//根据设置表的主键进行约束
        List<TableColumn> newColumList = new ArrayList<>();
        for (TableColumn tableColumn : columnList) {
            if (tableColumn.getPrimaryKey()) {
                newColumList.add(tableColumn);
            }
        }
        StringBuffer faStringBuffer = new StringBuffer();
        if (newColumList.size() > 0) {
            for (int j = 0; j < newColumList.size(); j++) {
                faStringBuffer.append(" A.\"").append(newColumList.get(j).getName()).append("\"").append(" = ").append(" B.\"").append(newColumList.get(j).getName()).append("\"");
                if (j < newColumList.size() - 1) {
                    faStringBuffer.append(" AND ");
                }
            }
        }

        return faStringBuffer.toString();
    }

    private String getUpdateSetString(List<TableColumn> columnList) {//去除主键约束字段，其余字段全部更新
        StringBuffer faStringBuffer = new StringBuffer();
        for (int j = 0; j < columnList.size(); j++) {
            if (!columnList.get(j).getPrimaryKey()) {
                faStringBuffer.append(" A.\"").append(columnList.get(j).getName()).append("\"").append(" = B.\"").append(columnList.get(j).getName()).append("\"");
                if (j < columnList.size() - 1) {
                    faStringBuffer.append(",");
                }
            }

        }
        return faStringBuffer.toString();
    }

    private String getDeleteWhereString(List<TableColumn> columnList, List<Map<String, Object>> dataList) {
        List<TableColumn> newColumList = new ArrayList<>();
        for (TableColumn tableColumn : columnList) {
            if (tableColumn.getPrimaryKey()) {
                newColumList.add(tableColumn);
            }
        }
        StringBuffer faStringBuffer = new StringBuffer();
        if (newColumList.size() > 0) {
            for (int j = 0; j < newColumList.size(); j++) {
                StringBuffer stringBuffer = new StringBuffer();
                stringBuffer.append(newColumList.get(j).getName()).append(" in ");
                stringBuffer.append(" ( ");
                for (int i = 0; i < dataList.size(); i++) {
                    Object object = dataList.get(i).get(newColumList.get(j).getName());
                    stringBuffer.append("'").append(object).append("'");
                    if (i < dataList.size() - 1) {
                        stringBuffer.append(",");
                    }
                }
                stringBuffer.append(" ) ");
                faStringBuffer.append(stringBuffer);
                if (j < newColumList.size() - 1) {
                    faStringBuffer.append(" AND ");
                }
            }
        }
        return faStringBuffer.toString();
    }


    private Map<String, Object> getDataString(List<Map<String, Object>> dataList) {
        Map<String, Object> map = new HashMap<>();
        StringBuffer stringBuffer = new StringBuffer();
        List<TableColumn> list = new ArrayList<>();
        for (int i = 0; i < dataList.size(); i++) {
            StringBuffer BstringBuffer = new StringBuffer();
            BstringBuffer.append(" SELECT ");
            Iterator iterator = dataList.get(i).keySet().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next().toString();
                Object value = dataList.get(i).get(key);
                BstringBuffer.append("'").append(value).append("'").append(" AS \"").append(key).append("\",");
                if (i == 0) {
                    TableColumn tableColumn = new TableColumn();
                    tableColumn.setName(key);
                    list.add(tableColumn);
                }
            }
            String dataSql = BstringBuffer.toString();
            dataSql = dataSql.substring(0, dataSql.length() - 1);
            BstringBuffer = new StringBuffer().append(dataSql);
            if (i < dataList.size() - 1) {
                BstringBuffer.append(" UNION　ALL ");
            }
            stringBuffer.append(BstringBuffer);
        }
        map.put("cloumn", list);
        map.put("sql", stringBuffer.toString());
        return map;
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

}
