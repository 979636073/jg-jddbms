package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.plugin.dm.DMMetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.*;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.*;

import static org.junit.Assert.*;

public class TableSectionLoadingTest {
    private Plugin original;
    private String dbType;
    private final RecordingMetadata metadata = new RecordingMetadata();

    @After
    public void clear() {
        Chat2DBContext.removeContext();
        if (original != null) Chat2DBContext.PLUGIN_MAP.put(dbType, original);
    }

    @Test
    public void dmSectionsShouldOnlyReadRequiredMetadata() {
        prepare("DM");
        read("columns");
        assertEquals(Collections.singletonList("columns"), metadata.calls);
        metadata.calls.clear();
        read("indexes");
        assertEquals(Collections.singletonList("indexes"), metadata.calls);
        metadata.calls.clear();
        read("basic");
        assertEquals(Collections.singletonList("tables"), metadata.calls);
        metadata.calls.clear();
        assertTrue(read("ddl").getQuerySql().contains("CREATE TABLE"));
        assertEquals(Collections.singletonList("ddl"), metadata.calls);
    }

    @Test
    public void oracleColumnsShouldPreserveCompositePrimaryKeyWithoutLoadingIndexesOrDdl() {
        prepare("ORACLE");
        Table table = read("columns");
        assertEquals(Arrays.asList("columns", "keys"), metadata.calls);
        assertTrue(table.getColumnList().get(0).getPrimaryKey());
        assertTrue(table.getColumnList().get(1).getPrimaryKey());
        assertEquals("PK_TEST", table.getColumnList().get(1).getPrimaryKeyName());
        assertEquals(2, table.getColumnList().get(1).getPrimaryKeyOrder());
        assertNull(table.getIndexList());
    }

    @Test
    public void constraintsShouldRetainKeysWithoutReadingDdlOrReturningOtherSections() {
        prepare("ORACLE");
        Table table = read("constraints");
        assertEquals(Arrays.asList("indexes", "keys", "columns", "foreign", "checks"), metadata.calls);
        assertEquals("ID,TENANT_ID", table.getConstraints().get(0).getColumn());
        assertNull(table.getColumnList());
        assertNull(table.getIndexList());
        assertNull(table.getQuerySql());
    }

    @Test
    public void dmConstraintsShouldRetainNotNullConstraints() {
        prepare("DM");
        Table table = read("constraints");
        assertEquals(2, table.getConstraints().size());
        assertTrue(table.getConstraints().get(0).getIsN());
        assertFalse(metadata.calls.contains("ddl"));
    }

    private Table read(String section) {
        TableQueryParam param = new TableQueryParam();
        param.setTableName("TEST");
        param.setSchemaName("APP");
        return new TableServiceImpl().querySection(param, section).getData();
    }

    private void prepare(String type) {
        dbType = type;
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> "getMetaData".equals(method.getName()) ? metadata : null);
        original = Chat2DBContext.PLUGIN_MAP.put(type, plugin);
        ConnectInfo info = new ConnectInfo();
        info.setDbType(type);
        info.setDriverConfig(new DriverConfig());
        info.setConnection((Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null));
        Chat2DBContext.putContext(info);
    }

    private static class RecordingMetadata extends DMMetaData {
        final List<String> calls = new ArrayList<>();

        @Override
        public List<Table> tables(Connection connection, String database, String schema, String name) {
            calls.add("tables");
            Table table = new Table();
            table.setName(name);
            return Collections.singletonList(table);
        }

        @Override
        public List<TableColumn> columns(Connection connection, String database, String schema, String name) {
            calls.add("columns");
            List<TableColumn> columns = new ArrayList<>();
            for (String columnName : Arrays.asList("ID", "TENANT_ID")) {
                TableColumn column = new TableColumn();
                column.setName(columnName);
                column.setPrimaryKey(false);
                column.setNullable(1);
                columns.add(column);
            }
            return columns;
        }

        @Override
        public List<TableIndex> indexes(Connection connection, String database, String schema, String name) {
            calls.add("indexes");
            return new ArrayList<>();
        }

        @Override
        public List<TableIndex> getConstraintsData(Connection connection, String database, String schema, String name) {
            calls.add("keys");
            TableIndex key = new TableIndex();
            key.setName("PK_TEST");
            key.setType("Primary");
            List<TableIndexColumn> columns = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                TableIndexColumn column = new TableIndexColumn();
                column.setColumnName(i == 0 ? "ID" : "TENANT_ID");
                column.setOrdinalPosition((short) (i + 1));
                columns.add(column);
            }
            key.setColumnList(columns);
            return new ArrayList<>(Collections.singletonList(key));
        }

        @Override
        public List<ForeignData> getForeignKey(Connection connection, String name, String schema) {
            calls.add("foreign");
            return Collections.emptyList();
        }

        @Override
        public Set<TableIndex> getCheckSQl(Connection connection, String schema, String name) {
            calls.add("checks");
            return Collections.emptySet();
        }

        @Override
        public String tableDDL(Connection connection, String database, String schema, String name) {
            calls.add("ddl");
            return "CREATE TABLE TEST (ID INT);";
        }
    }
}
