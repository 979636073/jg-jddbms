package com.jd.plugin.mysql;

import cn.hutool.core.util.StrUtil;
import com.jd.plugin.mysql.builder.OscarSqlBuilder;
import com.jd.plugin.mysql.type.*;
import com.jd.spi.MetaData;
import com.jd.spi.SqlBuilder;
import com.jd.spi.ValueHandler;
import com.jd.spi.jdbc.DefaultMetaService;
import com.jd.spi.model.*;
import com.jd.spi.sql.SQLExecutor;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotEmpty;
import java.io.Reader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static com.jd.spi.util.SortUtils.sortDatabase;

public class OscarMetaData extends DefaultMetaService implements MetaData {

    private List<String> systemDatabases = Arrays.asList("information_schema", "performance_schema", "oscar", "sys");
    @Override
    public List<Database> databases(Connection connection) {
        List<Database> databases = SQLExecutor.getInstance().databases(connection);
        return sortDatabase(databases,systemDatabases,connection);
    }


    @Override
    public String tableDDL(Connection connection, @NotEmpty String databaseName, String schemaName,
                           @NotEmpty String tableName) {
        String sql1="SELECT DISTINCT a.attnum,\n" +
                "       a.attname AS field,\n" +
                "       t.typname AS type,\n" +
                "       a.attlen AS length,\n" +
                "       a.atttypmod AS lengthvar,\n" +
                "       a.attnotnull AS notnull,\n" +
                "       b.description AS comment\n" +
                "  FROM sys_class c,\n" +
                "       sys_attribute a\n" +
                "       LEFT OUTER JOIN sys_description b ON a.attrelid=b.objoid AND a.attnum = b.objsubid,\n" +
                "       sys_type t\n" +
                " WHERE c.relname = '%s'  \n" +
                "       and a.attnum > 0\n" +
                "       and a.attrelid = c.oid\n" +
                "       and a.atttypid = t.oid\n" +
                " ORDER BY a.attnum";
        String sql = " SELECT (SELECT comments FROM user_tab_comments WHERE table_name = '%s') AS comments, (SELECT TABLE_SCHEM.get_ddl('TABLE', '%s', '%s') FROM dual) AS ddl FROM dual;";
        String selectObjectDDLSQL = String.format(sql, tableName);
        return SQLExecutor.getInstance().execute(connection, selectObjectDDLSQL, resultSet -> {
            try {
                if (resultSet.next()) {
                    String ddl = resultSet.getString("ddl");
                    String comment = resultSet.getString("comments");
                    if (StringUtils.isNotBlank(comment)) {
                        return ddl +"\n"+ "COMMENT ON TABLE " + format(schemaName) + "." + format(tableName) +
                                " IS " + "'" + comment + "';";
                    }
                    return ddl;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Override
    public String showViewSql(String databaseName, String schemaName, String viewName, String viewSql, List<TableColumn> columnList) {
        String str = "CREATE OR REPLACE VIEW " + schemaName + "." + viewName + " AS \n\t" + viewSql + ";\n";
        for (TableColumn column : columnList) {
            if (StrUtil.isNotEmpty(column.getComment())) {
                str += "\n\tCOMMENT ON COLUMN " + schemaName + "." + viewName + "." + column.getName() + " IS " + column.getComment() + ";";
            }
        }
        return str;
    }
    public static String format(String tableName) {
        return  tableName ;
    }

    private static String ROUTINES_SQL
            =
            "SELECT SPECIFIC_NAME, ROUTINE_COMMENT, ROUTINE_DEFINITION FROM information_schema.routines WHERE "
                    + "routine_type = '%s' AND ROUTINE_SCHEMA ='%s'  AND "
                    + "routine_name = '%s';";

    @Override
    public Function function(Connection connection, @NotEmpty String databaseName, String schemaName,
                             String functionName) {

        String sql = String.format(ROUTINES_SQL, "FUNCTION", databaseName, functionName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Function function = new Function();
            function.setDatabaseName(databaseName);
            function.setSchemaName(schemaName);
            function.setFunctionName(functionName);
            if (resultSet.next()) {
                function.setSpecificName(resultSet.getString("SPECIFIC_NAME"));
                function.setRemarks(resultSet.getString("ROUTINE_COMMENT"));
                function.setFunctionBody(resultSet.getString("ROUTINE_DEFINITION"));
            }
            return function;
        });

    }

    private static String TRIGGER_SQL
            = "SELECT TRIGGER_NAME,EVENT_MANIPULATION, ACTION_STATEMENT  FROM INFORMATION_SCHEMA.TRIGGERS where "
            + "TRIGGER_SCHEMA = '%s' AND TRIGGER_NAME = '%s';";

    private static String TRIGGER_SQL_LIST
            = "SELECT TRIGGER_NAME FROM INFORMATION_SCHEMA.TRIGGERS where TRIGGER_SCHEMA = '%s' AND TABLE_NAME = '%s';";

    @Override
    public List<Trigger> triggers(Connection connection, String databaseName, String schemaName, String tableName) {
        List<Trigger> triggers = new ArrayList<>();
        String sql = String.format(TRIGGER_SQL_LIST, databaseName, tableName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
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
    public Trigger trigger(Connection connection, @NotEmpty String databaseName, String schemaName,
                           String triggerName) {

        String sql = String.format(TRIGGER_SQL, databaseName, triggerName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Trigger trigger = new Trigger();
            trigger.setDatabaseName(databaseName);
            trigger.setSchemaName(schemaName);
            trigger.setTriggerName(triggerName);
            if (resultSet.next()) {
                trigger.setTriggerBody(resultSet.getString("ACTION_STATEMENT"));
            }
            return trigger;
        });
    }

    @Override
    public Procedure procedure(Connection connection, @NotEmpty String databaseName, String schemaName,
                               String procedureName) {
        String sql = String.format(ROUTINES_SQL, "PROCEDURE", databaseName, procedureName);
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Procedure procedure = new Procedure();
            procedure.setDatabaseName(databaseName);
            procedure.setSchemaName(schemaName);
            procedure.setProcedureName(procedureName);
            if (resultSet.next()) {
                procedure.setSpecificName(resultSet.getString("SPECIFIC_NAME"));
                procedure.setRemarks(resultSet.getString("ROUTINE_COMMENT"));
                procedure.setProcedureBody(resultSet.getString("ROUTINE_DEFINITION"));
            }
            return procedure;
        });
    }

    private static String SELECT_TABLE_COLUMNS = " SELECT A.*,B.* FROM\n" +
            "    V_SYS_COLUMNS A, V_SYS_TYPE_INFO\n" +
            "    B WHERE\n" +
            "    A.DATA_TYPE =\n" +
            "    B.DATA_TYPE AND\n" +
            "    TABLE_NAME LIKE'%s'\n" +
            "    AND A.ORDINAL_POSITION >0\n" +
            "    AND A.\n" +
            "    TABLE_SCHEM LIKE '%s'\n" +
            "    ORDER BY\n" +
            "    A.TABLE_SCHEM,A.TABLE_NAME,A.ORDINAL_POSITION";

    private static String SELECT_TABLE_COLUMNS1 = "SELECT * FROM information_schema.COLUMNS  WHERE TABLE_SCHEMA =  '%s'  AND TABLE_NAME =  '%s'  order by ORDINAL_POSITION";
    @Override
    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
        String sql = String.format(SELECT_TABLE_COLUMNS,tableName, schemaName );
        List<TableColumn> tableColumns = new ArrayList<>();
        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            while (resultSet.next()) {
                TableColumn column = new TableColumn();
                column.setDatabaseName(databaseName);
                column.setTableName(tableName);
                column.setOldName(resultSet.getString("COLUMN_NAME"));
                column.setName(resultSet.getString("COLUMN_NAME"));
                //column.setColumnType(resultSet.getString("COLUMN_TYPE"));
                column.setColumnType(resultSet.getString("TYPE_NAME").toUpperCase());
                //column.setDataType(resultSet.getInt("DATA_TYPE"));
//                column.setDefaultValue(resultSet.getString("DATA_DEFAULT"));
//                column.setAutoIncrement(resultSet.getString("EXTRA").contains("auto_increment"));
                column.setComment(resultSet.getString("REMARKS"));
//                column.setPrimaryKey("PRI".equalsIgnoreCase(resultSet.getString("COLUMN_KEY")));
                column.setNullable("YES".equalsIgnoreCase(resultSet.getString("IS_NULLABLE")) ? 1 : 0);
                column.setOrdinalPosition(resultSet.getInt("ORDINAL_POSITION"));
                column.setDecimalDigits(resultSet.getInt("FIXED_PREC_SCALE"));
                column.setColumnSize(resultSet.getInt("COLUMN_SIZE"));
//                column.setCharSetName(resultSet.getString("CHARACTER_SET_NAME"));
//                column.setCollationName(resultSet.getString("COLLATION_NAME"));
//                setColumnSize(column, resultSet.getString("COLUMN_TYPE"));
                tableColumns.add(column);
                //
            }
            return tableColumns;
        });
    }

//    @Override
//    public List<TableColumn> columns(Connection connection, String databaseName, String schemaName, String tableName) {
//        String sql = String.format(SELECT_TABLE_COLUMNS, schemaName, tableName);
//        return SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
//            List<TableColumn> tableColumns = new ArrayList<>();
//            while (resultSet.next()) {
//                TableColumn tableColumn = new TableColumn();
//                tableColumn.setTableName(tableName);
//                tableColumn.setSchemaName(schemaName);
//                try {
//                    //
//                    // Fields of the LONG type cannot be retrieved using getObject. They need to be accessed using getCharacterStream, and must be read first in the sequence.
//                    Reader reader = resultSet.getCharacterStream("DATA_DEFAULT");
//                    if(reader != null){
//                        StringBuilder sb = new StringBuilder();
//                        int charValue;
//                        while ((charValue = reader.read()) != -1) {
//                            sb.append((char) charValue);
//                        }
//                        tableColumn.setDefaultValue(sb.toString());
//                    }
//                }catch (Exception e){
//                    e.printStackTrace();
//                }
//                tableColumn.setName(resultSet.getString("COLUMN_NAME"));
//                tableColumn.setColumnType(resultSet.getString("DATA_TYPE"));
//                Integer dataPrecision = resultSet.getInt("DATA_PRECISION");
//                if(dataPrecision!=null) {
//                    tableColumn.setColumnSize(dataPrecision);
//                }else {
//                    tableColumn.setColumnSize(resultSet.getInt("DATA_LENGTH"));
//                }
////                Object dataDefault = resultSet.getObject(7);
////                if(dataDefault!=null) {
////                    tableColumn.setDefaultValue(dataDefault.toString());
////                }
//
//
//
//
//                tableColumn.setComment(resultSet.getString("COMMENTS"));
//                tableColumn.setNullable("Y".equalsIgnoreCase(resultSet.getString("NULLABLE")) ? 1 : 0);
//                tableColumn.setOrdinalPosition(resultSet.getInt("COLUMN_ID"));
//                tableColumn.setDecimalDigits(resultSet.getInt("DATA_SCALE"));
//                String charUsed = resultSet.getString("CHAR_USED");
//                if ("B".equalsIgnoreCase(charUsed)) {
//                    tableColumn.setUnit("BYTE");
//                } else if ("C".equalsIgnoreCase(charUsed)) {
//                    tableColumn.setUnit("CHAR");
//                }
//                tableColumns.add(tableColumn);
//            }
//            return tableColumns;
//        });
//    }

