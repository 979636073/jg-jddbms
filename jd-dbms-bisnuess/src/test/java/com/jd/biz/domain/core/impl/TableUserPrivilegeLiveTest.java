package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.controller.rdb.vo.UserOperationResult;
import com.jd.plugin.dm.builder.DMSqlBuilder;
import com.jd.plugin.oracle.builder.OracleSqlBuilder;
import com.jd.spi.SqlBuilder;
import com.jd.spi.model.TableObjectRoleData;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public class TableUserPrivilegeLiveTest {

    @Test
    public void oracleGrantAndRevokeInIsolatedUsers() throws Exception {
        verify("ORACLE_TEST_URL", "ORACLE_TEST_USER", "ORACLE_TEST_PASSWORD", "ORACLE_TEST_DRIVER_JAR",
                "oracle.jdbc.OracleDriver", new OracleSqlBuilder(), true);
    }

    @Test
    public void dmGrantAndRevokeInIsolatedUsers() throws Exception {
        verify("DM_TEST_URL", "DM_TEST_USER", "DM_TEST_PASSWORD", "DM_TEST_DRIVER_JAR",
                "dm.jdbc.driver.DmDriver", new DMSqlBuilder(), false);
    }

    private void verify(String urlKey, String userKey, String passwordKey, String driverJarKey,
                        String driverClass, SqlBuilder<?> builder, boolean oracle) throws Exception {
        String url = System.getenv(urlKey);
        String adminUser = System.getenv(userKey);
        String adminPassword = System.getenv(passwordKey);
        String driverJar = System.getenv(driverJarKey);
        Assume.assumeTrue(url != null && adminUser != null && adminPassword != null && driverJar != null);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String owner = "JQO_" + suffix;
        String grantee = "JQG_" + suffix;
        String password = "Qa9" + suffix.toLowerCase();
        boolean ownerCreated = false;
        boolean granteeCreated = false;
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{Paths.get(driverJar).toUri().toURL()});
             Connection admin = connect(loader, driverClass, url, adminUser, adminPassword)) {
            try {
                execute(admin, "CREATE USER \"" + owner + "\" IDENTIFIED BY \"" + password + "\"");
                ownerCreated = true;
                execute(admin, "CREATE USER \"" + grantee + "\" IDENTIFIED BY \"" + password + "\"");
                granteeCreated = true;
                if (oracle) {
                    execute(admin, "GRANT CREATE SESSION, CREATE TABLE TO \"" + owner + "\"");
                    execute(admin, "ALTER USER \"" + owner + "\" QUOTA 1M ON USERS");
                    execute(admin, "GRANT CREATE SESSION TO \"" + grantee + "\"");
                } else {
                    execute(admin, "GRANT RESOURCE TO \"" + owner + "\"");
                }

                try (Connection ownerConnection = connect(loader, driverClass, url, owner, password);
                     Connection granteeConnection = connect(loader, driverClass, url, grantee, password)) {
                    execute(ownerConnection, "CREATE TABLE \"Q_PRIV\" (\"ID\" INTEGER)");
                    TableUserServiceImpl service = new TableUserServiceImpl();
                    TableBriefQueryRequest grant = request(owner, grantee, true);
                    UserOperationResult granted = service.executeChanges(service.buildObjectRoleChanges(builder, grant),
                            sql -> execute(ownerConnection, sql));
                    Assert.assertTrue(granted.getItems().toString(), granted.isSuccess());
                    Assert.assertEquals("SUCCESS", granted.getItems().get(0).getStatus());
                    execute(granteeConnection, "SELECT * FROM \"" + owner + "\".\"Q_PRIV\"");

                    UserOperationResult denied = service.executeChanges(
                            service.buildObjectRoleChanges(builder, request(owner, adminUser, true)),
                            sql -> execute(granteeConnection, sql));
                    Assert.assertFalse("grantee without grant option must not grant", denied.isSuccess());
                    Assert.assertEquals("FAILED", denied.getItems().get(0).getStatus());
                    Assert.assertEquals("SELECT", denied.getItems().get(0).getName());
                    Assert.assertNotNull(denied.getItems().get(0).getMessage());

                    TableBriefQueryRequest revoke = request(owner, grantee, false);
                    UserOperationResult revoked = service.executeChanges(service.buildObjectRoleChanges(builder, revoke),
                            sql -> execute(ownerConnection, sql));
                    Assert.assertTrue(revoked.getItems().toString(), revoked.isSuccess());
                    Assert.assertEquals("REVOKE", revoked.getItems().get(0).getAction());
                    try {
                        execute(granteeConnection, "SELECT * FROM \"" + owner + "\".\"Q_PRIV\"");
                        Assert.fail("revoked user must not read the table");
                    } catch (SQLException expected) {
                        // The database must reject the read after REVOKE.
                    }
                }
            } finally {
                if (granteeCreated) execute(admin, "DROP USER \"" + grantee + "\" CASCADE");
                if (ownerCreated) execute(admin, "DROP USER \"" + owner + "\" CASCADE");
            }
        }
    }

    private TableBriefQueryRequest request(String owner, String grantee, boolean grant) {
        TableBriefQueryRequest request = new TableBriefQueryRequest();
        request.setSchemaName(owner);
        request.setUserName(grantee);
        request.setTableName("Q_PRIV");
        TableObjectRoleData role = new TableObjectRoleData();
        role.setDesc("SELECT");
        role.setRule(true);
        request.setOldObjectRoleData(grant ? Collections.emptyList() : Arrays.asList(role));
        request.setNewObjectRoleData(grant ? Arrays.asList(role) : Collections.emptyList());
        return request;
    }

    private Connection connect(ClassLoader loader, String driverClass, String url, String user, String password)
            throws Exception {
        Driver driver = (Driver) Class.forName(driverClass, true, loader).newInstance();
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        return driver.connect(url, properties);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
