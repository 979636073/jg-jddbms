package com.jd.biz.migration;

import com.jd.biz.domain.api.model.DataSource;
import org.junit.Test;
import java.io.File;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import static com.jd.biz.migration.MigrationDialect.qualified;
import static com.jd.biz.migration.MigrationModel.*;
import static org.junit.Assert.*;

/** Opt-in: only uniquely named tables in the dedicated DM/Oracle QA schemas. */
public class MigrationLobLiveTest {
    private static final String DM_SCHEMA = "JQDMO_260926", ORACLE_SCHEMA = "JQORO_260926";

    @Test public void textAndNclobHaveTheSameDmMetadata() throws Exception {
        DataSource dm = dm(); String name = name("M");
        try (Connection connection = new MigrationConnections().open(dm)) {
            assertNull(MigrationMetadata.objectType(connection, DM_SCHEMA, name));
            try {
                execute(connection, "CREATE TABLE " + qualified(DM_SCHEMA, name) + " (TXT TEXT, NAT NCLOB)");
                Map<String, String> types = new HashMap<>();
                try (PreparedStatement query = connection.prepareStatement("SELECT COLUMN_NAME, DATA_TYPE FROM ALL_TAB_COLUMNS WHERE OWNER=? AND TABLE_NAME=?")) {
                    query.setString(1, DM_SCHEMA); query.setString(2, name);
                    try (ResultSet rows = query.executeQuery()) { while (rows.next()) types.put(rows.getString(1), rows.getString(2)); }
                }
                assertEquals("TEXT", types.get("TXT")); assertEquals("TEXT", types.get("NAT"));
            } finally { drop(connection, DM_SCHEMA, name, "DM"); }
            assertNull(MigrationMetadata.objectType(connection, DM_SCHEMA, name));
        }
    }

    @Test public void dmToDm() throws Exception { run("DM", "DM"); }
    @Test public void dmToOracle() throws Exception { run("DM", "ORACLE"); }
    @Test public void oracleToDm() throws Exception { run("ORACLE", "DM"); }
    @Test public void oracleToOracle() throws Exception { run("ORACLE", "ORACLE"); }

    @Test public void provisionOracleLobQuota() throws Exception {
        org.junit.Assume.assumeTrue("1".equals(System.getenv("JDDBMS_MIGRATION_PROVISION_QA")));
        org.junit.Assume.assumeTrue("SYSTEM".equals(System.getenv("JDDBMS_ORACLE_QA_USERNAME")));
        try (Connection admin = new MigrationConnections().open(oracle());
             PreparedStatement query = admin.prepareStatement("SELECT MAX_BYTES FROM DBA_TS_QUOTAS WHERE USERNAME=? AND TABLESPACE_NAME='USERS'")) {
            query.setString(1, ORACLE_SCHEMA);
            long quota = 0;
            try (ResultSet rows = query.executeQuery()) { if (rows.next()) quota = rows.getLong(1); }
            System.out.println("Oracle QA USERS quota bytes before LOB test: " + quota);
            if (quota >= 0 && quota < 64L * 1024 * 1024) {
                execute(admin, "ALTER USER \"" + ORACLE_SCHEMA + "\" QUOTA 64M ON USERS");
            }
        }
    }

