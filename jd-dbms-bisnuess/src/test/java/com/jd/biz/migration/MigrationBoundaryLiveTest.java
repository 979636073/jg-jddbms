package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.jd.biz.domain.api.model.DataSource;
import org.junit.Test;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

/** Real boundary cases use only random, owned QA objects/users. No business grants or quotas are changed. */
public class MigrationBoundaryLiveTest {
    @Test public void oracleObjectAttributeToDm() throws Exception { attribute("DM"); }
    @Test public void oracleObjectAttributeToOracle() throws Exception { attribute("ORACLE"); }

    private void attribute(String to) throws Exception {
        MigrationLiveTest environment = new MigrationLiveTest(); DataSource from = environment.environment("ORACLE"), targetInfo = environment.environment(to), storeInfo = environment.environment("DM");
        quiet(); MigrationConnections raw = new MigrationConnections(); String prefix = prefix(), sourceName = prefix + "_S", targetName = prefix + "_T", typeName = prefix + "_TYPE", storeName = prefix + "_M";
        boolean typeCreated = false, sourceCreated = false, storeCreated = false; MigrationService service = null;
        try (Connection source = raw.open(from); Connection target = raw.open(targetInfo); Connection store = raw.open(storeInfo)) {
            try {
                execute(source, "CREATE TYPE " + qualified(from.getUserName(), typeName) + " AS OBJECT (AMOUNT NUMBER(12,4), NOTE VARCHAR2(20))"); typeCreated = true;
                execute(source, "CREATE TABLE " + qualified(from.getUserName(), sourceName) + " (ID NUMBER(10,0) PRIMARY KEY, PAYLOAD " + qualified(from.getUserName(), typeName) + ")"); sourceCreated = true;
                execute(source, "INSERT INTO " + qualified(from.getUserName(), sourceName) + " VALUES (1," + qualified(from.getUserName(), typeName) + "(12.3456,'not projected'))");
                execute(source, "INSERT INTO " + qualified(from.getUserName(), sourceName) + " VALUES (2,NULL)");
                createStore(store, storeInfo, storeName); storeCreated = true;
                service = service(raw, from, targetInfo, storeInfo, storeName);
                Config config = config(from, targetInfo, sourceName, targetName); Task draft = service.save(null, 42L, config), blocked = service.precheck(draft.id, 42L);
                assertEquals("Unmapped UDT must not be silently serialized", "MIG_BLOCKED", blocked.status);
                List<Column> editable = new MigrationMetadata().editableColumns(source, from.getUserName(), sourceName, "ORACLE");
                assertTrue(editable.stream().anyMatch(column -> "PAYLOAD".equals(column.name) && "UNSUPPORTED".equals(column.kind)));
                FieldMapping field = new FieldMapping(); field.source = "PAYLOAD"; field.target = "AMOUNT"; field.targetType = "NUMBER(12,2)"; field.sourceAttribute = "AMOUNT"; field.conversionConfirmed = true; config.tables.get(0).columns.add(field);
                service.save(draft.id, 42L, config); Task checked = service.precheck(draft.id, 42L); assertEquals(JSON.toJSONString(checked.assessment), "MIG_READY", checked.status);
                Task finished = run(service, checked); assertEquals(finished.message, "MIG_COMPLETED", finished.status); assertEquals(2, finished.committedRows);
                try (Statement query = target.createStatement(); ResultSet rows = query.executeQuery("SELECT AMOUNT FROM " + qualified(targetInfo.getUserName(), targetName) + " ORDER BY ID")) {
                    assertTrue(rows.next()); assertEquals("12.35", rows.getBigDecimal(1).stripTrailingZeros().toPlainString()); assertTrue(rows.next()); assertNull(rows.getObject(1)); assertFalse(rows.next());
                }
                System.out.println("UDT ATTRIBUTE ORACLE-" + to + " PASS: explicit scalar projection, NULL and rounded converted-content verification; unmapped UDT blocked");
            } finally {
                if (service != null) service.shutdown(); dropIfPresent(target, targetInfo, targetName);
                if (sourceCreated) dropIfPresent(source, from, sourceName);
                if (typeCreated) execute(source, "DROP TYPE " + qualified(from.getUserName(), typeName));
                if (storeCreated) dropIfPresent(store, storeInfo, storeName);
            }
        }
    }

