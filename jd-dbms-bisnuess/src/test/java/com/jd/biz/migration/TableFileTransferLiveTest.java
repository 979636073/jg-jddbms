package com.jd.biz.migration;

import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.model.DataSource;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.File;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;

/** Opt-in tests against the dedicated DM and Oracle QA users. */
public class TableFileTransferLiveTest {
    @Test public void everyFileFormatTransfersBothDirections() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_MIGRATION_LIVE")));
        File root = new File(System.getProperty("user.dir"));
        if ("jd-dbms-bisnuess".equals(root.getName())) System.setProperty("user.dir", root.getParentFile().getAbsolutePath());
        DataSource dm = source("DM"), oracle = source("ORACLE");
        Map<Long, DataSource> sources = new HashMap<>(); sources.put(dm.getId(), dm); sources.put(oracle.getId(), oracle);
        MigrationConnections connections = new MigrationConnections() {
            @Override public DataSource authorized(Long id, DataSourceAction action) { return sources.get(id); }
        };
        TableFileTransfer service = new TableFileTransfer(connections);
        String table = "JD_QA_FILE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String dmSchema = dm.getUserName(), oracleSchema = oracle.getUserName();
        String dmTable = MigrationDialect.qualified(dmSchema, table), oracleTable = MigrationDialect.qualified(oracleSchema, table);
        try {
            create(connections, dm, dmTable);
            create(connections, oracle, oracleTable);
            for (TableFileTransfer.Format format : TableFileTransfer.Format.values()) {
                verifyTransfer(service, connections, dm, dmSchema, oracle, oracleSchema, table, format);
                verifyTransfer(service, connections, oracle, oracleSchema, dm, dmSchema, table, format);
            }
        } finally {
            drop(connections, dm, dmTable, false);
            drop(connections, oracle, oracleTable, true);
        }
    }

    @Test public void schemaSqlCreatesTableAndDataAcrossBothDatabaseTypes() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_MIGRATION_LIVE")));
        File root = new File(System.getProperty("user.dir"));
        if ("jd-dbms-bisnuess".equals(root.getName())) System.setProperty("user.dir", root.getParentFile().getAbsolutePath());
        DataSource dm = source("DM"), oracle = source("ORACLE");
        MigrationConnections connections = new MigrationConnections();
        SchemaSqlTransfer service = new SchemaSqlTransfer(connections, new MigrationMetadata(), new TableFileTransfer(connections));
        verifySchemaTransfer(service, connections, dm, oracle);
        verifySchemaTransfer(service, connections, oracle, dm);
    }

    @Test public void schemaSqlIncludesIndexesAndConstraintsForBothDatabases() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_MIGRATION_LIVE")));
        File root = new File(System.getProperty("user.dir"));
        if ("jd-dbms-bisnuess".equals(root.getName())) System.setProperty("user.dir", root.getParentFile().getAbsolutePath());
        MigrationConnections connections = new MigrationConnections();
        SchemaSqlTransfer service = new SchemaSqlTransfer(connections, new MigrationMetadata(), new TableFileTransfer(connections));
        for (String type : Arrays.asList("DM", "ORACLE")) {
            DataSource source = source(type);
            String table = "JD_QA_NATIVE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            String qualified = MigrationDialect.qualified(source.getUserName(), table);
            try {
                try (Connection connection = connections.open(source); Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE " + qualified + " (\"ID\" NUMBER(10,0), \"NOTE\" VARCHAR2(50), CONSTRAINT " + MigrationDialect.quote(table + "_PK") + " PRIMARY KEY (\"ID\"))");
                    statement.execute("CREATE INDEX " + MigrationDialect.quote(table + "_IX") + " ON " + qualified + " (\"NOTE\")");
                }
                SchemaSqlTransfer.Options options = new SchemaSqlTransfer.Options(); options.indexes = true; options.constraints = true;
                byte[] bytes = service.exportSql(source, source.getUserName(), Collections.singletonList(table), options);
                String script = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(type, script.contains("JDDBMS-BEGIN INDEX")); assertTrue(type, script.contains("JDDBMS-BEGIN CONSTRAINT"));
                drop(connections, source, qualified, "ORACLE".equals(type));
                assertEquals(0, service.importSql(source, source.getUserName(), bytes, Collections.singletonList(table), options));
                try (Connection connection = connections.open(source); PreparedStatement query = connection.prepareStatement("SELECT CONSTRAINT_NAME FROM ALL_CONSTRAINTS WHERE OWNER=? AND TABLE_NAME=? AND CONSTRAINT_TYPE='P'")) {
                    query.setString(1, source.getUserName()); query.setString(2, table);
                    try (ResultSet result = query.executeQuery()) { assertTrue(type, result.next()); }
                }
            } finally { drop(connections, source, qualified, "ORACLE".equals(type)); }
        }
    }

    @Test public void schemaSqlIncludesDependentViewProcedureAndTrigger() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_MIGRATION_LIVE")));
        File root = new File(System.getProperty("user.dir"));
        if ("jd-dbms-bisnuess".equals(root.getName())) System.setProperty("user.dir", root.getParentFile().getAbsolutePath());
        MigrationConnections connections = new MigrationConnections();
        SchemaSqlTransfer service = new SchemaSqlTransfer(connections, new MigrationMetadata(), new TableFileTransfer(connections));
        for (String type : Arrays.asList("DM", "ORACLE")) {
            DataSource source = source(type); String schema = source.getUserName();
            String base = "JD_QA_OBJECT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            String table = MigrationDialect.qualified(schema, base), view = MigrationDialect.qualified(schema, base + "_V");
            String procedure = MigrationDialect.qualified(schema, base + "_P"), trigger = MigrationDialect.qualified(schema, base + "_T");
            try {
                create(connections, source, table);
                try (Connection connection = connections.open(source); Statement statement = connection.createStatement()) {
                    statement.execute("CREATE VIEW " + view + " AS SELECT \"ID\" FROM " + table);
                    statement.execute("CREATE OR REPLACE PROCEDURE " + procedure + " AS v_count NUMBER; BEGIN SELECT COUNT(*) INTO v_count FROM " + table + "; END;");
                    statement.execute("CREATE OR REPLACE TRIGGER " + trigger + " BEFORE INSERT ON " + table + " FOR EACH ROW BEGIN NULL; END;");
                }
                SchemaSqlTransfer.Options options = new SchemaSqlTransfer.Options(); options.views = true; options.procedures = true; options.triggers = true;
                byte[] packageFile = service.exportSql(source, schema, Collections.singletonList(base), options);
                String script = new String(packageFile, java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(type, script.contains("JDDBMS-BEGIN VIEW")); assertTrue(type, script.contains("JDDBMS-BEGIN PROCEDURE")); assertTrue(type, script.contains("JDDBMS-BEGIN TRIGGER"));
                dropObject(connections, source, "TRIGGER", trigger); dropObject(connections, source, "PROCEDURE", procedure); dropObject(connections, source, "VIEW", view);
                drop(connections, source, table, "ORACLE".equals(type));
                assertEquals(0, service.importSql(source, schema, packageFile, Collections.singletonList(base), options));
                try (Connection connection = connections.open(source); PreparedStatement query = connection.prepareStatement("SELECT OBJECT_TYPE FROM ALL_OBJECTS WHERE OWNER=? AND OBJECT_NAME=?")) {
                    for (String name : Arrays.asList(base + "_V", base + "_P", base + "_T")) {
                        query.setString(1, schema); query.setString(2, name);
                        try (ResultSet result = query.executeQuery()) { assertTrue(type + ":" + name, result.next()); }
                    }
                }
            } finally {
                dropObject(connections, source, "TRIGGER", trigger); dropObject(connections, source, "PROCEDURE", procedure); dropObject(connections, source, "VIEW", view);
                drop(connections, source, table, "ORACLE".equals(type));
            }
        }
    }

    private static void dropObject(MigrationConnections connections, DataSource source, String type, String name) {
        try (Connection connection = connections.open(source); Statement statement = connection.createStatement()) { statement.execute("DROP " + type + " " + name); }
        catch (Exception ignored) { }
    }

    private static void verifySchemaTransfer(SchemaSqlTransfer service, MigrationConnections connections, DataSource source, DataSource target) throws Exception {
        String table = "JD_QA_SCHEMA_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String from = MigrationDialect.qualified(source.getUserName(), table), to = MigrationDialect.qualified(target.getUserName(), table);
        try {
            create(connections, source, from);
            try (Connection connection = connections.open(source); PreparedStatement insert = connection.prepareStatement("INSERT INTO " + from + " (\"ID\",\"NOTE\",\"PAYLOAD\") VALUES (?,?,?)")) {
                insert.setInt(1, 21); insert.setString(2, "模式 SQL 测试"); insert.setBytes(3, new byte[]{4, 5}); insert.executeUpdate();
            }
            byte[] script = service.exportSql(source, source.getUserName(), Collections.singletonList(table), SchemaSqlTransfer.Options.fullExport());
            String sql = new String(script, java.nio.charset.StandardCharsets.UTF_8);
            assertTrue("模式导出必须包含数据段", sql.contains("JDDBMS-BEGIN DATA"));
            assertTrue("模式导出必须包含插入语句", sql.contains("INSERT INTO "));
            assertEquals(Collections.singletonList(table), service.preview(script).tables);
            assertEquals(0, service.importSql(target, target.getUserName(), script, Collections.singletonList(table), new SchemaSqlTransfer.Options()));
            try (Connection connection = connections.open(target); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + to)) {
                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
            }
            drop(connections, target, to, "ORACLE".equals(target.getType()));
            SchemaSqlTransfer.Options withData = new SchemaSqlTransfer.Options(); withData.dataRows = true;
            assertEquals(1, service.importSql(target, target.getUserName(), script, Collections.singletonList(table), withData));
            try (Connection connection = connections.open(target); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT \"ID\",\"NOTE\",\"PAYLOAD\" FROM " + to)) {
                assertTrue(rows.next()); assertEquals(21, rows.getInt(1)); assertEquals("模式 SQL 测试", rows.getString(2)); assertArrayEquals(new byte[]{4, 5}, rows.getBytes(3)); assertFalse(rows.next());
            }
        } finally { drop(connections, source, from, "ORACLE".equals(source.getType())); drop(connections, target, to, "ORACLE".equals(target.getType())); }
    }

    static DataSource source(String type) throws Exception {
        MigrationLiveTest fixture = new MigrationLiveTest();
        String prefix = "JDDBMS_TEST_" + type + "_";
        String url = System.getenv(prefix + "URL");
        if (url == null || url.isEmpty()) return fixture.environment(type);
        DataSource source = new DataSource(); source.setId(type.equals("DM") ? -1001L : -1002L);
        source.setType(type); source.setDriverConfig(fixture.driver(type));
        source.setUrl(url); source.setUserName(System.getenv(prefix + "USERNAME")); source.setPassword(System.getenv(prefix + "PASSWORD"));
        return source;
    }

    private static void create(MigrationConnections connections, DataSource source, String name) throws Exception {
        try (Connection connection = connections.open(source); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + name + " (\"ID\" NUMBER(10,0), \"NOTE\" VARCHAR2(120), \"PAYLOAD\" BLOB, \"AMOUNT\" NUMBER(12,2), \"CREATED_AT\" TIMESTAMP(6))");
        }
    }

    private static void drop(MigrationConnections connections, DataSource source, String name, boolean oracle) {
        try (Connection connection = connections.open(source); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + name + (oracle ? " PURGE" : ""));
        } catch (Exception ignored) { }
    }

    private static void verifyTransfer(TableFileTransfer service, MigrationConnections connections, DataSource source,
            String sourceSchema, DataSource target, String targetSchema, String table, TableFileTransfer.Format format) throws Exception {
        String from = MigrationDialect.qualified(sourceSchema, table), to = MigrationDialect.qualified(targetSchema, table);
        try (Connection connection = connections.open(source); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + from);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + from + " (\"ID\",\"NOTE\",\"PAYLOAD\",\"AMOUNT\",\"CREATED_AT\") VALUES (?,?,?,?,?)")) {
                insert.setInt(1, 7); insert.setString(2, "中文,换行\n'quoted'"); insert.setBytes(3, new byte[]{1, 2, 3});
                insert.setBigDecimal(4, new java.math.BigDecimal("12.50")); insert.setTimestamp(5, Timestamp.valueOf("2026-10-07 12:34:56")); insert.executeUpdate();
            }
        }
        try (Connection connection = connections.open(target); Statement statement = connection.createStatement()) { statement.execute("DELETE FROM " + to); }
        byte[] file = service.exportFile(source.getId(), sourceSchema, table, format.name());
        assertTrue(format + " empty file", file.length > 0);
        int count = service.importFile(target.getId(), targetSchema, table, format.name(), new MockMultipartFile("file", table + "." + format.extension, format.mime, file));
        assertEquals(format.name(), 1, count);
        try (Connection connection = connections.open(target); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT \"ID\",\"NOTE\",\"PAYLOAD\",\"AMOUNT\",\"CREATED_AT\" FROM " + to)) {
            assertTrue(format.name(), result.next()); assertEquals(7, result.getInt(1)); assertEquals("中文,换行\n'quoted'", result.getString(2));
            assertArrayEquals(new byte[]{1, 2, 3}, result.getBytes(3)); assertEquals(0, new java.math.BigDecimal("12.50").compareTo(result.getBigDecimal(4)));
            assertEquals(Timestamp.valueOf("2026-10-07 12:34:56"), result.getTimestamp(5)); assertFalse(result.next());
        }
    }
}
