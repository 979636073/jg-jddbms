package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.jd.biz.domain.api.model.DataSource;
import org.junit.Test;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

public class MigrationAdvancedLiveTest {
    @Test public void dmToDm() throws Exception { run("DM", "DM"); }
    @Test public void dmToOracle() throws Exception { run("DM", "ORACLE"); }
    @Test public void oracleToOracle() throws Exception { run("ORACLE", "ORACLE"); }
    @Test public void oracleToDm() throws Exception { run("ORACLE", "DM"); }

    private void run(String from, String to) throws Exception {
        MigrationLiveTest environment = new MigrationLiveTest(); DataSource sourceInfo = environment.environment(from), targetInfo = environment.environment(to), storeInfo = environment.environment("DM");
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);
        MigrationConnections raw = new MigrationConnections();
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT), prefix = "JQA_" + token, storeName = prefix + "_TASK";
        Config config = new Config(); config.name = "并发 / 类型 / 表空间 / 无主键重迁移 " + from + "-" + to;
        config.sourceId = 1L; config.targetId = 2L; config.sourceSchema = sourceInfo.getUserName(); config.targetSchema = targetInfo.getUserName();
        config.scope = "DATA"; config.parallelism = 3; config.rowsPerSecond = 20; config.commitSize = 1; config.errorPolicy = "CONTINUE_INDEPENDENT";
        List<String> sourceObjects = new ArrayList<>(), targetObjects = new ArrayList<>(); MigrationService service = null; boolean storeCreated = false;
        try (Connection source = raw.open(sourceInfo); Connection target = raw.open(targetInfo); Connection store = raw.open(storeInfo)) {
            try {
                execute(store, "CREATE TABLE " + qualified(storeInfo.getUserName(), storeName) + " (ID BIGINT IDENTITY(1,1) PRIMARY KEY, GMT_CREATE TIMESTAMP, GMT_MODIFIED TIMESTAMP, DATA_SOURCE_ID BIGINT, DATABASE_NAME VARCHAR(128), SCHEMA_NAME VARCHAR(128), TABLE_NAME VARCHAR(128), DELETED CHAR(1), USER_ID BIGINT, TASK_TYPE VARCHAR(64), TASK_STATUS VARCHAR(64), TASK_PROGRESS VARCHAR(64), TASK_NAME VARCHAR(256), DOWNLOAD_URL VARCHAR(512), CONTENT BLOB)"); storeCreated = true;
                MigrationConnections connections = new MigrationConnections() { @Override public DataSource authorized(Long id) { return id == 1L ? sourceInfo : targetInfo; } };
                service = new MigrationService(MigrationWorkflowLiveTest.persistentMapper(raw, storeInfo, storeName), connections, new MigrationMetadata());
                for (int i = 0; i < 3; i++) {
                    Mapping mapping = new Mapping(); mapping.source = prefix + "_S" + i; mapping.target = prefix + "_T" + i; config.tables.add(mapping);
                    execute(source, "CREATE TABLE " + qualified(config.sourceSchema, mapping.source) + " (ID NUMBER(10,0), AMOUNT NUMBER(12,4))"); sourceObjects.add(mapping.source);
                    execute(target, "CREATE TABLE " + qualified(config.targetSchema, mapping.target) + " (ID NUMBER(10,0), AMOUNT NUMBER(12,2)" + (i == 0 ? ", CHECK(ID<2)" : "") + ")"); targetObjects.add(mapping.target);
                    execute(source, "INSERT INTO " + qualified(config.sourceSchema, mapping.source) + " VALUES(1,12.3456)");
                    execute(source, "INSERT INTO " + qualified(config.sourceSchema, mapping.source) + " VALUES(2,23.4567)");
                    FieldMapping amount = new FieldMapping(); amount.source = amount.target = "AMOUNT"; amount.targetType = "NUMBER(12,2)"; amount.conversionConfirmed = true; mapping.columns.add(amount);
                }
                Task draft = service.save(null, 42L, config), checked = service.precheck(draft.id, 42L);
                assertEquals(JSON.toJSONString(checked.assessment), "MIG_READY", checked.status);
                StartRequest request = new StartRequest(); request.offlineConfirmed = true; request.fingerprint = checked.assessment.fingerprint;
                service.start(draft.id, 42L, request); Task finished = await(service, draft.id);
                assertEquals(finished.message, "MIG_PARTIAL", finished.status); assertEquals(5, finished.committedRows); assertEquals(0, finished.inFlightCommits);
                assertEquals(2, finished.objects.stream().filter(result -> "COMPLETED".equals(result.stage)).count());
                assertEquals("12.35", value(target, "SELECT AMOUNT FROM " + qualified(config.targetSchema, config.tables.get(1).target) + " WHERE ID=1"));
                Task review = service.reviewObject(draft.id, 42L, finished.objects.get(0).source); assertNull("No PK must not silently resume", review.resumeFingerprint);
                Task retry = service.retryDraft(draft.id, 42L, finished.objects.get(0).source); assertEquals(draft.id, retry.parentTaskId); assertEquals(1, retry.config.tables.size());
                List<Map<String, String>> spaces = new MigrationMetadata().tablespaces(target); assertFalse("Selectable target tablespaces", spaces.isEmpty());
                retry.config.targetTablespace = spaces.get(0).get("name");
                for (Mapping mapping : retry.config.tables) { assertTrue(mapping.target.startsWith("JQA_")); targetObjects.add(mapping.target); }
                retry = service.save(retry.id, 42L, retry.config); checked = service.precheck(retry.id, 42L);
                assertEquals(JSON.toJSONString(checked.assessment), "MIG_READY", checked.status);
                request.fingerprint = checked.assessment.fingerprint; service.start(retry.id, 42L, request); Task retried = await(service, retry.id);
                assertEquals(retried.message, "MIG_COMPLETED", retried.status); assertEquals(2, retried.committedRows); assertEquals(draft.id, retried.parentTaskId);
                assertEquals("1", value(target, "SELECT COUNT(*) FROM " + qualified(config.targetSchema, config.tables.get(0).target)));
                assertEquals("2", value(target, "SELECT COUNT(*) FROM " + qualified(config.targetSchema, config.tables.get(1).target)));
                Files.write(Paths.get(System.getProperty("user.dir"), "jd-dbms-bisnuess", "target", "migration-advanced-" + from + "-" + to + ".json"), JSON.toJSONBytes(retried));
                System.out.println("ADVANCED " + from + "-" + to + " PASS: parallelism=3; shared throttle; explicit rounded CAST; no-PK new-target retry; actual tablespace verified; original targets untouched");
            } finally {
                if (service != null) service.shutdown();
                Collections.reverse(targetObjects); for (String name : targetObjects) if (MigrationMetadata.objectType(target, config.targetSchema, name) != null) drop(target, config.targetSchema, name, to, token);
                Collections.reverse(sourceObjects); for (String name : sourceObjects) drop(source, config.sourceSchema, name, from, token);
                if (storeCreated) drop(store, storeInfo.getUserName(), storeName, "DM", token);
            }
        }
    }
    private static Task await(MigrationService service, Long id) throws Exception {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) { Task task = service.get(id, 42L); if (!MigrationService.ACTIVE.contains(task.status)) { Thread.sleep(25); return task; } Thread.sleep(50); }
        throw new IllegalStateException("QA task timed out");
    }
    private static void execute(Connection connection, String sql) throws SQLException { try (Statement statement = connection.createStatement()) { statement.execute(sql); } }
    private static String value(Connection connection, String sql) throws SQLException { try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) { assertTrue(rows.next()); return rows.getBigDecimal(1).stripTrailingZeros().toPlainString(); } }
    private static void drop(Connection connection, String schema, String name, String engine, String token) throws SQLException {
        if (!name.startsWith("JQA_" + token + "_") || !name.matches("[A-Z0-9_]+")) throw new IllegalArgumentException("Not this run's QA object");
        execute(connection, "DROP TABLE " + qualified(schema, name) + ("ORACLE".equals(engine) ? " PURGE" : ""));
    }
}