    @Test public void missingPrivilegeAndZeroQuotaFailThenNewTargetRetryCompletes() throws Exception {
        MigrationLiveTest environment = new MigrationLiveTest(); DataSource sourceInfo = environment.environment("ORACLE"), storeInfo = environment.environment("DM"); quiet();
        MigrationConnections raw = new MigrationConnections(); DataSource adminInfo = MigrationQaSetupTest.oracleAdministrator(environment, sourceInfo, raw);
        String prefix = prefix(), user = prefix + "_U", sourceName = prefix + "_S", targetName = prefix + "_T", storeName = prefix + "_M"; boolean userCreated = false, sourceCreated = false, storeCreated = false; MigrationService service = null;
        DataSource targetInfo = JSON.parseObject(JSON.toJSONString(sourceInfo), DataSource.class); targetInfo.setUserName(user); targetInfo.setPassword("Qa" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        try (Connection admin = raw.open(adminInfo); Connection source = raw.open(sourceInfo); Connection store = raw.open(storeInfo)) {
            try {
                String tablespace;
                try (Statement query = source.createStatement(); ResultSet rows = query.executeQuery("SELECT DEFAULT_TABLESPACE FROM USER_USERS")) { assertTrue(rows.next()); tablespace = rows.getString(1); }
                try { execute(admin, "CREATE USER " + quote(user) + " IDENTIFIED BY " + quote(targetInfo.getPassword()) + " DEFAULT TABLESPACE " + quote(tablespace) + " QUOTA 0 ON " + quote(tablespace)); }
                catch (SQLException error) { throw new IllegalStateException("QA user creation failed, code=" + error.getErrorCode()); }
                userCreated = true;
                execute(admin, "GRANT CREATE SESSION TO " + quote(user));
                execute(source, "CREATE TABLE " + qualified(sourceInfo.getUserName(), sourceName) + " (ID NUMBER(10,0) PRIMARY KEY)"); sourceCreated = true;
                execute(source, "INSERT INTO " + qualified(sourceInfo.getUserName(), sourceName) + " VALUES (1)");
                createStore(store, storeInfo, storeName); storeCreated = true; service = service(raw, sourceInfo, targetInfo, storeInfo, storeName);
                Task draft = service.save(null, 42L, config(sourceInfo, targetInfo, sourceName, targetName)), checked = service.precheck(draft.id, 42L);
                assertEquals(JSON.toJSONString(checked.assessment), "MIG_BLOCKED", checked.status); assertTrue(JSON.toJSONString(checked.assessment).contains("CREATE TABLE"));
                execute(admin, "GRANT CREATE TABLE TO " + quote(user)); checked = service.precheck(draft.id, 42L); assertEquals(JSON.toJSONString(checked.assessment), "MIG_READY", checked.status);
                Task failed = run(service, checked); assertEquals(failed.message, "MIG_FAILED", failed.status); assertEquals(0, failed.committedRows); assertEquals(0, failed.inFlightCommits);
                assertTrue("Actual quota failure must be reported: " + failed.message, (failed.message + JSON.toJSONString(failed.objects)).matches("(?si).*(代码 1950|代码 1536).*$"));
                execute(admin, "ALTER USER " + quote(user) + " QUOTA 2M ON " + quote(tablespace));
                Task retry = service.retryDraft(draft.id, 42L, null); assertEquals(draft.id, retry.parentTaskId);
                checked = service.precheck(retry.id, 42L); assertEquals(JSON.toJSONString(checked.assessment), "MIG_READY", checked.status);
                Task finished = run(service, checked); assertEquals(finished.message, "MIG_COMPLETED", finished.status); assertEquals(1, finished.committedRows);
                System.out.println("ORACLE PERMISSION / ZERO QUOTA PASS: privilege precheck blocked; quota execution failed with zero committed rows; provisioned new-target retry completed");
                service.shutdown(); service = service(raw, adminInfo, adminInfo, storeInfo, storeName);
                execute(admin, "CREATE TABLE " + qualified(user, prefix + "_S2") + " (ID NUMBER(10,0) PRIMARY KEY)");
                execute(admin, "INSERT INTO " + qualified(user, prefix + "_S2") + " VALUES(2)");
                Config multi = config(sourceInfo, targetInfo, sourceName, prefix + "_T1");
                Mapping second = new Mapping(); second.source = prefix + "_S2"; second.sourceSchema = user; second.target = prefix + "_T2"; second.targetSchema = sourceInfo.getUserName(); multi.tables.add(second);
                Task multiDraft = service.save(null, 42L, multi); checked = service.precheck(multiDraft.id, 42L); assertEquals(JSON.toJSONString(checked.assessment), "MIG_READY", checked.status);
                finished = run(service, checked); assertEquals(finished.message, "MIG_COMPLETED", finished.status); assertEquals(2, finished.committedRows);
                try (Statement query = admin.createStatement(); ResultSet rows = query.executeQuery("SELECT ID FROM " + qualified(sourceInfo.getUserName(), prefix + "_T2"))) { assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertFalse(rows.next()); }
                System.out.println("REAL MULTI-SCHEMA PASS: two source schemas, two explicit target schemas, whole-task content verification, exclusively dedicated QA objects");
            } finally {
                if (service != null) service.shutdown();
                dropNumbered(source, sourceInfo, prefix + "_T2");
                if (userCreated) execute(admin, "DROP USER " + quote(user) + " CASCADE");
                if (sourceCreated) dropIfPresent(source, sourceInfo, sourceName);
                if (storeCreated) dropIfPresent(store, storeInfo, storeName);
            }
        }
    }

    @Test public void parallelLobCancellationJoinsAllWritersBeforeFinalStatus() throws Exception {
        MigrationLiveTest environment = new MigrationLiveTest(); DataSource dm = environment.environment("DM"); quiet(); MigrationConnections raw = new MigrationConnections();
        String prefix = prefix(), storeName = prefix + "_M"; boolean storeCreated = false; MigrationService service = null; List<String> sources = new ArrayList<>(), targets = new ArrayList<>();
        try (Connection database = raw.open(dm)) {
            try {
                createStore(database, dm, storeName); storeCreated = true; service = service(raw, dm, dm, dm, storeName);
                Config config = config(dm, dm, prefix + "_S0", prefix + "_T0"); config.tables.clear(); config.parallelism = 3; config.commitSize = 1; config.rowsPerSecond = 3;
                for (int i = 0; i < 3; i++) {
                    Mapping mapping = new Mapping(); mapping.source = prefix + "_S" + i; mapping.target = prefix + "_T" + i; config.tables.add(mapping); targets.add(mapping.target);
                    execute(database, "CREATE TABLE " + qualified(dm.getUserName(), mapping.source) + " (ID NUMBER(10,0) PRIMARY KEY" + (i < 2 ? ", PAYLOAD BLOB" : "") + ")"); sources.add(mapping.source);
                    try (PreparedStatement insert = database.prepareStatement("INSERT INTO " + qualified(dm.getUserName(), mapping.source) + " VALUES (?" + (i < 2 ? ",?" : "") + ")")) {
                        for (int row = 1; row <= 5; row++) { insert.setInt(1, row); if (i < 2) insert.setBytes(2, new byte[128 * 1024]); insert.executeUpdate(); }
                    }
                }
                Task draft = service.save(null, 42L, config), checked = service.precheck(draft.id, 42L); assertEquals(JSON.toJSONString(checked.assessment), "MIG_READY", checked.status);
                StartRequest request = new StartRequest(); request.fingerprint = checked.assessment.fingerprint; request.offlineConfirmed = true; service.start(draft.id, 42L, request);
                long deadline = System.currentTimeMillis() + 30000; Task running;
                do { running = service.get(draft.id, 42L); if (running.committedRows >= 3) break; Thread.sleep(50); } while (System.currentTimeMillis() < deadline);
                assertTrue(running.committedRows >= 3); service.cancel(draft.id, 42L); Task cancelled;
                do { cancelled = service.get(draft.id, 42L); if (!MigrationService.ACTIVE.contains(cancelled.status)) break; Thread.sleep(50); } while (System.currentTimeMillis() < deadline);
                assertEquals(cancelled.message, "MIG_CANCELLED", cancelled.status); assertEquals(0, cancelled.inFlightCommits);
                long count = countTargets(database, dm, targets); assertEquals(cancelled.committedRows, count); assertTrue(count > 0 && count < 15);
                Thread.sleep(400); assertEquals("No late writer may keep writing after terminal cancellation", count, countTargets(database, dm, targets));
                assertEquals("MIG_CANCELLED", service.get(draft.id, 42L).status);
                System.out.println("PARALLEL LOB CANCEL PASS: parallelism=3, serialized LOB slot, shared rate, all writers joined, exact committed-row counts, no late writes");
            } finally {
                if (service != null) service.shutdown();
                for (String name : targets) dropNumbered(database, dm, name); for (String name : sources) dropNumbered(database, dm, name);
                if (storeCreated) dropIfPresent(database, dm, storeName);
            }
        }
    }

    private static long countTargets(Connection connection, DataSource info, List<String> names) throws SQLException { long count = 0; for (String name : names) if (MigrationMetadata.objectType(connection, info.getUserName(), name) != null) try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT COUNT(*) FROM " + qualified(info.getUserName(), name))) { assertTrue(rows.next()); count += rows.getLong(1); } return count; }
    private static void dropNumbered(Connection connection, DataSource info, String name) throws SQLException { if (!name.matches("JQA_B_[A-F0-9]{8}_[ST][0-2]")) throw new IllegalArgumentException("Not an owned QA object"); if (MigrationMetadata.objectType(connection, info.getUserName(), name) != null) execute(connection, "DROP TABLE " + qualified(info.getUserName(), name) + ("ORACLE".equals(info.getType()) ? " PURGE" : "")); }

    private static String prefix() { return "JQA_B_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT); }
    private static void quiet() { ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN); }
    private static Config config(DataSource source, DataSource target, String from, String to) { Config config = new Config(); config.name = "边界条件 QA"; config.sourceId = 1L; config.targetId = 2L; config.sourceSchema = source.getUserName(); config.targetSchema = target.getUserName(); Mapping mapping = new Mapping(); mapping.source = from; mapping.target = to; config.tables.add(mapping); return config; }
    private static MigrationService service(MigrationConnections raw, DataSource source, DataSource target, DataSource store, String storeName) throws Exception { return new MigrationService(MigrationWorkflowLiveTest.persistentMapper(raw, store, storeName), new MigrationConnections() { @Override public DataSource authorized(Long id) { return id == 1L ? source : target; } }, new MigrationMetadata()); }
    private static Task run(MigrationService service, Task checked) throws Exception {
        StartRequest request = new StartRequest(); request.offlineConfirmed = true; request.fingerprint = checked.assessment.fingerprint; service.start(checked.id, 42L, request);
        long deadline = System.currentTimeMillis() + 30000;
        do { Task task = service.get(checked.id, 42L); if (!MigrationService.ACTIVE.contains(task.status)) return task; Thread.sleep(50); } while (System.currentTimeMillis() < deadline);
        throw new IllegalStateException("QA task timed out");
    }
    private static void createStore(Connection connection, DataSource info, String name) throws SQLException { execute(connection, "CREATE TABLE " + qualified(info.getUserName(), name) + " (ID BIGINT IDENTITY(1,1) PRIMARY KEY, GMT_CREATE TIMESTAMP, GMT_MODIFIED TIMESTAMP, DATA_SOURCE_ID BIGINT, DATABASE_NAME VARCHAR(128), SCHEMA_NAME VARCHAR(128), TABLE_NAME VARCHAR(128), DELETED CHAR(1), USER_ID BIGINT, TASK_TYPE VARCHAR(64), TASK_STATUS VARCHAR(64), TASK_PROGRESS VARCHAR(64), TASK_NAME VARCHAR(256), DOWNLOAD_URL VARCHAR(512), CONTENT BLOB)"); }
    private static void execute(Connection connection, String sql) throws SQLException { try (Statement statement = connection.createStatement()) { statement.execute(sql); } }
    private static void dropIfPresent(Connection connection, DataSource info, String name) throws SQLException { if (!name.matches("JQA_B_[A-F0-9]{8}_[STM]")) throw new IllegalArgumentException("Not an owned QA object"); if (MigrationMetadata.objectType(connection, info.getUserName(), name) != null) execute(connection, "DROP TABLE " + qualified(info.getUserName(), name) + ("ORACLE".equals(info.getType()) ? " PURGE" : "")); }
}