    private void setColumnSize(TableColumn column, String columnType) {
        try {
            if (columnType.contains("(")) {
                String size = columnType.substring(columnType.indexOf("(") + 1, columnType.indexOf(")"));
                if ("SET".equalsIgnoreCase(column.getColumnType()) || "ENUM".equalsIgnoreCase(column.getColumnType())) {
                    column.setValue(size);
                } else {
                    if (size.contains(",")) {
                        String[] sizes = size.split(",");
                        if (StringUtils.isNotBlank(sizes[0])) {
                            column.setColumnSize(Integer.parseInt(sizes[0]));
                        }
                        if (StringUtils.isNotBlank(sizes[1])) {
                            column.setDecimalDigits(Integer.parseInt(sizes[1]));
                        }
                    } else {
                        column.setColumnSize(Integer.parseInt(size));
                    }
                }
            }
        } catch (Exception e) {
        }
    }


    private static String VIEW_SQL
            =   "SELECT OWNER, VIEW_NAME, TEXT FROM ALL_VIEWS WHERE OWNER = '%s' AND VIEW_NAME = '%s'";

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




private static String INDEX_SQL = " SELECT DISTINCT i.TABLE_NAME, i.INDEX_TYPE, i.INDEX_NAME, i.UNIQUENESS ,c.COLUMN_NAME, c.COLUMN_POSITION, c.DESCEND, cons.CONSTRAINT_TYPE FROM ALL_INDEXES i JOIN ALL_IND_COLUMNS c ON i.INDEX_NAME = c.INDEX_NAME AND i.TABLE_NAME = c.TABLE_NAME AND i.TABLE_OWNER = c.TABLE_OWNER LEFT JOIN ALL_CONSTRAINTS cons ON i.INDEX_NAME = cons.INDEX_NAME AND i.TABLE_NAME = cons.TABLE_NAME AND i.TABLE_OWNER = cons.OWNER WHERE i.TABLE_OWNER = '%s' AND i.TABLE_NAME = '%s' ORDER BY i.INDEX_NAME, c.COLUMN_POSITION;";


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
                    index.setUnique("UNIQUE".equalsIgnoreCase(resultSet.getString("UNIQUENESS")));
//                    index.setType(resultSet.getString("Index_type"));
//                    index.setComment(resultSet.getString("Index_comment"));
                    List<TableIndexColumn> tableIndexColumns = new ArrayList<>();
                    tableIndexColumns.add(getTableIndexColumn(resultSet));
                    index.setColumnList(tableIndexColumns);
                    if ("P".equalsIgnoreCase(resultSet.getString("CONSTRAINT_TYPE"))) {
                        index.setType(OscarIndexTypeEnum.PRIMARY_KEY.getName());
                    } else if ("BITMAP".equalsIgnoreCase(resultSet.getString("INDEX_TYPE"))) {
                        index.setType(OscarIndexTypeEnum.BITMAP.getName());
                    } else {
                        index.setType(OscarIndexTypeEnum.NORMAL.getName());
                    }
                    map.put(keyName, index);
                }
            }
            return map.values().stream().collect(Collectors.toList());
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
        return new OscarSqlBuilder();
    }

    @Override
    public TableMeta getTableMeta(String databaseName, String schemaName, String tableName) {
        return TableMeta.builder()
                .columnTypes(OscarColumnTypeEnum.getTypes())
                .charsets(OscarCharsetEnum.getCharsets())
                .collations(OscarCollationEnum.getCollations())
                .indexTypes(OscarIndexTypeEnum.getIndexTypes())
                .defaultValues(OscarDefaultValueEnum.getDefaultValues())
                .build();
    }

    @Override
    public String getMetaDataName(String... names) {
        return Arrays.stream(names).filter(name -> StringUtils.isNotBlank(name)).map(name ->  name).collect(Collectors.joining("."));
    }

//    @Override
//    public ValueHandler getValueHandler() {
//        return new OscarValueHandler();
//    }
}
