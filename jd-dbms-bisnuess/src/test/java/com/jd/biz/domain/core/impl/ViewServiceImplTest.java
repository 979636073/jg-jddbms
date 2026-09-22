package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.ViewQueryRequest;
import com.jd.biz.controller.rdb.request.ViewRequest;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Table;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertEquals(Arrays.asList("execute:CREATE OR REPLACE VIEW \"SYSTEM\".\"V_NEW\" AS SELECT 1 FROM DUAL",
                "drop:V_OLD"), service.steps);
    }

    @Test
    public void shouldKeepOriginalViewWhenRenamedViewCreationFails() {
        RecordingViewService service = new RecordingViewService(false);

        assertFalse(service.updateViewTableName(renameRequest()));
        assertEquals(1, service.steps.size());
        assertTrue(service.steps.get(0).startsWith("execute:"));
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
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType("VIEW_TEST");
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
        private final List<String> steps = new ArrayList<>();

        private RecordingViewService(boolean executeSuccess) {
            this.executeSuccess = executeSuccess;
        }

        @Override
        public DataResult<Table> detail(String databaseName, String schemaName, String tableName) {
            Table table = new Table();
            table.setDdl("CREATE OR REPLACE VIEW \"SYSTEM\".\"V_OLD\" AS SELECT 1 FROM DUAL");
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
        }
    }
}
