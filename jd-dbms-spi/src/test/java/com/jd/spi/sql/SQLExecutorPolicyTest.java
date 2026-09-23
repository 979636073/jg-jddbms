package com.jd.spi.sql;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class SQLExecutorPolicyTest {

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
