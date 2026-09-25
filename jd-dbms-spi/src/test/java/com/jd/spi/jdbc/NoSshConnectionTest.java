package com.jd.spi.jdbc;

import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.DataSourceConnect;
import com.jd.spi.model.DriverEntry;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.sql.IDriverManager;
import com.jd.spi.util.JdbcUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NoSshConnectionTest {
    private final DriverConfig driverConfig = new DriverConfig();
    private final AtomicInteger connectionCount = new AtomicInteger();
    private final Connection connection = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
            (proxy, method, args) -> "isClosed".equals(method.getName()) ? false : null);
    private Map<String, DriverEntry> driverEntries;

    @Before
    @SuppressWarnings("unchecked")
    public void registerDriver() throws Exception {
        driverConfig.setJdbcDriver("no-ssh-test-" + UUID.randomUUID());
        Driver driver = (Driver) Proxy.newProxyInstance(Driver.class.getClassLoader(),
                new Class<?>[]{Driver.class}, (proxy, method, args) -> {
                    if ("connect".equals(method.getName())) {
                        connectionCount.incrementAndGet();
                        return connection;
                    }
                    return null;
                });
        Field field = IDriverManager.class.getDeclaredField("DRIVER_ENTRY_MAP");
        field.setAccessible(true);
        driverEntries = (Map<String, DriverEntry>) field.get(null);
        driverEntries.put(driverConfig.getJdbcDriver(), DriverEntry.builder()
                .driverConfig(driverConfig).driver(driver).build());
    }

    @After
    public void unregisterDriver() {
        if (driverEntries != null) {
            driverEntries.remove(driverConfig.getJdbcDriver());
        }
    }

    @Test
    public void shouldConnectSavedDataSourceWithoutSsh() {
        ConnectInfo info = new ConnectInfo();
        info.setUrl("jdbc:test:direct");
        info.setHost("localhost");
        info.setPort(1234);
        info.setDriverConfig(driverConfig);

        assertSame(connection, new DefaultDBManage().getConnection(info));
        assertEquals(1, connectionCount.get());
    }

    @Test
    public void shouldTestConnectionWithoutSsh() {
        DataSourceConnect result = JdbcUtils.testConnect("jdbc:test:direct", "localhost", "1234",
                "user", "password", "oracle", driverConfig, null, Collections.emptyMap());

        assertTrue(result.getSuccess());
        assertEquals(1, connectionCount.get());
    }
}
