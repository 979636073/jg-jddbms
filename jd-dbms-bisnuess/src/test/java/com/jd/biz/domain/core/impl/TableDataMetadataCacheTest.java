package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.service.TableService;
import com.jd.biz.domain.core.cache.CacheKey;
import com.jd.biz.domain.core.cache.CacheManage;
import com.jd.plugin.dm.DMMetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.Header;
import com.jd.spi.model.TableColumn;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class TableDataMetadataCacheTest {
    private Plugin previous;
    private final RecordingMetadata metadata = new RecordingMetadata();
    private final TableServiceImpl tables = new TableServiceImpl();
    private final DlTemplateServiceImpl service = new DlTemplateServiceImpl();
    private final String table = "QA_" + System.nanoTime();

    @After public void clear() {
        Chat2DBContext.removeContext();
        if (previous == null) Chat2DBContext.PLUGIN_MAP.remove("DM");
        else Chat2DBContext.PLUGIN_MAP.put("DM", previous);
    }

    @Test public void repeatedPagesReuseColumnsAndNeverReadIndexes() throws Exception {
        prepare(711L);
        for (int i = 0; i < 3; i++) {
            Header header = headers().get(0);
            assertTrue(header.getPrimaryKey());
            assertEquals(Integer.valueOf(1), header.getAutoIncrement());
            assertEquals(Integer.valueOf(1), header.getNullable());
            assertEquals(Integer.valueOf(12), header.getColumnSize());
            assertEquals("42", header.getDefaultValue());
        }
        assertEquals(1, metadata.columnReads);
        assertEquals(0, metadata.indexReads);
    }

    @Test public void dataSourceSchemaAndDatabaseAreIsolatedAndRefreshReplacesCachedColumns() throws Exception {
        prepare(711L);
        headers();
        Chat2DBContext.getConnectInfo().setDataSourceId(712L);
        headers();
        assertEquals(2, metadata.columnReads);
        metadata.defaultValue = "99";
        TableQueryParam param = new TableQueryParam();
        param.setDataSourceId(712L); param.setSchemaName("APP"); param.setTableName(table);
        param.setRefresh(true);
        tables.queryColumns(param);
        assertEquals("99", headers().get(0).getDefaultValue());
        assertEquals(3, metadata.columnReads);
        param.setRefresh(false); param.setSchemaName("OTHER"); tables.queryColumns(param);
        param.setDatabaseName("OTHER_DB"); tables.queryColumns(param);
        assertEquals(5, metadata.columnReads);
    }

    @Test public void invalidationIsLimitedToOneDataSourceAndCachedObjectsAreNotShared() throws Exception {
        prepare(711L); headers();
        Chat2DBContext.getConnectInfo().setDataSourceId(712L); headers();
        CacheManage.invalidateColumns(711L);
        assertEquals("42", headers().get(0).getDefaultValue());
        assertEquals(2, metadata.columnReads);
        Chat2DBContext.getConnectInfo().setDataSourceId(711L);
        metadata.defaultValue = "99";
        assertEquals("99", headers().get(0).getDefaultValue());
        assertEquals(3, metadata.columnReads);
        TableQueryParam param = new TableQueryParam();
        param.setDataSourceId(711L); param.setSchemaName("APP"); param.setTableName(table);
        tables.queryColumns(param).get(0).setDefaultValue("changed by caller");
        assertEquals("99", headers().get(0).getDefaultValue());
        assertEquals(3, metadata.columnReads);
        assertNotEquals(CacheKey.getColumnKey(711L, "A_schemaName_B", "C", table),
                CacheKey.getColumnKey(711L, "A", "B_schemaName_C", table));
    }

    private List<Header> headers() throws Exception {
        List<Header> result = new ArrayList<>(Collections.singletonList(Header.builder().name("ID").build()));
        Method method = DlTemplateServiceImpl.class.getDeclaredMethod("setColumnInfo", List.class, String.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(service, result, table, "APP", null);
        return result;
    }

    private void prepare(Long id) throws Exception {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> "getMetaData".equals(method.getName()) ? metadata : null);
        previous = Chat2DBContext.PLUGIN_MAP.put("DM", plugin);
        ConnectInfo info = new ConnectInfo(); info.setDbType("DM"); info.setDataSourceId(id);
        info.setDriverConfig(new DriverConfig());
        info.setConnection((Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null));
        Chat2DBContext.putContext(info);
        Field field = DlTemplateServiceImpl.class.getDeclaredField("tableService");
        field.setAccessible(true); field.set(service, (TableService) tables);
    }

    private static class RecordingMetadata extends DMMetaData {
        int columnReads, indexReads;
        String defaultValue = "42";
        @Override public List<TableColumn> columns(Connection connection, String database, String schema, String name) {
            columnReads++;
            TableColumn column = new TableColumn(); column.setName("ID"); column.setPrimaryKey(true);
            column.setAutoIncrement(true); column.setNullable(1); column.setColumnSize(12);
            column.setDefaultValue(defaultValue);
            return Collections.singletonList(column);
        }
        @Override public List<com.jd.spi.model.TableIndex> indexes(Connection connection, String database, String schema, String name) {
            indexReads++;
            return Collections.emptyList();
        }
    }
}
