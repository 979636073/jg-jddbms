package com.jd.spi.sql;

import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.ConnectionVo;
import com.jd.spi.model.ExecuteResult;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SQLExecutorSessionTest {

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
