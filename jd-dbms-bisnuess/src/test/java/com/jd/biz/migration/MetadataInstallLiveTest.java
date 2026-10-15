package com.jd.biz.migration;

import com.alibaba.druid.DbType;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.spi.util.SqlUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.Assert.*;

/** Executes the distributed SQL only in a newly created, owned DM QA schema. */
public class MetadataInstallLiveTest {
    @Test public void installAndRepeatedUpgradePreserveExistingData() throws Exception {
        org.junit.Assume.assumeTrue("1".equals(System.getenv("JDDBMS_INSTALL_LIVE")));
        Path root = Paths.get(System.getProperty("user.dir"));
        if (root.getFileName().toString().equals("jd-dbms-bisnuess")) root = root.getParent();
        System.setProperty("user.dir", root.toString());
        Path scripts = Paths.get(Objects.requireNonNull(System.getenv("JDDBMS_INSTALL_SQL_DIR")));
        String install = new String(Files.readAllBytes(scripts.resolve("JDDBMS_metadata_dm.sql")), StandardCharsets.UTF_8);
        String upgrade = new String(Files.readAllBytes(scripts.resolve("JDDBMS_upgrade_dm.sql")), StandardCharsets.UTF_8);
        String name = "JQA_INSTALL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String password = "Qa9_" + UUID.randomUUID().toString().replace("-", "");
        DataSource admin = new DataSource(); admin.setType("DM"); admin.setDriverConfig(new MigrationLiveTest().driver("DM"));
        admin.setUrl(MigrationLiveTest.hostUrl(System.getenv("JDDBMS_META_DB_URL")));
        admin.setUserName(System.getenv("JDDBMS_META_DB_USERNAME")); admin.setPassword(System.getenv("JDDBMS_META_DB_PASSWORD"));
        MigrationConnections connections = new MigrationConnections();
        try (Connection owner = connections.open(admin)) {
            assertEquals(0, number(owner, "SELECT COUNT(*) FROM ALL_USERS WHERE USERNAME='" + name + "'"));
            boolean created = false;
            try {
                execute(owner, "CREATE USER \"" + name + "\" IDENTIFIED BY \"" + password + "\" DEFAULT TABLESPACE MAIN"); created = true;
                execute(owner, "GRANT RESOURCE TO \"" + name + "\"");
                DataSource qa = new DataSource(); qa.setType("DM"); qa.setDriverConfig(admin.getDriverConfig());
                qa.setUrl(admin.getUrl()); qa.setUserName(name); qa.setPassword(password);
                try (Connection session = connections.open(qa)) {
                    runScript(session, install, name, "install");
                    assertTrue(number(session, "SELECT COUNT(*) FROM USER_TABLES") >= 40);
                    assertEquals(1, number(session, "SELECT COUNT(*) FROM SYS_USER WHERE USER_NAME='admin'"));
                    assertEquals("用户与权限", value(session, "SELECT MENU_NAME FROM SYS_MENU WHERE MENU_ID=1"));
                    try (Statement query = session.createStatement(); ResultSet notice = query.executeQuery("SELECT NOTICE_CONTENT FROM SYS_NOTICE WHERE NOTICE_ID=1")) {
                        assertTrue(notice.next());
                        assertEquals("新版本内容", new String(notice.getBytes(1), StandardCharsets.UTF_8));
                    }
                    execute(session, "UPDATE SYS_USER SET NICK_NAME='QA_KEEP', PASSWORD='QA_PASSWORD_SENTINEL' WHERE USER_NAME='admin'");
                    execute(session, "INSERT INTO DATA_SOURCE (GMT_CREATE,GMT_MODIFIED,ALIAS,USER_NAME,PASSWORD,TYPE,USER_ID) VALUES (SYSDATE,SYSDATE,'QA_KEEP','QA','v2:preserve-sentinel','DM',1)");
                    session.commit();
                    String roles = snapshot(session, "SELECT USER_ID,ROLE_ID FROM SYS_USER_ROLE ORDER BY USER_ID,ROLE_ID");
                    String grants = snapshot(session, "SELECT ROLE_ID,MENU_ID FROM SYS_ROLE_MENU ORDER BY ROLE_ID,MENU_ID");
                    // Recreate the supported legacy omissions in this disposable installation.
                    execute(session, "ALTER TABLE DATA_SOURCE DROP COLUMN PASSWORD_COPY");
                    execute(session, "ALTER TABLE DATA_SOURCE DROP COLUMN IS_SAVE");
                    execute(session, "ALTER TABLE DATA_SOURCE_ACCESS DROP COLUMN PERMISSION_CODES");
                    execute(session, "DROP TABLE HISTORY_USER_LOG");
                    execute(session, "DROP TABLE PIGENHOLE");
                    for (int i = 0; i < 2; i++) {
                        runScript(session, upgrade, name, "upgrade-" + (i + 1));
                        assertEquals("QA_KEEP", value(session, "SELECT NICK_NAME FROM SYS_USER WHERE USER_NAME='admin'"));
                        assertEquals("QA_PASSWORD_SENTINEL", value(session, "SELECT PASSWORD FROM SYS_USER WHERE USER_NAME='admin'"));
                        assertEquals("v2:preserve-sentinel", value(session, "SELECT PASSWORD FROM DATA_SOURCE WHERE ALIAS='QA_KEEP'"));
                        assertEquals(roles, snapshot(session, "SELECT USER_ID,ROLE_ID FROM SYS_USER_ROLE ORDER BY USER_ID,ROLE_ID"));
                        assertEquals(grants, snapshot(session, "SELECT ROLE_ID,MENU_ID FROM SYS_ROLE_MENU ORDER BY ROLE_ID,MENU_ID"));
                        assertEquals(2, number(session, "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME='DATA_SOURCE' AND COLUMN_NAME IN ('PASSWORD_COPY','IS_SAVE')"));
                        assertEquals(1, number(session, "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME='DATA_SOURCE_ACCESS' AND COLUMN_NAME='PERMISSION_CODES'"));
                        assertEquals(2, number(session, "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME IN ('HISTORY_USER_LOG','PIGENHOLE')"));
                    }
                    System.out.println("INSTALL QA: full install + legacy repair + repeated upgrade preserved users, roles and datasource; schema=" + name);
                }
            } finally {
                if (created) {
                    if (!name.matches("JQA_INSTALL_[A-F0-9]{8}")) throw new IllegalStateException("Not an owned QA schema");
                    execute(owner, "DROP USER \"" + name + "\" CASCADE");
                }
            }
            assertEquals(0, number(owner, "SELECT COUNT(*) FROM ALL_USERS WHERE USERNAME='" + name + "'"));
        }
    }

    private static void runScript(Connection session, String script, String schema, String label) throws Exception {
        List<String> statements = SqlUtils.parse(script.replaceAll("\\bJDDBMS\\b", schema), DbType.dm);
        int index = 0;
        for (String sql : statements) {
            index++;
            try { execute(session, sql); }
            catch (SQLException e) { throw new SQLException(label + " statement " + index + " failed: " + e.getMessage() + " SQL=" + sql, e.getSQLState(), e.getErrorCode(), e); }
        }
        session.commit();
        System.out.println("INSTALL QA " + label + ": " + statements.size() + " statements passed");
    }
    private static void execute(Connection c, String sql) throws SQLException { try (Statement s = c.createStatement()) { s.execute(sql); } }
    private static long number(Connection c, String sql) throws SQLException { return Long.parseLong(value(c, sql)); }
    private static String value(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); }
    }
    private static String snapshot(Connection c, String sql) throws SQLException {
        StringBuilder result = new StringBuilder();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) result.append(r.getLong(1)).append(':').append(r.getLong(2)).append(';');
        }
        return result.toString();
    }
}
