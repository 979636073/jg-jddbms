package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.jd.biz.domain.api.model.DataSource;
import org.junit.Test;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.management.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

/** Opt-in bounded baseline, not a claim about unspecified commercial maximum scale or SLA. */
public class MigrationPerformanceLiveTest {
    @Test public void tenThousandRowsAndEightMiBLob() throws Exception {
        DataSource info = new MigrationLiveTest().environment("DM"); MigrationConnections connections = new MigrationConnections();
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        Config config = new Config(); config.name = "有界性能基线"; config.sourceId = config.targetId = info.getId(); config.sourceSchema = config.targetSchema = info.getUserName();
        Mapping mapping = new Mapping(); mapping.source = "JD_QA_P0929_S" + token; mapping.target = "JD_QA_P0929_T" + token; config.tables.add(mapping);
        boolean sourceCreated = false, targetCreated = false;
        try (Connection source = connections.open(info); Connection target = connections.open(info)) {
            try {
                assertNull(MigrationMetadata.objectType(source, config.sourceSchema, mapping.source)); assertNull(MigrationMetadata.objectType(target, config.targetSchema, mapping.target));
                String sourceName = qualified(config.sourceSchema, mapping.source);
                try (Statement ddl = source.createStatement()) { ddl.execute("CREATE TABLE " + sourceName + " (ID NUMBER(12,0) PRIMARY KEY, NOTE VARCHAR(256), PAYLOAD BLOB, DETAIL CLOB)"); sourceCreated = true; }
                source.setAutoCommit(false);
                try (PreparedStatement insert = source.prepareStatement("INSERT INTO " + sourceName + " VALUES (?, ?, ?, ?)")) {
                    insert.setInt(1, 1); insert.setString(2, "8MiB 流式二进制与长文本"); long length = 8 * 1024 * 1024L;
                    insert.setBinaryStream(3, new InputStream() { long index; @Override public int read() { return index < length ? (int) ((index++ * 31) & 255) : -1; }
                        @Override public int read(byte[] bytes, int offset, int count) { int size = (int) Math.min(count, length - index); if (size == 0) return -1; for (int i = 0; i < size; i++) bytes[offset + i] = (byte) ((index++ * 31) & 255); return size; } }, length);
                    String text = String.join("", Collections.nCopies(200000, "长文本中文\n")); insert.setCharacterStream(4, new StringReader(text)); insert.executeUpdate();
                    for (int id = 2; id <= 10001; id++) { insert.setInt(1, id); insert.setString(2, "性能基线-" + id); insert.setNull(3, Types.BLOB); insert.setNull(4, Types.CLOB); insert.addBatch(); if (id % 500 == 0) { insert.executeBatch(); insert.clearBatch(); } }
                    insert.executeBatch(); source.commit();
                }
                MigrationMetadata metadata = new MigrationMetadata(); Assessment report = metadata.assess(source, target, config, "DM", "DM"); assertTrue(JSON.toJSONString(report), report.isReady()); TablePlan table = report.tables.get(0);
                target.setAutoCommit(false); try (Statement ddl = target.createStatement()) { ddl.execute(table.targetDdl); targetCreated = true; }
                metadata.verifyCreated(target, config, table, "DM");
                Task task = new Task(); task.config = config; ObjectResult result = new ObjectResult(); MigrationTransfer transfer = new MigrationTransfer(); MigrationTransfer.Control control = new MigrationTransfer.Control(task, () -> { });
                for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) if (pool.getType() == MemoryType.HEAP) pool.resetPeakUsage();
                long start = System.nanoTime(); transfer.baseline(source, config, table, result, control);
                long copyStart = System.nanoTime(); transfer.copy(source, target, config, table, result, control); long copyEnd = System.nanoTime(); transfer.verify(source, target, config, table, result, control); long end = System.nanoTime();
                assertEquals(10001, result.committedRows); assertEquals("FULL_CONTENT_HASH_PASS", result.verification);
                Map<String, Object> metrics = new LinkedHashMap<>(); metrics.put("architecture", System.getProperty("os.arch")); metrics.put("os", System.getProperty("os.name")); metrics.put("java", System.getProperty("java.version"));
                metrics.put("sourceVersion", report.sourceVersion); metrics.put("targetVersion", report.targetVersion); metrics.put("rows", result.committedRows); metrics.put("blobBytes", 8 * 1024 * 1024); metrics.put("clobCharacters", 1200000);
                metrics.put("copySeconds", (copyEnd - copyStart) / 1e9); metrics.put("baselineCopyVerificationSeconds", (end - start) / 1e9); metrics.put("copyRowsPerSecond", result.committedRows / ((copyEnd - copyStart) / 1e9)); metrics.put("jvmMaxHeapBytes", Runtime.getRuntime().maxMemory());
                metrics.put("sumOfHeapPoolPeaksBytesNotRss", ManagementFactory.getMemoryPoolMXBeans().stream().filter(p -> p.getType() == MemoryType.HEAP).mapToLong(p -> p.getPeakUsage().getUsed()).sum()); metrics.put("verification", result.verification);
                Path evidence = Paths.get(System.getProperty("user.dir"), "jd-dbms-bisnuess", "target", "migration-performance-" + System.getProperty("os.arch") + ".json"); Files.write(evidence, JSON.toJSONBytes(metrics)); System.out.println("PERFORMANCE BASELINE " + JSON.toJSONString(metrics));
            } finally {
                source.rollback(); target.rollback();
                if (targetCreated) drop(target, config.targetSchema, mapping.target); if (sourceCreated) drop(source, config.sourceSchema, mapping.source);
            }
            assertNull(MigrationMetadata.objectType(source, config.sourceSchema, mapping.source)); assertNull(MigrationMetadata.objectType(target, config.targetSchema, mapping.target));
        }
    }
    private static void drop(Connection connection, String schema, String name) throws SQLException { if (!name.matches("JD_QA_P0929_[ST][A-F0-9]{8}")) throw new IllegalArgumentException("Not an owned QA fixture");
        try (Statement ddl = connection.createStatement()) { ddl.execute("DROP TABLE " + qualified(schema, name)); } }
}
