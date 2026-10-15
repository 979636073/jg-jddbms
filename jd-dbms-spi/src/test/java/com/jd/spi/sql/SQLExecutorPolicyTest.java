package com.jd.spi.sql;

import com.jd.spi.model.Command;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.ConnectionVo;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SQLExecutorPolicyTest {
    @Test
    public void recognizesNativeQueriesWhenTheParserDoesNotUnderstandThem() {
        assertTrue(SQLExecutor.isReadQuery("-- note\nSELECT LEVEL FROM dual CONNECT BY LEVEL <= 405"));
        assertTrue(SQLExecutor.isReadQuery("/* hint */ WITH t AS (SELECT 1 FROM dual) SELECT * FROM t"));
        assertFalse(SQLExecutor.isReadQuery("BEGIN NULL; END;"));
    }
    @Test
    public void recognizesMultilineCommentedDmlAndMergeTransactions() {
        assertTrue(SQLExecutor.parseSqlCommit(java.util.Arrays.asList("-- note\nUPDATE T\nSET V = 1")));
        assertTrue(SQLExecutor.parseSqlCommit(java.util.Arrays.asList("/* note */ MERGE INTO T USING S ON (T.ID=S.ID) WHEN MATCHED THEN UPDATE SET T.V=S.V")));
        assertFalse(SQLExecutor.parseSqlCommit(java.util.Arrays.asList("SELECT 'insert' FROM dual")));
    }

    @Test
    public void shouldBoundPageNumberAndPageSize() {
        assertEquals(1, SQLExecutor.normalizePageNo(null));
        assertEquals(1, SQLExecutor.normalizePageNo(0));
        assertEquals(3, SQLExecutor.normalizePageNo(3));
        assertEquals(1, SQLExecutor.normalizePageSize(0));
        assertEquals(200, SQLExecutor.normalizePageSize(200));
        assertEquals(1000, SQLExecutor.normalizePageSize(5000));
    }

    @Test
    public void shouldBoundQueryTimeout() {
        assertEquals(60, SQLExecutor.normalizeQueryTimeout(null));
        assertEquals(1, SQLExecutor.normalizeQueryTimeout(0));
        assertEquals(5, SQLExecutor.normalizeQueryTimeout(5));
        assertEquals(300, SQLExecutor.normalizeQueryTimeout(600));
    }

    @Test
    public void shouldApplyExplicitMetadataQueryTimeout() {
        AtomicInteger timeout = new AtomicInteger();
        ResultSet resultSet = proxy(ResultSet.class, (method, args) -> null);
        Statement statement = proxy(Statement.class, (method, args) -> {
            if ("setQueryTimeout".equals(method)) timeout.set((Integer) args[0]);
            if ("execute".equals(method)) return true;
            if ("getResultSet".equals(method)) return resultSet;
            return null;
        });
        Connection connection = proxy(Connection.class, (method, args) ->
                "createStatement".equals(method) ? statement : null);

        String result = SQLExecutor.getInstance().execute(connection, "SELECT 1", 15, rs -> "OK");

        assertEquals("OK", result);
        assertEquals(15, timeout.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void exactCountUsesTheOwnedQuerySessionAndPreservesZero() throws Exception {
        AtomicInteger timeout = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        ResultSet resultSet = proxy(ResultSet.class, (method, args) -> {
            if ("next".equals(method)) return executions.getAndIncrement() == 0;
            if ("getString".equals(method)) return "0";
            return null;
        });
        Statement statement = proxy(Statement.class, (method, args) -> {
            if ("setQueryTimeout".equals(method)) timeout.set((Integer) args[0]);
            if ("executeQuery".equals(method)) return resultSet;
            return null;
        });
        Connection ownedConnection = proxy(Connection.class, (method, args) ->
                "createStatement".equals(method) ? statement : null);
        Connection otherConnection = proxy(Connection.class, (method, args) -> {
            if ("createStatement".equals(method)) throw new AssertionError("Count must not use another connection");
            return null;
        });
        ConnectInfo info = new ConnectInfo();
        info.setDbType("ORACLE"); info.setDataSourceId(91L); info.setConsoleId(92L);
        info.setDriverConfig(new com.jd.spi.config.DriverConfig());
        info.setConnection(otherConnection);
        Chat2DBContext.putContext(info);
        String id = "exact-count-test@" + System.nanoTime();
        ConnectionVo session = new ConnectionVo();
        session.setConnection(ownedConnection); session.bind(info);
        Field field = SQLExecutor.class.getDeclaredField("ACTIVE_SESSIONS");
        field.setAccessible(true);
        Map<String, ConnectionVo> sessions = (Map<String, ConnectionVo>) field.get(null);
        sessions.put(id, session);
        try {
            Method count = SQLExecutor.class.getDeclaredMethod("getCount", String.class, String.class, String.class, Integer.class);
            count.setAccessible(true);
            assertEquals("0", count.invoke(SQLExecutor.getInstance(), "SELECT COUNT(*) FROM T", id, null, 17));
            assertEquals(17, timeout.get());
        } finally {
            sessions.remove(id);
            Chat2DBContext.detachContext();
        }
    }

    @Test
    public void shouldStopAfterReturnedFailureWhenErrorExecutionIsDisabled() {
        Command command = new Command();
        command.setIsErrorExecute(true);
        ExecuteResult failed = new ExecuteResult();
        failed.setSuccess(false);

        assertTrue(SQLExecutor.shouldStopAfterResult(command, failed));

        command.setIsErrorExecute(false);
        assertFalse(SQLExecutor.shouldStopAfterResult(command, failed));
    }

    private <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (target, method, args) -> {
                    Object result = invocation.invoke(method.getName(), args);
                    if (result != null || !method.getReturnType().isPrimitive()) return result;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                }));
    }

    private interface Invocation {
        Object invoke(String method, Object[] args);
    }
}
