package com.jd.plugin.oracle;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.plugin.oracle.builder.OracleSqlBuilder;
import com.jd.plugin.oracle.type.OracleColumnDefaultValueEnum;
import com.jd.plugin.oracle.type.OracleColumnTypeEnum;
import com.jd.plugin.oracle.type.OracleDefaultValueEnum;
import com.jd.plugin.oracle.type.OracleIndexTypeEnum;
import com.jd.spi.MetaData;
import com.jd.spi.SqlBuilder;
import com.jd.spi.jdbc.DefaultMetaService;
import com.jd.spi.jdbc.DefaultValueHandler;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class OracleMetaData extends DefaultMetaService implements MetaData {

    private static final String TABLE_DDL_SQL = "select dbms_metadata.get_ddl('TABLE','%s','%s') as sql from dual";
    private static final int RELATION_QUERY_TIMEOUT_SECONDS = 15;

    private List<String> systemSchemas = Arrays.asList("ANONYMOUS", "APEX_030200", "APEX_PUBLIC_USER", "APPQOSSYS", "BI", "CTXSYS", "DBSNMP", "DIP", "EXFSYS", "FLOWS_FILES", "HR", "IX", "MDDATA", "MDSYS", "MGMT_VIEW", "OE", "OLAPSYS", "ORACLE_OCM", "ORDDATA", "ORDPLUGINS", "ORDSYS", "OUTLN", "OWBSYS", "OWBSYS_AUDIT", "PM", "SCOTT", "SH", "SI_INFORMTN_SCHEMA", "SPATIAL_CSW_ADMIN_USR", "SPATIAL_WFS_ADMIN_USR", "SYS", "SYSMAN", "SYSTEM", "WMSYS", "XDB", "XS$NULL");


    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        List<Schema> schemas = SQLExecutor.getInstance().schemas(connection, databaseName, null);
        return SortUtils.sortSchema(schemas, systemSchemas);
    }

    private String format(String tableName) {
        return "\"" + tableName + "\"";
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
    public String tableDDL(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(TABLE_DDL_SQL, tableName, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            try {
                if (resultSet.next()) {
                    return resultSet.getString("sql");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            return null;
        });
    }

    private static String SELECT_TABLE_SQL = "SELECT A.OWNER, A.TABLE_NAME, B.COMMENTS " +
            "FROM ALL_TABLES A LEFT JOIN ALL_TAB_COMMENTS B ON  A.OWNER = B.OWNER  AND A.TABLE_NAME = B.TABLE_NAME\n" +
            "where A.OWNER = '%s' ";

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TABLE_SQL, schemaName);
        if (StringUtils.isNotBlank(tableName)) {
            sql = sql + " and A.TABLE_NAME = '" + tableName + "'";
        }
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            List<Table> tables = new ArrayList<>();
            while (resultSet.next()) {
                Table table = new Table();
                table.setDatabaseName(databaseName);
                table.setSchemaName(schemaName);
                table.setName(resultSet.getString("TABLE_NAME"));
                table.setComment(resultSet.getString("COMMENTS"));
                tables.add(table);
            }
            return tables;
        });
    }

    private static String CONSTRAINTS_SQL = "SELECT\n" +
            "\ta.CONSTRAINT_NAME,\n" +
            "\ta.TABLE_NAME,\n" +
            "\ta.COLUMN_NAME,\n" +
            "\tb.STATUS,\n" +
            "\tb.CONSTRAINT_TYPE,\n" +
            "\tb.OWNER \n" +
            "FROM\n" +
            "\tALL_CONS_COLUMNS a\n" +
            "\tJOIN ALL_CONSTRAINTS b ON a.CONSTRAINT_NAME = b.CONSTRAINT_NAME \n" +
            "WHERE\n" +
            "\ta.OWNER = '%s' \n" +
            "\tAND a.TABLE_NAME = '%s'";

    @Override
    public List<TableIndex> getConstraintsData(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(CONSTRAINTS_SQL, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            LinkedHashMap<String, TableIndex> map = new LinkedHashMap();
            while (resultSet.next()) {
                String keyName = resultSet.getString("CONSTRAINT_NAME");
                TableIndex tableIndex = map.get(keyName);
                if (tableIndex != null) {
                    List<TableIndexColumn> columnList = tableIndex.getColumnList();
                    columnList.add(getTableConsIndexColumn(resultSet));
                    tableIndex.setColumnList(columnList);
                } else {
                    TableIndex index = new TableIndex();
                    index.setDatabaseName(databaseName);
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(keyName);
                    index.setColumn(resultSet.getString("COLUMN_NAME"));
                    index.setStatus("ENABLED".equals(resultSet.getString("STATUS")) ? "VALID": "INVALID");
                    index.setType(resultSet.getString("CONSTRAINT_TYPE"));
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableConsIndexColumn(resultSet));
                    index.setColumnList(tableIndexColumns);
                    if ("U".equalsIgnoreCase(index.getType())) {
                        index.setType(OracleIndexTypeEnum.UNIQUE.getName());
                    } else if ("C".equalsIgnoreCase(index.getType())) {
                        index.setType(OracleIndexTypeEnum.NOT_NULL.getName());
                    } else if ("P".equalsIgnoreCase(index.getType())) {
                        index.setType(OracleIndexTypeEnum.PRIMARY_KEY.getName());
                    } else if ("R".equalsIgnoreCase(index.getType())) {
                        index.setType(OracleIndexTypeEnum.VIRTUAL.getName());
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
        });
    }


    private static String getObjectRule = "SELECT GRANTEE,OWNER,TABLE_NAME,GRANTOR,PRIVILEGE,GRANTABLE FROM DBA_TAB_PRIVS WHERE GRANTEE = '%s' AND TABLE_NAME = '%s' AND OWNER = '%s'";


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




    private static String SELECT_TAB_COLS = "SELECT atc.column_id , atc.column_name as COLUMN_NAME, atc.data_type as DATA_TYPE , atc.data_length as DATA_LENGTH , atc.data_type_mod , atc.nullable ,  atc.data_default as DATA_DEFAULT,  acc.comments ,  atc.DATA_PRECISION ,  atc.DATA_SCALE , atc.CHAR_USED  FROM  all_tab_columns atc, all_col_comments acc WHERE atc.owner = acc.owner AND atc.table_name = acc.table_name AND atc.column_name = acc.column_name AND atc.owner = '%s'  AND atc.table_name = '%s'  order by atc.column_id";

    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TAB_COLS, schemaName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
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
                tableColumn.setName(resultSet.getString("COLUMN_NAME"));
                tableColumn.setColumnType(resultSet.getString("DATA_TYPE"));
                Integer dataPrecision = resultSet.getInt("DATA_PRECISION");
                if (resultSet.getString("DATA_PRECISION") != null) {
                    tableColumn.setColumnSize(dataPrecision);
                } else {
                    tableColumn.setColumnSize(resultSet.getInt("DATA_LENGTH"));
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
                tableColumns.add(tableColumn);
            }
            return tableColumns;
        });
    }

    private static String ROUTINES_SQL
            = "SELECT LINE, TEXT "
            + "FROM ALL_SOURCE "
            + "WHERE TYPE = '%s' AND NAME = '%s' and OWNER  = '%s'"
            + "ORDER BY LINE";

    private static final String FUNCTIONS_SQL
            = "SELECT OWNER, OBJECT_NAME FROM ALL_OBJECTS WHERE OBJECT_TYPE = 'FUNCTION'";

    static String buildFunctionsSql(String schemaName) {
        String sql = FUNCTIONS_SQL;
        if (StringUtils.isNotBlank(schemaName)) {
            sql += " AND OWNER = '" + schemaName.replace("'", "''") + "'";
        }
        return sql + " ORDER BY OWNER, OBJECT_NAME";
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

        String sql = String.format(ROUTINES_SQL, "FUNCTION", functionName, schemaName);
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

    private static String TRIGGER_SQL_LIST
            = "SELECT TRIGGER_NAME "
            + "FROM ALL_TRIGGERS WHERE OWNER = '%s' AND TABLE_NAME = '%s'";

//    private static String SELECT_PK_SQL = "select  acc.CONSTRAINT_NAME from  all_cons_columns acc,  all_constraints ac  where  acc.constraint_name = ac.constraint_name  and acc.owner = ac.owner  and acc.owner = '%s'  and ac.constraint_type = 'P'  and ac.table_name = '%s' ";
//
//    private static String SELECT_TABLE_INDEX = "SELECT ai.index_name AS Key_name, ai.STATUS, aic.column_name AS Column_name, ai.index_type AS Index_type, ai.uniqueness AS Unique_name, aic.COLUMN_POSITION as Seq_in_index, aic.descend AS Collation, ex.COLUMN_EXPRESSION as COLUMN_EXPRESSION FROM all_ind_columns aic JOIN all_indexes ai ON aic.table_owner = ai.table_owner and aic.table_name = ai.table_name and aic.index_name = ai.index_name LEFT JOIN ALL_IND_EXPRESSIONS ex ON aic.table_owner = ex.table_owner and aic.table_name = ex.table_name and aic.index_name = ex.index_name where ai.table_owner = '%s' AND ai.table_name = '%s' ";
//
//
//    @Override
//    public List<TableIndex> indexes(Connection connection, String databaseName, String schemaName, String tableName) {
//        String pkSql = String.format(SELECT_PK_SQL, schemaName, tableName);
//        Set<String> pkSet = new HashSet<>();
//        SQLExecutor.getInstance().execute(connection, pkSql, resultSet -> {
//                    while (resultSet.next()) {
//                        pkSet.add(resultSet.getString("CONSTRAINT_NAME"));
//                    }
//                    return null;
//                }
//        );
//
//        String sql = String.format(SELECT_TABLE_INDEX, schemaName, tableName);
//        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
//            LinkedHashMap<String, TableIndex> map = new LinkedHashMap();
//            while (resultSet.next()) {
//                String keyName = resultSet.getString("Key_name");
//                TableIndex tableIndex = map.get(keyName);
//                if (tableIndex != null) {
//                    List<TableIndexColumn> columnList = tableIndex.getColumnList();
//                    columnList.add(getTableIndexColumn(resultSet));
//                    columnList = columnList.stream().sorted(Comparator.comparing(TableIndexColumn::getOrdinalPosition))
//                            .collect(Collectors.toList());
//                    tableIndex.setColumnList(columnList);
//                } else {
//                    TableIndex index = new TableIndex();
//                    index.setDatabaseName(databaseName);
//                    index.setSchemaName(schemaName);
//                    index.setTableName(tableName);
//                    index.setName(keyName);
//                    index.setKeyName(keyName);
//                    index.setStatus(getConStatus(connection, tableName, schemaName, keyName));
//                    index.setUnique("unique".equalsIgnoreCase(resultSet.getString("Unique_name")));
//                    index.setType(resultSet.getString("Index_type"));
//                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
//                    tableIndexColumns.add(getTableIndexColumn(resultSet));
//                    index.setColumnList(tableIndexColumns);
//                    if (index.getUnique()) {
//                        index.setType(OracleIndexTypeEnum.UNIQUE.getName());
//                    } else if ("NORMAL".equalsIgnoreCase(index.getType())) {
//                        index.setType(OracleIndexTypeEnum.NORMAL.getName());
//                    } else if ("BITMAP".equalsIgnoreCase(index.getType())) {
//                        index.setType(OracleIndexTypeEnum.BITMAP.getName());
//                    } else if (StringUtils.isNotBlank(index.getType()) && index.getType().toUpperCase().contains("NORMAL")) {
//                        index.setType(OracleIndexTypeEnum.NORMAL.getName());
//                    } else if ("VIRTUAL".equalsIgnoreCase(index.getType())) {
//                        // 外键
//                        index.setType(OracleIndexTypeEnum.VIRTUAL.getName());
//                    }
//                    if (pkSet.contains(keyName)) {
//                        index.setType(OracleIndexTypeEnum.PRIMARY_KEY.getName());
//                    }
//                    map.put(keyName, index);
//                }
//            }
//            return map.values().stream().collect(Collectors.toList());
//        });
//
//    }


    private static String INDEX_SQL = "SELECT i.TABLE_NAME, i.STATUS, i.INDEX_TYPE, i.INDEX_NAME, i.UNIQUENESS ,c.COLUMN_NAME, c.COLUMN_POSITION, c.DESCEND, cons.CONSTRAINT_TYPE, cons.CONSTRAINT_NAME FROM ALL_INDEXES i LEFT JOIN ALL_IND_COLUMNS c ON i.INDEX_NAME = c.INDEX_NAME AND i.TABLE_NAME = c.TABLE_NAME AND i.TABLE_OWNER = c.TABLE_OWNER LEFT JOIN ALL_CONSTRAINTS cons ON i.INDEX_NAME = cons.INDEX_NAME AND i.TABLE_NAME = cons.TABLE_NAME AND i.TABLE_OWNER = cons.OWNER WHERE i.TABLE_OWNER = '%s' AND i.TABLE_NAME = '%s' ORDER BY i.INDEX_NAME, c.COLUMN_POSITION";

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
                        index.setType(OracleIndexTypeEnum.PRIMARY_KEY.getName());
                    } else if (index.getUnique()) {
                        index.setType(OracleIndexTypeEnum.UNIQUE.getName());
                    } else if ("BITMAP".equalsIgnoreCase(resultSet.getString("INDEX_TYPE"))) {
                        index.setType(OracleIndexTypeEnum.BITMAP.getName());
                    } else if ("VIRTUAL".equalsIgnoreCase(resultSet.getString("INDEX_TYPE"))) {
                        // 外键
                        String columnName = getConstraintsIndex(Chat2DBContext.getConnection(), schemaName, tableName, resultSet.getString("COLUMN_NAME"));
                        index.setKeyName(columnName);
                        index.setType(OracleIndexTypeEnum.VIRTUAL.getName());
                    } else {
                        index.setType(OracleIndexTypeEnum.NORMAL.getName());
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
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


    private static String getConStatus = "SELECT\n" +
            "\ta.CONSTRAINT_NAME,\n" +
            "\ta.TABLE_NAME,\n" +
            "\ta.COLUMN_NAME,\n" +
            "\tb.STATUS,\n" +
            "\tb.CONSTRAINT_TYPE,\n" +
            "\tb.OWNER \n" +
            "FROM\n" +
            "\tALL_CONS_COLUMNS a\n" +
            "\tJOIN ALL_CONSTRAINTS b ON a.CONSTRAINT_NAME = b.CONSTRAINT_NAME \n" +
            "WHERE\n" +
            "\ta.OWNER = '%s' \n" +
            "\tAND a.TABLE_NAME = '%s' AND a.CONSTRAINT_NAME = '%s'";


    /**
     * 获取键状态
     * @param connection
     * @param schemaName
     * @param tableName
     * @return
     */
    public String getConStatus(Connection connection, String tableName, String schemaName, String conName) {
        if (StrUtil.isBlank(conName) || "NULL".equalsIgnoreCase(conName)) {
            return "VALID";
        }
        String sql = String.format(getConStatus, schemaName, tableName, conName);
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
     *
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

//    private static String SQL_UN_FOREIGN_KEY_SQL = "select \n" +
//            "a.owner as \"schema\",a.table_name as \"table\",\n" +
//            "a.constraint_name as \"constraintName\",\n" +
//            "a.column_name as \"tableColumn\",\n" +
//            "b.table_name as \"forTableName\",\n" +
//            "b.owner as \"forSchema\",\n" +
//            "b.column_name as \"forTableColumn\", c.status\n" +
//            "from all_cons_columns a join all_constraints c on a.constraint_name  = c.constraint_name\n" +
//            "join all_cons_columns b on c.r_constraint_name = b.constraint_name \n" +
//            "where c.constraint_type = 'R' \n" +
//            "and a.owner = '%s' and and b.table_name = '%s'\n" +
//            "order by a.table_name, a.constraint_name, a.column_name";


    /**
     * 获取被引用信息
     * @param connection
     * @param schemaName
     * @param tableName
     * @return
     */
    @Override
    public List<ForeignData> getUnForeignKey(Connection connection, String schemaName, String tableName) {
        List<ForeignData> foreignKeys = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(SQL_FOREIGN_KEY_SQL, schemaName, tableName), RELATION_QUERY_TIMEOUT_SECONDS, resultSet -> {
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


    private TableIndexColumn getTableIndexColumn(ResultSet resultSet) throws SQLException {
        TableIndexColumn tableIndexColumn = new TableIndexColumn();
        tableIndexColumn.setColumnName(resultSet.getString("COLUMN_NAME"));
        tableIndexColumn.setOrdinalPosition(resultSet.getShort("COLUMN_POSITION"));
        String collation = resultSet.getString("DESCEND");
        if ("ASC".equalsIgnoreCase(collation)) {
            tableIndexColumn.setAscOrDesc("ASC");
        } else if ("DESC".equalsIgnoreCase(collation)) {
            tableIndexColumn.setAscOrDesc("DESC");
        }
        return tableIndexColumn;
    }


    private TableIndexColumn getTableConsIndexColumn(ResultSet resultSet) throws SQLException {
        TableIndexColumn tableIndexColumn = new TableIndexColumn();
        tableIndexColumn.setColumnName(resultSet.getString("COLUMN_NAME"));
        return tableIndexColumn;
    }

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName, String tableName) {
        List<Trigger> triggers = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(TRIGGER_SQL_LIST, schemaName, tableName),
                resultSet -> {
                    while (resultSet.next()) {
                        Trigger trigger = new Trigger();
                        trigger.setTriggerName(resultSet.getString("TRIGGER_NAME"));
                        trigger.setSchemaName(schemaName);
                        trigger.setDatabaseName(databaseName);
                        triggers.add(trigger);
                    }
                    return triggers;
                });
    }


    @Override
    public String columnDefault(Connection connection, String name) {
        String defaultValue = Objects.requireNonNull(OracleColumnDefaultValueEnum.getByName(name)).getDefaultValue();
        return StringUtils.isNotBlank(defaultValue) ? defaultValue : "";
    }

    private static String getCheckSQl = "SELECT B.CONSTRAINT_NAME, B.SEARCH_CONDITION, B.STATUS,A.COLUMN_NAME FROM ALL_CONSTRAINTS B JOIN ALL_CONS_COLUMNS A ON A.CONSTRAINT_NAME = B.CONSTRAINT_NAME  " +
            "WHERE B.OWNER = '%s' AND B.TABLE_NAME = '%s' AND B.CONSTRAINT_TYPE = 'C'";

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
                    index.setColumn(resultSet.getString("COLUMN_NAME"));
                    index.setName(resultSet.getString("CONSTRAINT_NAME"));
                    index.setStatus("ENABLED".equals(resultSet.getString("STATUS")) ? "VALID": "INVALID");
                    name.add(index);
                }
            }
            return name;
        });
    }

    @Override
    public Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName,
                           String triggerName) {

        String sql = String.format(ROUTINES_SQL, "TRIGGER", triggerName, schemaName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            StringBuilder sb = new StringBuilder();
            sb.append(" CREATE OR REPLACE ");
            while (resultSet.next()) {
                sb.append(resultSet.getString("TEXT") + "\n");
            }
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            trigger.setTriggerBody(sb.toString());
            return trigger;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, "PROCEDURE", procedureName, schemaName);
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

    private static String VIEW_SQL
            = "SELECT VIEW_NAME, TEXT FROM ALL_VIEWS WHERE OWNER = '%s' AND VIEW_NAME = '%s'";

    @Override
    public Table view(Connection connection, String databaseName, String schemaName, String viewName) {
        String sql = String.format(VIEW_SQL, schemaName, viewName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Table table = new Table();
            table.setDatabaseName(databaseName);
            table.setSchemaName(schemaName);
            table.setName(viewName);
            if (resultSet.next()) {
                table.setViewSql(resultSet.getString("TEXT"));
                String str = "CREATE OR REPLACE VIEW " + format(schemaName) + "." + format(viewName) + " AS \n\t" + table.getViewSql() + ";\n";
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


    private static String TABLE_USERS_SQL_LIST = "SELECT USER_ID, AUTHENTICATION_TYPE, INITIAL_RSRC_CONSUMER_GROUP, \n" +
            "LOCK_DATE, PROFILE, USERNAME,ACCOUNT_STATUS,DEFAULT_TABLESPACE,TEMPORARY_TABLESPACE,CREATED \n" +
            "FROM dba_users";

    @Override
    public List<Table> tableUsers(Connection connection, String dataBaseName) {
        List<Table> tableSpaces = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, TABLE_USERS_SQL_LIST,
                resultSet -> {
                    while (resultSet.next()) {
                        Table tableUser = new Table();
                        TableDetails tableDetails = new TableDetails();
                        tableDetails.setName(resultSet.getString("USERNAME"));
                        tableDetails.setAccountStatus(resultSet.getString("ACCOUNT_STATUS"));
                        tableDetails.setDefaultTableSpace(resultSet.getString("DEFAULT_TABLESPACE"));
                        tableDetails.setTempTableSpace(resultSet.getString("TEMPORARY_TABLESPACE"));
                        tableDetails.setCreated(resultSet.getString("CREATED"));
                        tableDetails.setDataBaseName(dataBaseName);
                        tableUser.setName(resultSet.getString("USERNAME"));
                        tableUser.setTableDetails(tableDetails);
                        tableSpaces.add(tableUser);
                    }
                    return tableSpaces;
                });
    }

    @Override
    public Table tableUser(Connection connection, String databaseName, String userName) {
        StringBuilder stringBuilder = new StringBuilder(TABLE_USERS_SQL_LIST);
        stringBuilder.append(" WHERE USERNAME = '%s'");
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


//    /**
//     * 表空间文件路径列表
//     */
//    private static String TABLE_SPACE_FILE_SQL_LIST = "SELECT * FROM ( select b.file_id, b.tablespace_name as name, b.file_name as path, rownum as c \n" +
//            "from dba_free_space a, dba_data_files b \n" +
//            "where a.tablespace_name = b.tablespace_name )";
//
//    private static String TABLE_PAGE = " where c between '%s' and '%s'";
//
//    @Override
//    public List<TableDetails> tableSpacesFile(Connection connection, Integer page, Integer size) {
//        List<TableDetails> tableSpaces = new ArrayList<>();
//        StringBuilder stringBuilder = new StringBuilder(TABLE_SPACE_FILE_SQL_LIST);
//        stringBuilder.append(SPACE_SPLIT_SQL);
//        return SQLExecutor.getInstance().execute(connection, SPACE_SPLIT_SQL + TABLE_PAGE, resultSet -> {
//            while (resultSet.next()) {
//                TableDetails tableDetails = new TableDetails();
//                tableDetails.setPath(resultSet.getString("path"));
//                tableDetails.setName(resultSet.getString("name"));
//                tableSpaces.add(tableDetails);
//            }
//            return tableSpaces;
//        });
//    }
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



    private static String TABLE_SPACE_SQL_LIST = "select b.file_id, b.tablespace_name as name, b.file_name as path, b.bytes / (1024 * 1024 * 1024) as totalSize, \n" +
            "(b.bytes - sum(nvl(a.bytes, 0))) / (1024 * 1024 * 1024) as useSize, sum(nvl(a.bytes, 0)) / (1024 * 1024 * 1024) as freeSize, \n" +
            "sum(nvl(a.bytes, 0)) / (b.bytes) * 100 as usageRate, b.autoextensible, b.maxbytes, b.online_status as status,b.increment_by as autoSize \n" +
            "from dba_free_space a, dba_data_files b\n" +
            "where a.file_id = b.file_id";


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
            tableDetails.setStatus(resultSet.getString("status"));
            String totalSize = new BigDecimal(resultSet.getString("totalSize")).setScale(2, RoundingMode.HALF_UP).toString();
            BigDecimal bigDecimal = new BigDecimal(totalSize);
            tableDetails.setTotalSize(bigDecimal.divide(new BigDecimal("1024"), RoundingMode.HALF_UP).toString());
            tableDetails.setFreeSize(new BigDecimal(resultSet.getString("freeSize")).setScale(2, RoundingMode.HALF_UP).toString());
            tableDetails.setUseSize(new BigDecimal(resultSet.getString("useSize")).setScale(2, RoundingMode.HALF_UP).toString());
            tableDetails.setUsageRate(new BigDecimal("100").subtract(new BigDecimal(resultSet.getString("usageRate")).setScale(2, RoundingMode.HALF_UP)).toString());
            tableDetails.setAutoScaling(resultSet.getString("autoextensible"));
            tableDetails.setAutoSize(resultSet.getString("autoSize"));

            tableDetails.setExpandUpperLimit(new BigDecimal(resultSet.getString("maxbytes")).setScale(0, RoundingMode.HALF_UP).toString());
            if (StringUtils.isNotBlank(tableDetails.getExpandUpperLimit())) {
                tableDetails.setCheck(Boolean.FALSE);
            }
            if ("YES".equals(tableDetails.getAutoScaling())) {
                tableDetails.setAutoCheck(Boolean.TRUE);
            }
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


    private static String SELECT_TABLE_DETAILS_SQL = "select\n" +
            "        DISTINCT t.table_name     ,\n" +
            "        t.owner          ,\n" +
            "        t.tablespace_name,\n" +
            "        t.last_analyzed  ,\n" +
            "        t.num_rows       \n" +
            "from\n" +
            "        all_tables t\n" +
            "where t.owner = '%s' order by t.table_name ASC";


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

    //    private static String SELECT_USER_ROLE_SQL = "select USERNAME, GRANTED_ROLE, ADMIN_OPTION, DEFAULT_ROLE, OS_GRANTED from USER_ROLE_PRIVS";
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
                tableUserRole.setIsAdmin(resultSet.getString("ADMIN_OPTION"));
                tableUserRole.setIsDefault(resultSet.getString("DEFAULT_ROLE"));
                tableUserRoles.add(tableUserRole);
            }
            return tableUserRoles;
        });
    }

    private static String SELECT_NOT_NULL_SQL = "SELECT OWNER, TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM DBA_TAB_COLUMNS WHERE NULLABLE = 'Y' AND OWNER = '%s' AND TABLE_NAME = '%s'";


    @Override
    public List<TableIndex> notNullConstraints(Connection connection, String databaseName, String schemaName, String tableName) {
        List<TableIndex> tableIndices = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, String.format(SELECT_NOT_NULL_SQL, schemaName, tableName), resultSet -> {
            while (resultSet.next()) {
                TableIndex tableIndex = new TableIndex();
                tableIndex.setSchemaName(resultSet.getString("OWNER"));
                tableIndex.setTableName(resultSet.getString("TABLE_NAME"));
                tableIndex.setColumn(resultSet.getString("COLUMN_NAME"));
                tableIndex.setType(resultSet.getString("DATA_TYPE"));
                tableIndex.setIsN(Boolean.TRUE);
                tableIndices.add(tableIndex);
            }
            return tableIndices;
        });
    }


    public static String QUERY_ROLES_SQL = "SELECT GRANTEE, PRIVILEGE, GRANTABLE, GRANTOR, OWNER, TABLE_NAME FROM DBA_TAB_PRIVS \n" +
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

    @Override
    public String getCount(Connection connection, String tableName, String schemaName) {
        if (systemSchemas.contains(schemaName)) {
            return "";
        }
        return SQLExecutor.getInstance().execute(connection, String.format("SELECT COUNT(*) as COUNT FROM %s  ", schemaName + "." + tableName).replace("'", ""), resultSet -> {
            String count = "";
            while (resultSet.next()) {
                count = resultSet.getString("COUNT");
            }
            return count;
        });
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
        try {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("ALTER TABLESPACE \"%s\" OFFLINE");
            SQLExecutor.getInstance().execute(connection, String.format(stringBuilder.toString(), spaceName));
            StringBuilder sql = new StringBuilder();
            sql.append("DROP TABLESPACE \"%s\" INCLUDING CONTENTS AND DATAFILES CASCADE CONSTRAINTS");
            SQLExecutor.getInstance().execute(connection, String.format(sql.toString(), spaceName));
        } catch (SQLException e) {
            throw new BusinessException("删除表空间信息失败" + e.getMessage());
        }
    }

    @Override
    public SqlBuilder getSqlBuilder() {
        return new OracleSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(OracleColumnTypeEnum.getTypes())
                .charsets(Lists.newArrayList())
                .collations(Lists.newArrayList())
                .indexTypes(OracleIndexTypeEnum.getIndexTypes())
                .defaultValues(OracleDefaultValueEnum.getDefaultValues())
                .build();
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name -> "\"" + name + "\"").collect(Collectors.joining("."));
    }

    private static final String REFERENCED_FOREIGN_KEY_SQL =
            "WITH target_keys AS (" +
                    "SELECT /*+ MATERIALIZE */ OWNER, CONSTRAINT_NAME, TABLE_NAME " +
                    "FROM ALL_CONSTRAINTS " +
                    "WHERE OWNER = '%s' AND TABLE_NAME = '%s' AND CONSTRAINT_TYPE IN ('P', 'U')" +
                    ") " +
                    "SELECT /*+ LEADING(pk) USE_NL(fk fk_col pk_col) */ " +
                    "fk.CONSTRAINT_NAME AS \"CONSTRAINT_NAME\", " +
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

    @Override
    public List<String> getSystemSchemas() {
        return systemSchemas;
    }

    @Override
    public DmpCommand importDmp(ConnectInfo connectInfo, List<String> tableName, String fromUser, String toUser, String schemaName, File file, File logFileName, String importDmp, List<String> toUserList, Boolean isWin, String dmpUser, String dmpPassword) {
        //获取主机名
        String host = connectInfo.getUrl().split("@")[1];
        String credential = dmpUser + "/" + dmpPassword + "@" + host;
        List<String> arguments = new ArrayList<>();
        MetaData metaData = Chat2DBContext.getMetaData();
        if (CollectionUtil.isNotEmpty(tableName) && StringUtils.isNotBlank(fromUser) && CollUtil.isEmpty(toUserList)) {
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
            arguments.add("tables=" + tables);
        }
        if (StringUtils.isNotBlank(fromUser) && StringUtils.isNotBlank(toUser)) {
            arguments.clear();
            arguments.add("file=" + file);
            arguments.add("IGNORE=Y");
            arguments.add("log=" + logFileName);
            arguments.add("fromuser=" + fromUser);
            arguments.add("touser=" + toUser);
        } else {
            if (CollUtil.isNotEmpty(toUserList)) {
                arguments.clear();
                arguments.add("file=" + file);
                arguments.add("log=" + logFileName);
                arguments.add("SCHEMAS=" + String.join(",", toUserList));
            }
        }
        return new DmpCommand(importDmp, credential, arguments);
    }

    @Override
    public DmpCommand exportDmp(ConnectInfo connectInfo, List<String> tableName, String schemaName, File logFileName, File dmpFileName, String exportDmp,Boolean isWin, String dmpUser, String dmpPassword) {
        //获取主机名
        String host = connectInfo.getUrl().split("@")[1];
        String credential = dmpUser + "/" + dmpPassword + "@" + host;
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
            arguments.add("file=" + dmpFileName);
            arguments.add("log=" + logFileName);
            arguments.add("tables=(" + tables + ")");
        }
        if (StrUtil.isNotEmpty(schemaName) && CollectionUtil.isEmpty(tableName)) {
            arguments.add("file=" + dmpFileName);
            arguments.add("log=" + logFileName);
            arguments.add("OWNER=" + metaData.getMetaDataName(schemaName));
        }

        if (StrUtil.isEmpty(schemaName) && CollectionUtil.isEmpty(tableName)) {
            arguments.add("file=" + dmpFileName);
            arguments.add("FULL=Y");
            arguments.add("log=" + logFileName);
        }

        return new DmpCommand(exportDmp, credential, arguments);
    }
}
