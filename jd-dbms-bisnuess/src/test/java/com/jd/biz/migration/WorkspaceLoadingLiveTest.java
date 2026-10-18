package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.spi.MetaData;
import com.jd.spi.model.Table;
import com.jd.spi.model.ForeignData;
import com.jd.spi.sql.Chat2DBContext;
import org.junit.Test;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static com.jd.biz.migration.MigrationDialect.qualified;
import static org.junit.Assert.*;

/** Bounded local JDBC baseline: 400 tables, wide columns, 1592 FKs and 10001 rows. */
public class WorkspaceLoadingLiveTest {
    @Test public void dmWorkspaceLoading() throws Exception { run("DM"); }
    @Test public void oracleWorkspaceLoading() throws Exception { run("ORACLE"); }

    private void run(String type) throws Exception {
        org.junit.Assume.assumeTrue("1".equals(System.getenv("JDDBMS_WORKSPACE_LIVE")));
        File root = new File(System.getProperty("user.dir"));
        if (root.getName().equals("jd-dbms-bisnuess")) root = root.getParentFile();
        System.setProperty("user.dir", root.getAbsolutePath());
        MigrationLiveTest env = new MigrationLiveTest();
        DataSource source;
        if ("ORACLE".equals(type)) source = env.environment(type);
        else {
            source = new DataSource(); source.setType(type); source.setDriverConfig(env.driver(type));
            source.setUrl(MigrationLiveTest.hostUrl(System.getenv("JDDBMS_META_DB_URL")));
            source.setUserName(System.getenv("JDDBMS_META_DB_USERNAME")); source.setPassword(System.getenv("JDDBMS_META_DB_PASSWORD"));
        }
        String schema = "DM".equals(type) ? "JQDMO_260926" : source.getUserName();
        String prefix = "JQA_W" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        String parent = prefix + "_P", wide = prefix + "_W";
        List<String> owned = new ArrayList<>();
        List<Map<String, Object>> metrics = new ArrayList<>();
        try (Connection connection = new MigrationConnections().open(source)) {
            try {
                create(connection, schema, parent, "ID NUMBER(12) PRIMARY KEY", owned);
                StringBuilder columns = new StringBuilder("ID NUMBER(12) PRIMARY KEY, NOTE VARCHAR(128), PAYLOAD BLOB, DETAIL CLOB");
                for (int i = 1; i <= 96; i++) columns.append(", C").append(i).append(" NUMBER(18,4)");
                create(connection, schema, wide, columns.toString(), owned);
                for (int i = 0; i < 398; i++) {
                    columns = new StringBuilder("ID NUMBER(12)");
                    for (int k = 1; k <= 4; k++) columns.append(", REF").append(k).append(" NUMBER(12) REFERENCES ").append(qualified(schema, parent)).append("(ID)");
                    for (int k = 1; k <= 55; k++) columns.append(", C").append(k).append(" VARCHAR(32)");
                    create(connection, schema, prefix + String.format(Locale.ROOT, "_T%03d", i), columns.toString(), owned);
                }
                connection.setAutoCommit(false);
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + qualified(schema, wide) + " (ID,NOTE,PAYLOAD,DETAIL) VALUES (?,?,?,?)")) {
                    for (int i = 1; i <= 10001; i++) {
                        insert.setInt(1, i); insert.setString(2, "宽表数据-" + i);
                        if (i == 1) {
                            insert.setBinaryStream(3, new ByteArrayInputStream(new byte[1024 * 1024]));
                            insert.setCharacterStream(4, new StringReader(String.join("", Collections.nCopies(32768, "中文LOB"))));
                            insert.executeUpdate();
                        } else {
                            insert.setNull(3, Types.BLOB); insert.setNull(4, Types.CLOB); insert.addBatch();
                            if (i % 500 == 0) { insert.executeBatch(); insert.clearBatch(); }
                        }
                    }
                    insert.executeBatch(); connection.commit();
                }
                MetaData meta = Chat2DBContext.getMetaData(type);
                Connection measured = measured(connection, metrics);
                for (int i = 0; i < 3; i++) {
                    long start = System.nanoTime();
                    PageResult<Table> first = meta.tableDetailsPage(measured, "", schema, prefix, 1, 200);
                    assertEquals(Long.valueOf(400), first.getTotal()); assertEquals(200, first.getData().size());
                    metric(metrics, "tablePage200-" + i, start);
                }
                PageResult<Table> second = meta.tableDetailsPage(measured, "", schema, prefix, 2, 200);
                assertEquals(200, second.getData().size());
                PageResult<Table> search = meta.tableDetailsPage(measured, "", schema, prefix + "_T397", 1, 200);
                assertEquals(Long.valueOf(1), search.getTotal()); assertEquals(prefix + "_T397", search.getData().get(0).getName());
                long start = System.nanoTime(); assertEquals(100, meta.columns(measured, "", schema, wide).size()); metric(metrics, "wideColumns100", start);
                start = System.nanoTime();
                List<ForeignData> foreignKeys = meta.getForeignKey(measured, prefix + "_T397", schema);
                assertEquals(4, foreignKeys.size());
                assertTrue(foreignKeys.stream().allMatch(key -> "VALID".equals(key.getStatus())));
                metric(metrics, "foreignKeys4", start);
                String disabledKey = foreignKeys.get(0).getConstraintName();
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE " + qualified(schema, prefix + "_T397") + " DISABLE CONSTRAINT " + MigrationDialect.quote(disabledKey));
                }
                assertTrue(meta.getForeignKey(connection, prefix + "_T397", schema).stream()
                        .anyMatch(key -> disabledKey.equals(key.getConstraintName()) && "INVALID".equals(key.getStatus())));
                try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + qualified(schema, wide))) {
                    assertTrue(rows.next()); assertEquals(10001, rows.getInt(1));
                }
                start = System.nanoTime();
                String sql = "DM".equals(type) ? "SELECT * FROM " + qualified(schema, wide) + " ORDER BY ID LIMIT 50" :
                        "SELECT * FROM (SELECT * FROM " + qualified(schema, wide) + " ORDER BY ID) WHERE ROWNUM<=50";
                try (Statement statement = measured.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
                    int count = 0;
                    while (rows.next()) { count++; assertEquals(count, rows.getInt("ID")); rows.getString("NOTE"); }
                    assertEquals(50, count);
                }
                metric(metrics, "dataPage50WithoutLobBody", start);
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("type", type); report.put("databaseVersion", connection.getMetaData().getDatabaseProductVersion());
                report.put("tables", 400); report.put("ordinaryColumns", 60); report.put("wideColumns", 100); report.put("foreignKeys", 1592); report.put("rows", 10001);
                report.put("scope", "local JDBC, not HTTP/browser or physical ARM SLA"); report.put("metrics", metrics);
                Path output = root.toPath().resolve("jd-dbms-bisnuess/target/workspace-loading-" + type + ".json");
                Files.write(output, JSON.toJSONBytes(report));
                System.out.println("WORKSPACE BASELINE " + type + " " + JSON.toJSONString(metrics));
            } finally {
                connection.rollback(); connection.setAutoCommit(true);
                Collections.reverse(owned);
                SQLException cleanupError = null;
                for (String table : owned) {
                    if (!table.startsWith(prefix + "_") || !table.matches("JQA_W[A-F0-9]{6}_(P|W|T[0-9]{3})")) throw new IllegalStateException("Not owned fixture");
                    try (Statement statement = connection.createStatement()) { statement.execute("DROP TABLE " + qualified(schema, table) + ("ORACLE".equals(type) ? " PURGE" : "")); }
                    catch (SQLException e) { if (cleanupError == null) cleanupError = e; else cleanupError.addSuppressed(e); }
                }
                if (cleanupError != null) throw cleanupError;
                System.out.println("WORKSPACE QA cleaned " + owned.size() + " owned " + type + " tables");
            }
        }
    }
    private static void create(Connection c, String schema, String name, String columns, List<String> owned) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute("CREATE TABLE " + qualified(schema, name) + " (" + columns + ")"); owned.add(name); }
    }
    private static void metric(List<Map<String, Object>> metrics, String name, long start) {
        Map<String, Object> metric = new LinkedHashMap<>(); metric.put("operation", name); metric.put("ms", (System.nanoTime() - start) / 1_000_000.0); metrics.add(metric);
    }
    private static Connection measured(Connection connection, List<Map<String, Object>> metrics) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
            try {
                Object result = m.invoke(connection, a);
                if (!(result instanceof Statement)) return result;
                String sql = a != null && a.length > 0 && a[0] instanceof String ? (String) a[0] : "statement";
                return Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{result instanceof PreparedStatement ? PreparedStatement.class : Statement.class}, (sp, sm, sa) -> {
                    long start = System.nanoTime();
                    try { return sm.invoke(result, sa); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                    finally { if (sm.getName().startsWith("execute")) metric(metrics, "jdbc:" + (sa != null && sa.length > 0 && sa[0] instanceof String ? sa[0] : sql), start); }
                });
            } catch (InvocationTargetException e) { throw e.getCause(); }
        });
    }
}
