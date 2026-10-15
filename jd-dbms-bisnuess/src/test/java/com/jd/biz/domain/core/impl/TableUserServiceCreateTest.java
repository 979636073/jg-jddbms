package com.jd.biz.domain.core.impl;

import com.jd.common.tools.base.excption.BusinessException;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TableUserServiceCreateTest {

    @After
    public void clearContext() {
        Chat2DBContext.removeContext();
    }

    @Test
    public void shouldExplainDatabasePasswordPolicyFailure() {
        assertCreateFailure("Password not accord with complexity rule", "user.password.policy.failed");
    }

    @Test
    public void shouldExplainChineseDatabasePasswordPolicyFailure() {
        assertCreateFailure("密码长度不符合要求", "user.password.policy.failed");
    }

    @Test
    public void shouldKeepOtherDatabaseErrors() {
        assertCreateFailure("permission denied", "user.create.failed");
    }

    private void assertCreateFailure(String databaseError, String expectedCode) {
        Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        throw new SQLException(databaseError);
                    }
                    return false;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) {
                        return statement;
                    }
                    return false;
                });
        ConnectInfo info = new ConnectInfo();
        info.setDbType("DM");
        info.setDriverConfig(new DriverConfig());
        info.setConnection(connection);
        Chat2DBContext.putContext(info);

        try {
            new TableUserServiceImpl().createUser("QA_USER", "weakpass1", null, null, Collections.emptyList());
            fail("Expected the database error to be returned");
        } catch (BusinessException error) {
            assertEquals(expectedCode, error.getCode());
        }
    }
}
