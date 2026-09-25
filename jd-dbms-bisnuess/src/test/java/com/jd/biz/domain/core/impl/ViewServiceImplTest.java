package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.ViewQueryRequest;
import com.jd.biz.controller.rdb.request.ViewRequest;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableDetails;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ViewServiceImplTest {

    @After
    public void clearContext() {
        Chat2DBContext.removeContext();
    }

    @Test
    public void shouldExecuteViewSqlOnceWithoutClosingContextConnection() {
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger connectionCloses = new AtomicInteger();
        putContext(connection(executions, connectionCloses));
        ViewRequest request = new ViewRequest();
        request.setViewSql("CREATE OR REPLACE VIEW V_TEST AS SELECT 1 ID FROM DUAL");

        ListResult<ExecuteResult> result = new ViewServiceImpl().execute(request);

        assertTrue(result.getSuccess());
        assertEquals(1, executions.get());
        assertEquals(0, connectionCloses.get());
    }

    @Test
    public void shouldCreateRenamedViewBeforeDroppingOriginalView() {
        RecordingViewService service = new RecordingViewService(true);

        assertTrue(service.updateViewTableName(renameRequest()));
        assertEquals(Arrays.asList("execute:CREATE VIEW \"SYSTEM\".\"V_NEW\" AS SELECT 1 FROM DUAL",
                "drop:V_OLD"), service.steps);
    }

    @Test
    public void shouldKeepOriginalViewWhenRenamedViewCreationFails() {
        RecordingViewService service = new RecordingViewService(false);

        assertFalse(service.updateViewTableName(renameRequest()));
        assertEquals(1, service.steps.size());
        assertTrue(service.steps.get(0).startsWith("execute:"));
    }

    @Test
    public void shouldRemoveNewViewWhenDroppingOriginalFails() {
        RecordingViewService service = new RecordingViewService(true, true);

        assertFalse(service.updateViewTableName(renameRequest()));
        assertEquals(Arrays.asList("execute:CREATE VIEW \"SYSTEM\".\"V_NEW\" AS SELECT 1 FROM DUAL",
                "drop:V_OLD", "drop:V_NEW"), service.steps);
    }

    @Test
    public void shouldReturnStoredViewDdlForExport() {
        ViewRequest request = new ViewRequest();
        request.setSchemaName("SYSTEM");
        request.setTableName("V_OLD");

        assertEquals("CREATE OR REPLACE VIEW \"SYSTEM\".\"V_OLD\" AS SELECT 1 FROM DUAL",
                new RecordingViewService(true).getViewSql(request).getData().getSql());
    }

    @Test
    public void shouldBuildDmMaterializedViewStatements() {
        assertEquals("REFRESH MATERIALIZED VIEW \"SYSDBA\".\"MV_TEST\"",
                ViewServiceImpl.buildRefreshMaterializedViewSql("DM", "SYSDBA", "MV_TEST"));
        assertEquals("DROP MATERIALIZED VIEW \"SYSDBA\".\"MV_TEST\"",
                ViewServiceImpl.buildDropMaterializedViewSql("SYSDBA", "MV_TEST"));
    }

    @Test
    public void shouldBuildOracleMaterializedViewRefreshStatement() {
        assertEquals("BEGIN DBMS_MVIEW.REFRESH('\"SYSTEM\".\"MV_TEST\"', 'C'); END;",
                ViewServiceImpl.buildRefreshMaterializedViewSql("ORACLE", "SYSTEM", "MV_TEST"));
    }

    @Test
    public void shouldIdentifyOracleMaterializedViewWithoutDbaPrivileges() throws Exception {
        AtomicReference<String> sql = new AtomicReference<>();
        Method lookup = ViewServiceImpl.class.getDeclaredMethod("isMaterializedViewObject",
                Connection.class, String.class, String.class, String.class);
        lookup.setAccessible(true);

        assertTrue((Boolean) lookup.invoke(new ViewServiceImpl(), metadataConnection(sql, null),
                "ORACLE", "SYSTEM", "MV_TEST"));
        assertEquals("SELECT 1 FROM ALL_MVIEWS WHERE OWNER = ? AND MVIEW_NAME = ?", sql.get());
    }

    @Test
    public void shouldKeepOracleMaterializedViewReadableWhenDdlIsHidden() throws Exception {
        Method ddl = ViewServiceImpl.class.getDeclaredMethod("materializedViewDdl",
                Connection.class, String.class, String.class, String.class);
        ddl.setAccessible(true);

        assertNull(ddl.invoke(new ViewServiceImpl(),
                metadataConnection(new AtomicReference<>(), new SQLException("metadata not visible", "42000", 31603)),
                "ORACLE", "SYSTEM", "MV_TEST"));
    }

    @Test
    public void shouldExplainOracleMaterializedRefreshPermissionDenied() {
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        throw new SQLException("ORA-01031: insufficient privileges ORA-06512: at SYS.DBMS_SNAPSHOT", "42000", 6550);
                    }
                    return defaultValue(method.getReturnType());
                });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) ->
                        "createStatement".equals(method.getName()) ? statement : defaultValue(method.getReturnType()));
        putContext(connection, "ORACLE");
        Table view = new Table();
        view.setName("MV_TEST");
        TableDetails details = new TableDetails();
        details.setType("MATERIALIZED VIEW");
        view.setTableDetails(details);
        ViewServiceImpl service = new ViewServiceImpl() {
            @Override
            public List<Table> getViewList(String databaseName, String schemaName) {
                return Arrays.asList(view);
            }
        };
        ViewRequest request = new ViewRequest();
        request.setSchemaName("SYSTEM");
        request.setTableName("MV_TEST");

        try {
            service.refreshMaterialized(request);
            fail("Expected refresh permission failure");
        } catch (BusinessException e) {
            assertEquals("当前账号无权刷新物化视图，请使用对象所有者或具备刷新权限的账号", e.getMessage());
        }
    }

    @Test
    public void shouldRenameOnlyRequestedColumnWithoutParsingSelectBody() {
        RecordingViewService service = new RecordingViewService(true, false,
                "SELECT 1 OLD_ID, (SELECT 2 FROM DUAL) OTHER_ID FROM DUAL");

        assertTrue(service.updateViewColumnName(columnRenameRequest("OLD_ID", "NEW_ID")));
        assertEquals(Arrays.asList("execute:CREATE OR REPLACE VIEW \"SYSTEM\".\"V_OLD\" ( \"NEW_ID\",\"OTHER_ID\" )  AS SELECT 1 OLD_ID, (SELECT 2 FROM DUAL) OTHER_ID FROM DUAL"), service.steps);
    }

    @Test
    public void shouldNotReplayOldDdlWhenColumnRenameFails() {
        RecordingViewService service = new RecordingViewService(false, false,
                "SELECT 1 OLD_ID, (SELECT 2 FROM DUAL) OTHER_ID FROM DUAL");

        assertFalse(service.updateViewColumnName(columnRenameRequest("OLD_ID", "NEW_ID")));
        assertEquals(1, service.steps.size());
    }

    @Test
    public void shouldRejectUnknownOrDuplicateColumnNameBeforeExecuting() {
        RecordingViewService service = new RecordingViewService(true, false,
                "SELECT 1 OLD_ID, (SELECT 2 FROM DUAL) OTHER_ID FROM DUAL");

        assertFalse(service.updateViewColumnName(columnRenameRequest("MISSING", "NEW_ID")));
        assertFalse(service.updateViewColumnName(columnRenameRequest("OLD_ID", "OTHER_ID")));
        assertTrue(service.steps.isEmpty());
    }

    private ViewQueryRequest columnRenameRequest(String oldName, String newName) {
        ViewQueryRequest request = renameRequest();
        request.setOldColumns(Arrays.asList(oldName));
        request.setNewColumns(Arrays.asList(newName));
        return request;
    }

    private ViewQueryRequest renameRequest() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setDataSourceId(1L);
        request.setSchemaName("SYSTEM");
        request.setOldViewName("V_OLD");
        request.setNewViewName("V_NEW");
        return request;
    }

    private void putContext(Connection connection) {
        putContext(connection, "VIEW_TEST");
    }

    private void putContext(Connection connection, String dbType) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType(dbType);
        connectInfo.setDriverConfig(new DriverConfig());
        connectInfo.setConnection(connection);
        Chat2DBContext.putContext(connectInfo);
    }

    private Connection connection(AtomicInteger executions, AtomicInteger connectionCloses) {
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        executions.incrementAndGet();
                        return false;
                    }
                    return defaultValue(method.getReturnType());
                });
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) {
                        return statement;
                    }
                    if ("isClosed".equals(method.getName())) {
                        return false;
                    }
                    if ("close".equals(method.getName())) {
                        connectionCloses.incrementAndGet();
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private Connection metadataConnection(AtomicReference<String> sql, SQLException failure) {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) ->
                        "next".equals(method.getName()) ? true : defaultValue(method.getReturnType()));
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    if ("executeQuery".equals(method.getName())) {
                        if (failure != null) throw failure;
                        return resultSet;
                    }
                    return defaultValue(method.getReturnType());
                });
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        sql.set((String) args[0]);
                        return statement;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static class RecordingViewService extends ViewServiceImpl {
        private final boolean executeSuccess;
        private final boolean failOriginalDrop;
        private final String viewSql;
        private final List<String> steps = new ArrayList<>();

        private RecordingViewService(boolean executeSuccess) {
            this(executeSuccess, false);
        }

        private RecordingViewService(boolean executeSuccess, boolean failOriginalDrop) {
            this(executeSuccess, failOriginalDrop, "SELECT 1 FROM DUAL");
        }

        private RecordingViewService(boolean executeSuccess, boolean failOriginalDrop, String viewSql) {
            this.executeSuccess = executeSuccess;
            this.failOriginalDrop = failOriginalDrop;
            this.viewSql = viewSql;
        }

        @Override
        public DataResult<Table> detail(String databaseName, String schemaName, String tableName) {
            Table table = new Table();
            table.setDdl("CREATE OR REPLACE VIEW \"SYSTEM\".\"V_OLD\" AS SELECT 1 FROM DUAL");
            table.setViewSql(viewSql);
            if (viewSql.contains("OLD_ID")) {
                TableColumn first = new TableColumn();
                first.setName("OLD_ID");
                TableColumn second = new TableColumn();
                second.setName("OTHER_ID");
                table.setColumnList(Arrays.asList(first, second));
            }
            return DataResult.of(table);
        }

        @Override
        public ListResult<ExecuteResult> execute(ViewRequest request) {
            steps.add("execute:" + request.getViewSql());
            ListResult<ExecuteResult> result = new ListResult<>();
            result.setSuccess(executeSuccess);
            return result;
        }

        @Override
        public void drop(ViewRequest request) {
            steps.add("drop:" + request.getTableName());
            if (failOriginalDrop && "V_OLD".equals(request.getTableName())) {
                throw new RuntimeException("old view is referenced");
            }
        }
    }
}
