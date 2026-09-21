package com.jd.plugin.mysql.builder;

import com.jd.plugin.mysql.type.OscarColumnTypeEnum;
import com.jd.plugin.mysql.type.OscarIndexTypeEnum;
import com.jd.spi.SqlBuilder;
import com.jd.spi.jdbc.DefaultSqlBuilder;
import com.jd.spi.model.Database;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableIndex;
import cn.hutool.core.util.ArrayUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class OscarSqlBuilder extends DefaultSqlBuilder {
    @Override
    public String buildCreateTableSql(Table table) {
        StringBuilder script = new StringBuilder();
        script.append("CREATE TABLE ");
//        if (StringUtils.isNotBlank(table.getDatabaseName())) {
//            script.append(table.getDatabaseName()).append(".");
//        }

        if (StringUtils.isNotBlank(table.getSchemaName())) {
            script.append(table.getSchemaName()).append(".");
        }
        script.append(table.getName()).append(" (").append("\n");

        // append column
        for (TableColumn column : table.getColumnList()) {
            if (StringUtils.isBlank(column.getName()) || StringUtils.isBlank(column.getColumnType())) {
                continue;
            }
            OscarColumnTypeEnum typeEnum = OscarColumnTypeEnum.getByType(column.getColumnType());
            script.append("\t").append(typeEnum.buildCreateColumnSql(column)).append(",\n");
        }

        // append primary key and index
        for (TableIndex tableIndex : table.getIndexList()) {
            if (StringUtils.isBlank(tableIndex.getName()) || StringUtils.isBlank(tableIndex.getType())) {
                continue;
            }
            OscarIndexTypeEnum oscarIndexTypeEnum = OscarIndexTypeEnum.getByType(tableIndex.getType());
            script.append("\t").append("").append(oscarIndexTypeEnum.buildIndexScript(tableIndex)).append(",\n");
        }

        script = new StringBuilder(script.substring(0, script.length() - 2));
        script.append("\n);");


        if (StringUtils.isNotBlank(table.getEngine())) {
            script.append(" ENGINE=").append(table.getEngine());
        }

        if (StringUtils.isNotBlank(table.getCharset())) {
            script.append(" DEFAULT CHARACTER SET=").append(table.getCharset());
        }

        if (StringUtils.isNotBlank(table.getCollate())) {
            script.append(" COLLATE=").append(table.getCollate());
        }

        if (table.getIncrementValue() != null) {
            script.append(" AUTO_INCREMENT=").append(table.getIncrementValue());
        }



        if (StringUtils.isNotBlank(table.getPartition())) {
            script.append(" \n").append(table.getPartition());
        }
        if (StringUtils.isNotBlank(table.getComment())) {
            script.append(" \n");
            script.append("COMMENT ON TABLE ").append(table.getSchemaName()).append(".").append(table.getName()).append(" IS").append(" '").append(table.getComment()).append(" '").append(";");
        }
        for (TableColumn column : table.getColumnList()) {
           if (StringUtils.isNotBlank(column.getComment())){
               script.append("\n").append(buildComment(column)).append(";\n");           }
        }
//        script.append(";");

        return script.toString();
    }

    @Override
    public String buildModifyTaleSql(Table oldTable, Table newTable) {
        StringBuilder script = new StringBuilder();
        // append modify column
        for (TableColumn tableColumn : newTable.getColumnList()) {
            if (StringUtils.isNotBlank(tableColumn.getEditStatus()) && StringUtils.isNotBlank(tableColumn.getColumnType()) && StringUtils.isNotBlank(tableColumn.getName())) {
                OscarColumnTypeEnum typeEnum = OscarColumnTypeEnum.getByType(tableColumn.getColumnType());
                script.append("\t").append(typeEnum.buildModifyColumn(tableColumn)).append(";\n");
            }
        }
        if (!StringUtils.equalsIgnoreCase(oldTable.getComment(), newTable.getComment())) {
            script.append("\t").append("COMMENT=").append("'").append(newTable.getComment()).append("'").append(",\n");
        }
        if (oldTable.getIncrementValue() != newTable.getIncrementValue()) {
            script.append("ALTER TABLE ").append("\t").append("AUTO_INCREMENT=").append(newTable.getIncrementValue()).append(",\n");
        }



        // append modify index
        for (TableIndex tableIndex : newTable.getIndexList()) {
            if (StringUtils.isNotBlank(tableIndex.getEditStatus()) && StringUtils.isNotBlank(tableIndex.getType())) {
                OscarIndexTypeEnum oscarIndexTypeEnum = OscarIndexTypeEnum.getByType(tableIndex.getType());
                script.append("\t").append(oscarIndexTypeEnum.buildModifyIndex(tableIndex)).append(",\n");
            }
        }

        // append reorder column
        script.append(buildGenerateReorderColumnSql(oldTable, newTable));

        if (script.length() > 2) {
            script = new StringBuilder(script.substring(0, script.length() - 2));
            script.append(";");
        }

        return script.toString();
    }

    private String buildComment(TableColumn column) {
        StringBuilder script = new StringBuilder();
        script.append("COMMENT ON COLUMN ").append(column.getSchemaName()).append(".").append(column.getTableName()).append(".").append(column.getName()).append(" IS'").append(column.getComment()).append("'");
        return script.toString();
    }
    @Override
    public String pageLimit(String sql, int offset, int pageNo, int pageSize) {
        StringBuilder sqlBuilder = new StringBuilder(sql.length() + 14);
        sqlBuilder.append(sql);
        if (offset == 0) {
            sqlBuilder.append("\n LIMIT ");
            sqlBuilder.append(pageSize);
        } else {
            sqlBuilder.append("\n LIMIT ");
            sqlBuilder.append(offset);
            sqlBuilder.append(",");
            sqlBuilder.append(pageSize);
        }
        return sqlBuilder.toString();
    }


    @Override
    public String buildCreateDatabaseSql(Database database) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE DATABASE " + database.getName() + "");
        if (StringUtils.isNotBlank(database.getCharset())) {
            sqlBuilder.append(" DEFAULT CHARACTER SET=").append(database.getCharset());
        }
        if (StringUtils.isNotBlank(database.getCollation())) {
            sqlBuilder.append(" COLLATE=").append(database.getCollation());
        }
        return sqlBuilder.toString();
    }

    public String buildGenerateReorderColumnSql(Table oldTable, Table newTable) {
        StringBuilder sql = new StringBuilder();
        int n = 0;
        // Create a map to store the index of each column in the old table's column list
        Map<String, Integer> oldColumnIndexMap = new HashMap<>();
        for (int i = 0; i < oldTable.getColumnList().size(); i++) {
            oldColumnIndexMap.put(oldTable.getColumnList().get(i).getName(), i);
        }
        String[] oldColumnArray = oldTable.getColumnList().stream().map(TableColumn::getName).toArray(String[]::new);
        String[] newColumnArray = newTable.getColumnList().stream().map(TableColumn::getName).toArray(String[]::new);

        buildSql(oldColumnArray, newColumnArray, sql, oldTable, newTable, n);

        return sql.toString();
    }

    private String[] buildSql(String[] originalArray, String[] targetArray, StringBuilder sql, Table oldTable, Table newTable, int n) {
        // 先完成首位移动
        if (!originalArray[0].equals(targetArray[0])) {
            int a = findIndex(originalArray, targetArray[0]);
            if ( a != -1) {
                TableColumn column = oldTable.getColumnList().stream().filter(col -> StringUtils.equals(col.getName(), originalArray[a])).findFirst().get();
                String[] newArray = moveElement(originalArray, a, 0);
                System.out.println(ArrayUtil.toString(newArray));
                sql.append(" MODIFY COLUMN ");
                OscarColumnTypeEnum typeEnum = OscarColumnTypeEnum.getByType(column.getColumnType());
                sql.append(typeEnum.buildColumn(column));
                sql.append(" FIRST;\n");
                n++;
                if (Arrays.equals(newArray, targetArray)) {
                    return newArray;
                }
                String[] resultArray = buildSql(newArray, targetArray, sql, oldTable, newTable, n);
                if (Arrays.equals(resultArray, targetArray)) {
                    return resultArray;
                }
            }
        }

        // 在完成最后一位移动
        int max = originalArray.length - 1;
        if (!originalArray[max].equals(targetArray[max])) {
            int a = findIndex(originalArray, targetArray[max]);
            if ( a != -1) {
                TableColumn column = oldTable.getColumnList().stream().filter(col -> StringUtils.equals(col.getName(), originalArray[a])).findFirst().get();
                String[] newArray = moveElement(originalArray, a, max);
                System.out.println(ArrayUtil.toString(newArray));
                if (n > 0) {
                    sql.append("ALTER TABLE ");
                    if (StringUtils.isNotBlank(oldTable.getDatabaseName())) {
                        sql.append("").append(oldTable.getDatabaseName()).append("").append(".");
                    }
                    sql.append("").append(oldTable.getName()).append("").append("\n");
                }
                sql.append(" MODIFY COLUMN ");
                OscarColumnTypeEnum typeEnum = OscarColumnTypeEnum.getByType(column.getColumnType());
                sql.append(typeEnum.buildColumn(column));
                sql.append(" ");
                sql.append(" AFTER ");
                sql.append(oldTable.getColumnList().get(max).getName());
                sql.append(";\n");
                n++;
                if (Arrays.equals(newArray, targetArray)) {
                    return newArray;
                }
                String[] resultArray = buildSql(newArray, targetArray, sql, oldTable, newTable, n);
                if (Arrays.equals(resultArray, targetArray)) {
                    return resultArray;
                }
            }

        }


        for (int i = 0; i < originalArray.length; i++) {
            int a = findIndex(targetArray, originalArray[i]);
            if ( a != -1) {
                if (i != a && isMoveValid(originalArray, targetArray, i, a)) {
                    // oldTable.getColumnList中查找name为a
                    int finalI = i;
                    TableColumn column = oldTable.getColumnList().stream().filter(col -> StringUtils.equals(col.getName(), originalArray[finalI])).findFirst().get();
                    if (n > 0) {
                        sql.append("ALTER TABLE ");
                        if (StringUtils.isNotBlank(oldTable.getDatabaseName())) {
                            sql.append("").append(oldTable.getDatabaseName()).append("").append(".");
                        }
                        sql.append("").append(oldTable.getName()).append("").append("\n");
                    }
                    sql.append(" MODIFY COLUMN ");
                    OscarColumnTypeEnum typeEnum = OscarColumnTypeEnum.getByType(column.getColumnType());
                    sql.append(typeEnum.buildColumn(column));
                    sql.append(" ");
                    sql.append(" AFTER ");
                    if (i < a) {
                        sql.append(originalArray[a]);
                    } else {
                        sql.append(originalArray[a - 1]);
                    }

                    sql.append(";\n");
                    n++;
                    String[] newArray = moveElement(originalArray, i, a);
                    if (Arrays.equals(newArray, targetArray)) {
                        return newArray;
                    }
                    String[] resultArray = buildSql(newArray, targetArray, sql, oldTable, newTable, n);
                    if (Arrays.equals(resultArray, targetArray)) {
                        return resultArray;
                    }
                }
            }
        }
        return null;
    }

    private static int findIndex(String[] array, String element) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(element)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isMoveValid(String[] originalArray, String[] targetArray, int i, int a) {
        System.out.println("i : " + i + " a:" + a);
        return a != -1 && (i == 0 || a == 0 || !originalArray[i - 1].equals(targetArray[a - 1])) &&
                (i >= originalArray.length - 1 || a >= targetArray.length - 1 || !originalArray[i + 1].equals(targetArray[a + 1]));
    }

    private static String[] moveElement(String[] originalArray, int from, int to) {
        String[] newArray = new String[originalArray.length];
        System.arraycopy(originalArray, 0, newArray, 0, originalArray.length);
        String temp = newArray[from];
        if (from < to) {
            System.arraycopy(originalArray, from + 1, newArray, from, to - from);
        } else {
            System.arraycopy(originalArray, to, newArray, to + 1, from - to);
        }
        newArray[to] = temp;
        System.out.println(ArrayUtil.toString(newArray));
        return newArray;
    }

}
