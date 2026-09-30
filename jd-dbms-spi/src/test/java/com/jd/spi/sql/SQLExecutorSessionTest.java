package com.jd.spi.sql;

import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.ConnectionVo;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.Plugin;
import com.jd.spi.MetaData;
import com.jd.spi.jdbc.DefaultValueHandler;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class SQLExecutorSessionTest {
    @Test
    public void appliesSelectedSchemaToOracleAndDmSessions() throws Exception {
        java.util.List<String> sql = new java.util.ArrayList<>();
        AtomicInteger closes = new AtomicInteger();
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class[]{Statement.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) { sql.add((String) args[0]); return false; }
                    if ("close".equals(method.getName())) closes.incrementAndGet();
                    return null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class[]{Connection.class},
                (proxy, method, args) -> "createStatement".equals(method.getName()) ? statement : null);
        ConnectInfo info = new ConnectInfo();
        info.setSchemaName("Test\"Schema");
        for (String type : new String[]{"ORACLE", "DM"}) {
            info.setDbType(type);
            SQLExecutor.applySelectedSchema(connection, info);
        }
        info.setDbType("MYSQL");
        SQLExecutor.applySelectedSchema(connection, info);
        assertEquals(2, sql.size());
        assertEquals("ALTER SESSION SET CURRENT_SCHEMA = \"Test\"\"Schema\"", sql.get(0));
        assertEquals(sql.get(0), sql.get(1));
        assertEquals(2, closes.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void queryErrorDoesNotRollbackAnExistingPendingTransaction() throws Exception {
        AtomicInteger rollbacks = new AtomicInteger();
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class[]{Statement.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) throw new SQLException("Invalid SQL syntax");
                    return null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class[]{Connection.class},
                (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) return statement;
                    if ("isClosed".equals(method.getName())) return false;
                    if ("rollback".equals(method.getName())) rollbacks.incrementAndGet();
                    return null;
                });
        MetaData metadata = (MetaData) Proxy.newProxyInstance(MetaData.class.getClassLoader(), new Class[]{MetaData.class},
                (proxy, method, args) -> "getValueHandler".equals(method.getName()) ? new DefaultValueHandler() : null);
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class[]{Plugin.class},
                (proxy, method, args) -> "getMetaData".equals(method.getName()) ? metadata : null);
        Plugin previous = Chat2DBContext.PLUGIN_MAP.put("oracle", plugin);
        ConnectInfo info = new ConnectInfo();
        info.setDbType("oracle"); info.setDataSourceId(1L); info.setConsoleId(2L); info.setDriverConfig(new DriverConfig());
        Chat2DBContext.putContext(info);
        String key = "pending-error-test@" + System.currentTimeMillis();
        ConnectionVo session = new ConnectionVo();
        session.setConnection(connection); session.setSign(true); session.setSessionId(key); session.bind(info);
        Field field = SQLExecutor.class.getDeclaredField("ACTIVE_SESSIONS");
        field.setAccessible(true);
        Map<String, ConnectionVo> sessions = (Map<String, ConnectionVo>) field.get(null);
        sessions.put(key, session);
        try {
            Method execute = SQLExecutor.class.getDeclaredMethod("execute", String.class, Integer.class, Integer.class,
                    Boolean.class, String.class, Boolean.class, String.class, Integer.class);
            execute.setAccessible(true);
            ExecuteResult result = (ExecuteResult) execute.invoke(SQLExecutor.getInstance(),
                    "SELECT INVALID", 0, 10, false, key, false, null, null);
            assertFalse(result.getSuccess()); assertTrue(result.getSign());
            assertEquals(key, result.getSessionId()); assertEquals(0, rollbacks.get());
        } finally {
            sessions.remove(key);
            if (previous == null) Chat2DBContext.PLUGIN_MAP.remove("oracle");
            else Chat2DBContext.PLUGIN_MAP.put("oracle", previous);
        }
    }

    @After
    public void clearContext() {
        Chat2DBContext.removeContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void refusesToRetryLostPendingTransaction() throws Exception {
        ConnectInfo info = new ConnectInfo();
        info.setDbType("oracle");
        info.setDriverConfig(new DriverConfig());
        info.setDataSourceId(1L);
        info.setConsoleId(2L);
        Chat2DBContext.putContext(info);

        Connection closed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class[]{Connection.class}, (proxy, method, args) ->
                        "isClosed".equals(method.getName()) ? true : null);
        ConnectionVo session = new ConnectionVo();
        session.setConnection(closed);
        session.setSign(true);
        session.bind(info);
        Field field = SQLExecutor.class.getDeclaredField("ACTIVE_SESSIONS");
        field.setAccessible(true);
        Map<String, ConnectionVo> sessions = (Map<String, ConnectionVo>) field.get(null);
        sessions.put("closed-pending-test", session);

        Method execute = SQLExecutor.class.getDeclaredMethod("execute", String.class, Integer.class, Integer.class,
                Boolean.class, String.class, Boolean.class, String.class, Integer.class);
        execute.setAccessible(true);
        ExecuteResult result = (ExecuteResult) execute.invoke(SQLExecutor.getInstance(),
                "UPDATE T SET V = 1", 0, 10, false, "closed-pending-test", false, null, null);

        assertFalse(result.getSuccess());
        assertTrue(result.getMessage().contains("待提交事务"));
        assertNull(result.getSessionId());
        assertFalse(sessions.containsKey("closed-pending-test"));
    }
}
