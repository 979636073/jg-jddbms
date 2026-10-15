package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.TypeQueryRequest;
import com.jd.biz.domain.core.cache.CacheManage;
import com.jd.plugin.dm.DMMetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.AssociationTree;
import com.jd.spi.model.ForeignData;
import com.jd.spi.model.ViewReferencedData;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class TableRelationshipCacheTest {
    private Plugin previous;
    private final RecordingMetadata metadata = new RecordingMetadata();
    private final TableServiceImpl service = new TableServiceImpl();
    private final TypeQueryRequest request = new TypeQueryRequest();

    @Before public void prepare() {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> "getMetaData".equals(method.getName()) ? metadata : null);
        previous = Chat2DBContext.PLUGIN_MAP.put("DM", plugin);
        ConnectInfo info = new ConnectInfo(); info.setDbType("DM"); info.setDataSourceId(811L);
        info.setDriverConfig(new DriverConfig());
        info.setConnection((Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null));
        Chat2DBContext.putContext(info);
        request.setDataSourceId(811L); request.setSchemaName("APP"); request.setTableName("QA_" + System.nanoTime());
    }
    @After public void clear() {
        Chat2DBContext.removeContext();
        if (previous == null) Chat2DBContext.PLUGIN_MAP.remove("DM");
        else Chat2DBContext.PLUGIN_MAP.put("DM", previous);
    }
    @Test public void emptyRelationsAreCachedAndManualRefreshLoadsNewForeignKey() {
        assertNull(service.queryReferencedList(request)); assertNull(service.queryReferencedList(request));
        assertEquals(1, metadata.foreignReads);
        metadata.hasForeignKey = true; request.setIsRefreshCache(true);
        AssociationTree result = service.queryReferencedList(request);
        assertEquals(1, result.getChildren().size()); assertEquals("P_ID", result.getChildren().get(0).getForeignColumnName());
        request.setIsRefreshCache(false); result.getChildren().clear();
        assertEquals(1, service.queryReferencedList(request).getChildren().size());
        assertEquals(2, metadata.foreignReads);
    }
    @Test public void keysIsolateSourcesSchemasDatabasesAndViewDirections() {
        service.queryReferencedList(request);
        request.setDataSourceId(812L); service.queryReferencedList(request);
        request.setSchemaName("OTHER"); service.queryReferencedList(request);
        request.setDatabaseName("OTHER_DB"); service.queryReferencedList(request);
        assertEquals(4, metadata.foreignReads);
        request.setCheck("1"); service.queryViewReferenced(request); service.queryViewReferenced(request);
        request.setCheck("0"); service.queryViewReferenced(request); service.queryViewReferenced(request);
        assertEquals(1, metadata.viewReads); assertEquals(1, metadata.uponReads);
    }
    @Test public void ddlInvalidationIncludesEmptyAndViewResultsButOnlyForChangedSource() {
        service.queryReferencedList(request); request.setCheck("1"); service.queryViewReferenced(request);
        request.setDataSourceId(812L); service.queryReferencedList(request);
        CacheManage.invalidateRelations(811L);
        service.queryReferencedList(request); assertEquals(2, metadata.foreignReads);
        request.setDataSourceId(811L); metadata.hasForeignKey = true;
        assertNotNull(service.queryReferencedList(request)); service.queryViewReferenced(request);
        assertEquals(3, metadata.foreignReads); assertEquals(2, metadata.viewReads);
    }
    private static class RecordingMetadata extends DMMetaData {
        int foreignReads, viewReads, uponReads;
        boolean hasForeignKey;
        @Override public List<ForeignData> beiForeGnKey(Connection connection, String schema, String table) {
            foreignReads++;
            if (!hasForeignKey) return Collections.emptyList();
            ForeignData fk = new ForeignData(); fk.setForeignSchemaName(schema); fk.setForeignTableName("CHILD");
            fk.setConstraintName("FK_CHILD"); fk.setColumn("ID"); fk.setForeignColumnName("P_ID");
            return Collections.singletonList(fk);
        }
        @Override public List<ViewReferencedData> getViewReferenced(Connection connection, String schema, String table) {
            viewReads++; return Collections.emptyList();
        }
        @Override public List<ViewReferencedData> getViewUponReferenced(Connection connection, String schema, String table) {
            uponReads++; return Collections.emptyList();
        }
    }
}
