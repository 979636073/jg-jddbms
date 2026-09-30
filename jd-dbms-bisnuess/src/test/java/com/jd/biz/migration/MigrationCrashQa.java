package com.jd.biz.migration;

import com.jd.biz.domain.api.model.DataSource;
import java.sql.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

/** Run in a dedicated disposable JVM/container; a hard kill must not run cleanup in that JVM. */
public class MigrationCrashQa {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[1].matches("[A-F0-9]{8}")) throw new IllegalArgumentException("QA mode and exact random token required");
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);
        DataSource dm = new MigrationLiveTest().environment("DM"); MigrationConnections raw = new MigrationConnections();
        String prefix = "JQA_CR_" + args[1], store = prefix + "_M", source = prefix + "_S", target = prefix + "_T";
        try (Connection database = raw.open(dm)) {
            if ("list-fault".equals(args[0])) {
                try (PreparedStatement query = database.prepareStatement("SELECT OBJECT_NAME, CREATED FROM ALL_OBJECTS WHERE OWNER=? AND OBJECT_TYPE='TABLE' AND OBJECT_NAME LIKE 'JQA_NET_%' ORDER BY OBJECT_NAME")) {
                    query.setString(1, dm.getUserName()); try (ResultSet rows = query.executeQuery()) { while (rows.next()) System.out.println("QA_FAULT_OBJECT " + rows.getString(1) + " created=" + rows.getTimestamp(2)); }
                }
                return;
            }
            if ("cleanup-fault".equals(args[0])) {
                for (String ending : new String[] { "_A", "_B", "_C", "_S", "_M" }) {
                    String name = "JQA_NET_" + args[1] + ending;
                    if ("TABLE".equals(MigrationMetadata.objectType(database, dm.getUserName(), name))) execute(database, "DROP TABLE " + qualified(dm.getUserName(), name));
                }
                System.out.println("QA_FAULT_CLEANED " + args[1]); return;
            }
            if ("cleanup".equals(args[0])) {
                for (String name : new String[] { target, source, store }) if (MigrationMetadata.objectType(database, dm.getUserName(), name) != null) execute(database, "DROP TABLE " + qualified(dm.getUserName(), name));
                System.out.println("QA_CRASH_CLEANED " + args[1]); return;
            }
            if ("prepare".equals(args[0])) {
                for (String name : new String[] { store, source, target }) assertNull("No existing object may be taken over", MigrationMetadata.objectType(database, dm.getUserName(), name));
                execute(database, "CREATE TABLE " + qualified(dm.getUserName(), store) + " (ID BIGINT IDENTITY(1,1) PRIMARY KEY, GMT_CREATE TIMESTAMP, GMT_MODIFIED TIMESTAMP, DATA_SOURCE_ID BIGINT, DATABASE_NAME VARCHAR(128), SCHEMA_NAME VARCHAR(128), TABLE_NAME VARCHAR(128), DELETED CHAR(1), USER_ID BIGINT, TASK_TYPE VARCHAR(64), TASK_STATUS VARCHAR(64), TASK_PROGRESS VARCHAR(64), TASK_NAME VARCHAR(256), DOWNLOAD_URL VARCHAR(512), CONTENT BLOB)");
                execute(database, "CREATE TABLE " + qualified(dm.getUserName(), source) + " (ID NUMBER(10,0) PRIMARY KEY)");
                for (int i = 1; i <= 200; i++) execute(database, "INSERT INTO " + qualified(dm.getUserName(), source) + " VALUES(" + i + ")");
            } else if (!"recover".equals(args[0])) throw new IllegalArgumentException("Unknown QA mode");
            MigrationConnections connections = new MigrationConnections() { @Override public DataSource authorized(Long id) { return dm; } };
            MigrationService service = new MigrationService(MigrationWorkflowLiveTest.persistentMapper(raw, dm, store), connections, new MigrationMetadata());
            try {
                Long id;
                if ("prepare".equals(args[0])) {
                    Config config = new Config(); config.name = "隔离容器强停 QA"; config.sourceId = 1L; config.targetId = 2L; config.sourceSchema = config.targetSchema = dm.getUserName(); config.rowsPerSecond = 5; config.commitSize = 1; config.parallelism = 2;
                    Mapping mapping = new Mapping(); mapping.source = source; mapping.target = target; config.tables.add(mapping);
                    Task draft = service.save(null, 42L, config), checked = service.precheck(draft.id, 42L); assertEquals(checked.message, "MIG_READY", checked.status);
                    StartRequest request = new StartRequest(); request.fingerprint = checked.assessment.fingerprint; request.offlineConfirmed = true; service.start(draft.id, 42L, request); id = draft.id;
                    long deadline = System.currentTimeMillis() + 60000;
                    while (System.currentTimeMillis() < deadline) { Task running = service.get(id, 42L); if (running.committedRows >= 2) { System.out.println("QA_CRASH_READY " + args[1] + " committed=" + running.committedRows); System.out.flush(); break; } Thread.sleep(100); }
                } else {
                    id = 1L; service.recoverInterrupted(); Task interrupted = service.get(id, 42L);
                    assertTrue(interrupted.status, MigrationService.RESUMABLE.contains(interrupted.status)); assertNotEquals("MIG_COMPLETED", interrupted.status);
                    System.out.println("QA_CRASH_RECOVERED " + args[1] + " status=" + interrupted.status + " committed=" + interrupted.committedRows);
                    Task reviewed = service.reviewResume(id, 42L); assertNotNull(reviewed.message, reviewed.resumeFingerprint);
                    StartRequest request = new StartRequest(); request.fingerprint = reviewed.resumeFingerprint; request.offlineConfirmed = request.reviewConfirmed = true; service.resume(id, 42L, request);
                }
                long deadline = System.currentTimeMillis() + 120000; Task finished;
                do { finished = service.get(id, 42L); if (!MigrationService.ACTIVE.contains(finished.status)) break; Thread.sleep(100); } while (System.currentTimeMillis() < deadline);
                assertEquals(finished.message, "MIG_COMPLETED", finished.status);
                try (Statement query = database.createStatement(); ResultSet rows = query.executeQuery("SELECT COUNT(*), COUNT(DISTINCT ID) FROM " + qualified(dm.getUserName(), target))) {
                    assertTrue(rows.next()); assertEquals(200, rows.getLong(1)); assertEquals(200, rows.getLong(2));
                }
                System.out.println("QA_CRASH_COMPLETE " + args[1] + " full-content verification PASS; exactly 200 unique rows; reused=" + finished.objects.get(0).reusedRows);
            } finally { service.shutdown(); }
        }
    }
    private static void execute(Connection connection, String sql) throws SQLException { try (Statement statement = connection.createStatement()) { statement.execute(sql); } }
}
