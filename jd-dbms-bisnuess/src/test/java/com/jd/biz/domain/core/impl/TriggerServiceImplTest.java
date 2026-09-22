package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.TriggerDetailRequest;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class TriggerServiceImplTest {

    @After
    public void clearContext() {
        Chat2DBContext.removeContext();
    }

    @Test
    public void shouldReplaceTriggerWithoutDeletingItFirst() {
        AtomicInteger executions = new AtomicInteger();
        putContext(connection(executions));
        TriggerDetailRequest request = new TriggerDetailRequest();
        request.setTriggerName("TR_TEST");
        request.setIsUpdate(Boolean.TRUE);
        request.setSql("CREATE OR REPLACE TRIGGER TR_TEST BEFORE INSERT ON T BEGIN NULL; END;");

        new TriggerServiceImpl().createTriggersWH(request);

        assertEquals(1, executions.get());
    }

    private void putContext(Connection connection) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType("TRIGGER_TEST");
        connectInfo.setDriverConfig(new DriverConfig());
        connectInfo.setConnection(connection);
        Chat2DBContext.putContext(connectInfo);
    }

    private Connection connection(AtomicInteger executions) {
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
}
