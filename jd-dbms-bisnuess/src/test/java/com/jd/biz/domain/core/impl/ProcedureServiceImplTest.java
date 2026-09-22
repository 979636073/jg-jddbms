package com.jd.biz.domain.core.impl;

import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.Procedure;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class ProcedureServiceImplTest {

    @After
    public void clearContext() {
        Chat2DBContext.removeContext();
    }

    @Test
    public void shouldExecuteCreateOrReplaceForDmProcedureUpdate() throws Exception {
        AtomicReference<String> executedSql = new AtomicReference<>();
        putContext(connection(executedSql));
        Procedure procedure = new Procedure();
        procedure.setProcedureName("P_TEST");
        procedure.setProcedureBody("PROCEDURE P_TEST AS BEGIN NULL; END;");

        new ProcedureServiceImpl().update("", "SYSDBA", procedure);

        assertEquals("CREATE OR REPLACE PROCEDURE P_TEST AS BEGIN NULL; END;", executedSql.get());
    }

    @Test
    public void shouldKeepExistingCreateClause() {
        ProcedureServiceImpl service = new ProcedureServiceImpl();
        String sql = "CREATE OR REPLACE PROCEDURE P_TEST AS BEGIN NULL; END;";

        assertEquals(sql, service.buildCreateOrReplaceSql(sql));
    }

    private void putContext(Connection connection) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType("DM");
        connectInfo.setDriverConfig(new DriverConfig());
        connectInfo.setConnection(connection);
        Chat2DBContext.putContext(connectInfo);
    }

    private Connection connection(AtomicReference<String> executedSql) {
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        executedSql.set((String) args[0]);
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