    private void run(String fromType, String toType) throws Exception {
            DataSource from = "DM".equals(fromType) ? dm() : oracle(), to = "DM".equals(toType) ? dm() : oracle();
            String fromSchema = schema(fromType), toSchema = schema(toType), sourceName = name("S"), targetName = name("T");
            boolean sourceCreated = false, targetCreated = false;
            try (Connection source = new MigrationConnections().open(from); Connection target = new MigrationConnections().open(to)) {
                assertNull(MigrationMetadata.objectType(source, fromSchema, sourceName));
                assertNull(MigrationMetadata.objectType(target, toSchema, targetName));
                try {
                    String fields = "ID NUMBER(10,0) PRIMARY KEY, BIN BLOB, BODY CLOB, NATIONAL NCLOB"
                            + ("DM".equals(fromType) ? ", PLAIN TEXT" : "");
                    execute(source, "CREATE TABLE " + qualified(fromSchema, sourceName) + " (" + fields + ")"); sourceCreated = true;
                    String text = String.join("", Collections.nCopies(6000, "中文 😀 𠮷 ' 换行\n"));
                    byte[] bytes = new byte[1024 * 1024 + 17]; new Random(17).nextBytes(bytes);
                    try (PreparedStatement insert = source.prepareStatement("INSERT INTO " + qualified(fromSchema, sourceName)
                            + " VALUES (?, ?, ?, ?" + ("DM".equals(fromType) ? ", ?" : "") + ")")) {
                        insert.setInt(1, 1); insert.setBinaryStream(2, new java.io.ByteArrayInputStream(bytes), bytes.length);
                        insert.setCharacterStream(3, new StringReader(text)); insert.setNCharacterStream(4, new StringReader(text));
                        if ("DM".equals(fromType)) insert.setCharacterStream(5, new StringReader(text));
                        assertEquals(1, insert.executeUpdate());
                    }
                    execute(source, "INSERT INTO " + qualified(fromSchema, sourceName) + " (ID) VALUES (2)");
                    Config config = new Config(); config.name = "LOB QA"; config.sourceId = 1L; config.targetId = 2L;
                    config.sourceSchema = fromSchema; config.targetSchema = toSchema;
                    Mapping mapping = new Mapping(); mapping.source = sourceName; mapping.target = targetName; config.tables.add(mapping);
                    if ("DM".equals(fromType)) {
                        FieldMapping national = new FieldMapping(); national.source = "NATIONAL"; national.target = "NATIONAL";
                        national.targetType = "NCLOB"; national.conversionConfirmed = true; mapping.columns.add(national);
                    }
                    MigrationMetadata metadata = new MigrationMetadata(); Assessment report = metadata.assess(source, target, config, fromType, toType);
                    assertTrue(fromType + " → " + toType + ": " + report.tables.get(0).blockers, report.isReady());
                    TablePlan table = report.tables.get(0);
                    if ("DM".equals(fromType)) {
                        assertEquals("CLOB", column(table, "PLAIN").targetType);
                        assertEquals("NCLOB", column(table, "NATIONAL").targetType);
                    }
                    target.setAutoCommit(false);
                    execute(target, table.targetDdl); targetCreated = true;
                    metadata.verifyCreated(target, config, table, toType);
                    config.scope = "DATA"; config.conflict = "APPEND";
                    Assessment dataOnly = metadata.assess(source, target, config, fromType, toType);
                    assertTrue("Existing LOB target must be compatible: " + dataOnly.tables.get(0).blockers, dataOnly.isReady());
                    config.scope = "ALL"; config.conflict = "STOP";
                    Task task = new Task(); task.config = config; MigrationTransfer.Control control = new MigrationTransfer.Control(task, () -> { });
                    ObjectResult result = new ObjectResult(); MigrationTransfer transfer = new MigrationTransfer();
                    transfer.copy(source, target, config, table, result, control);
                    transfer.verify(source, target, config, table, result, control);
                    assertEquals(2, result.committedRows); assertEquals("FULL_CONTENT_HASH_PASS", result.verification);
                } finally {
                    target.rollback();
                    if (targetCreated) drop(target, toSchema, targetName, toType);
                    if (sourceCreated) drop(source, fromSchema, sourceName, fromType);
                }
                assertNull(MigrationMetadata.objectType(source, fromSchema, sourceName));
                assertNull(MigrationMetadata.objectType(target, toSchema, targetName));
            }
    }

    private static Column column(TablePlan table, String name) { return table.columns.stream().filter(field -> name.equals(field.name)).findFirst().get(); }
    private static String schema(String type) { return "DM".equals(type) ? DM_SCHEMA : ORACLE_SCHEMA; }
    private static String name(String part) { return "JD_QA_LOB_" + part + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT); }
    private static void execute(Connection connection, String sql) throws SQLException { try (Statement statement = connection.createStatement()) { statement.execute(sql); } }
    private static void drop(Connection connection, String schema, String name, String type) throws SQLException {
        if (!name.matches("JD_QA_LOB_[MST][A-F0-9]{8}") || !schema.equals(schema(type))) throw new IllegalArgumentException("Not an owned QA object");
        execute(connection, "DROP TABLE " + qualified(schema, name) + ("ORACLE".equals(type) ? " PURGE" : ""));
    }
    private static DataSource dm() {
        org.junit.Assume.assumeTrue("1".equals(System.getenv("JDDBMS_MIGRATION_LIVE")));
        File root = new File(System.getProperty("user.dir"));
        if (root.getName().equals("jd-dbms-bisnuess")) System.setProperty("user.dir", root.getParent());
        DataSource dm = new DataSource(); dm.setType("DM"); dm.setDriverConfig(new MigrationLiveTest().driver("DM"));
        dm.setUrl(MigrationLiveTest.hostUrl(System.getenv("JDDBMS_META_DB_URL")));
        dm.setUserName(System.getenv("JDDBMS_META_DB_USERNAME")); dm.setPassword(System.getenv("JDDBMS_META_DB_PASSWORD"));
        return dm;
    }
    private static DataSource oracle() throws Exception {
        return new MigrationLiveTest().environment("ORACLE");
    }
}
