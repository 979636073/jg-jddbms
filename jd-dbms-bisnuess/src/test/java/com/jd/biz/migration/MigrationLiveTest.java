package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.core.util.DataSourceCipher;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.KeyValue;
import org.junit.Assume;
import org.junit.Test;
import java.io.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

/** Opt-in: only new uniquely named fixtures in the existing dedicated QA schemas. */
public class MigrationLiveTest {
    private final MigrationConnections connections = new MigrationConnections();
    @Test public void dmToDm() throws Exception { run("DM", "DM"); }
    @Test public void dmToOracle() throws Exception { run("DM", "ORACLE"); }
    @Test public void oracleToOracle() throws Exception { run("ORACLE", "ORACLE"); }
    @Test public void oracleToDm() throws Exception { run("ORACLE", "DM"); }

    DriverConfig driver(String type) {
        DriverConfig config = new DriverConfig(); config.setDbType(type);
        config.setJdbcDriver(type.equals("DM") ? "DmJdbcDriver18-8.1.2.141.jar" : "ojdbc8-21.1.0.0.jar,orai18n-21.5.0.0.jar");
        config.setJdbcDriverClass(type.equals("DM") ? "dm.jdbc.driver.DmDriver" : "oracle.jdbc.OracleDriver"); return config;
    }

    DataSource saved(Connection meta, String type) throws SQLException {
        String username = type.equals("DM") ? "JQDMO_260926" : "JQORO_260926";
        try (PreparedStatement statement = meta.prepareStatement("SELECT \"ID\", \"URL\", \"USER_NAME\", \"PASSWORD\", \"EXTEND_INFO\" FROM \"DATA_SOURCE\" WHERE \"USER_NAME\" = ? AND \"TYPE\" = ? ORDER BY \"ID\" DESC")) {
            statement.setString(1, username); statement.setString(2, type);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Dedicated QA datasource missing: " + type);
                DataSource source = new DataSource(); source.setId(rows.getLong(1)); source.setUrl(hostUrl(rows.getString(2)));
                source.setUserName(rows.getString(3)); source.setPassword(new DataSourceCipher(System.getenv("JDDBMS_DATASOURCE_ENCRYPTION_KEY"), System.getenv("JDDBMS_DATASOURCE_ENCRYPTION_LEGACY_KEYS")).decrypt(rows.getString(4)));
                source.setExtendInfo(JSON.parseArray(rows.getString(5), KeyValue.class)); source.setType(type); source.setDriverConfig(driver(type)); return source;
            }
        }
    }

    DataSource environment(String type) throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_MIGRATION_LIVE")));
        File root = new File(System.getProperty("user.dir")); if (root.getName().equals("jd-dbms-bisnuess")) root = root.getParentFile(); System.setProperty("user.dir", root.getAbsolutePath());
        String prefix = "JDDBMS_QA_" + type + "_";
        if (System.getenv(prefix + "URL") != null) {
            DataSource source = new DataSource(); source.setType(type); source.setDriverConfig(driver(type));
            source.setUrl(hostUrl(System.getenv(prefix + "URL"))); source.setUserName(Objects.requireNonNull(System.getenv(prefix + "USERNAME")));
            source.setPassword(Objects.requireNonNull(System.getenv(prefix + "PASSWORD"))); source.setId("DM".equals(type) ? 900001L : 900002L);
            return source;
        }
        DataSource meta = new DataSource(); meta.setType("DM"); meta.setDriverConfig(driver("DM"));
        meta.setUrl(hostUrl(System.getenv("JDDBMS_META_DB_URL"))); meta.setUserName(System.getenv("JDDBMS_META_DB_USERNAME")); meta.setPassword(System.getenv("JDDBMS_META_DB_PASSWORD"));
        try (Connection connection = connections.open(meta)) { return saved(connection, type); }
    }

    static String hostUrl(String url) {
        String host = System.getenv("JDDBMS_MIGRATION_HOST"); if (host == null) host = "127.0.0.1";
        return url.replace("host.docker.internal", host).replace("127.0.0.1", host).replace("localhost", host);
    }

    private void run(String sourceType, String targetType) throws Exception {
        Assume.assumeTrue("Real DB tests require JDDBMS_MIGRATION_LIVE=1 and configured metadata DB secrets", "1".equals(System.getenv("JDDBMS_MIGRATION_LIVE")));
        File root = new File(System.getProperty("user.dir")); if (root.getName().equals("jd-dbms-bisnuess")) root = root.getParentFile();
        assertTrue("Bundled drivers must already exist; do not download during QA", new File(root, "assets/jar/DmJdbcDriver18-8.1.2.141.jar").isFile());
        System.setProperty("user.dir", root.getAbsolutePath());
        DataSource sourceInfo, targetInfo;
        sourceInfo = environment(sourceType); targetInfo = environment(targetType);
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        Config config = new Config(); config.name = "QA " + sourceType + " → " + targetType;
        config.sourceId = sourceInfo.getId(); config.targetId = targetInfo.getId(); config.sourceSchema = sourceInfo.getUserName(); config.targetSchema = targetInfo.getUserName(); config.commitSize = 2;
        Mapping mapping = new Mapping(); mapping.source = "JD_QA_M0929_S" + token; mapping.target = "JD_QA_M0929_T" + token; config.tables.add(mapping);
        FieldMapping idMapping = new FieldMapping(); idMapping.source = "ID"; idMapping.target = "KEY_ID"; mapping.columns.add(idMapping);
        FieldMapping noteMapping = new FieldMapping(); noteMapping.source = "NOTE"; noteMapping.target = "MAPPED_NOTE"; mapping.columns.add(noteMapping);
        boolean sourceCreated = false, targetCreated = false;
        try (Connection source = connections.open(sourceInfo); Connection target = connections.open(targetInfo)) {
            try {
                assertNull(MigrationMetadata.objectType(source, config.sourceSchema, mapping.source));
                assertNull(MigrationMetadata.objectType(target, config.targetSchema, mapping.target));
                String sourceName = qualified(config.sourceSchema, mapping.source);
                String text = sourceType.equals("ORACLE") ? "VARCHAR2(100 CHAR)" : "VARCHAR(100 CHAR)";
                String binary = sourceType.equals("ORACLE") ? "RAW(32)" : "VARBINARY(32)";
                String national = sourceType.equals("ORACLE") ? "NVARCHAR2(100)" : "NVARCHAR(100)";
                try (Statement statement = source.createStatement()) {
                    statement.execute("CREATE TABLE " + sourceName + " (\"ID\" NUMBER(12,0) NOT NULL, \"AMOUNT\" NUMBER(28,9), \"NOTE\" " + text + ", \"TS\" TIMESTAMP(6), \"BIN\" " + binary + ", \"PAYLOAD\" BLOB, \"DETAIL\" CLOB, \"NATIONAL_TEXT\" " + national + ", \"NATIONAL_LOB\" NCLOB, PRIMARY KEY (\"ID\"))");
                    sourceCreated = true;
                    statement.execute("COMMENT ON TABLE " + sourceName + " IS '迁移 QA 表'");
                    statement.execute("COMMENT ON COLUMN " + sourceName + ".\"NOTE\" IS '中文 '' 注释'");
                    statement.execute("ALTER TABLE " + sourceName + " ADD CONSTRAINT " + quote("JD_QA_U_" + token) + " UNIQUE (\"NOTE\")");
                    statement.execute("ALTER TABLE " + sourceName + " ADD CONSTRAINT " + quote("JD_QA_C_" + token) + " CHECK (\"ID\" > 0)");
                    statement.execute("CREATE INDEX " + qualified(config.sourceSchema, "JD_QA_I_" + token) + " ON " + sourceName + " (\"AMOUNT\")");
                }
                fixture(source, sourceName);
                MigrationMetadata metadata = new MigrationMetadata(); Assessment report = metadata.assess(source, target, config, sourceType, targetType);
                assertTrue("Precheck blockers: " + report.tables.get(0).blockers, report.isReady()); TablePlan table = report.tables.get(0);
                config.scope = "STRUCTURE"; assertTrue(metadata.assess(source, target, config, sourceType, targetType).isReady()); config.scope = "ALL";
                target.setAutoCommit(false);
                try (Statement statement = target.createStatement()) { statement.execute(table.targetDdl); targetCreated = true; for (String ddl : table.afterDdl) statement.execute(ddl); }
                metadata.verifyCreated(target, config, table, targetType);
                config.scope = "DATA"; assertTrue("Prebuilt compatible empty target must pass data-only precheck", metadata.assess(source, target, config, sourceType, targetType).isReady()); config.scope = "ALL";
                Task task = new Task(); task.config = config; task.assessment = report;
                MigrationTransfer.Control control = new MigrationTransfer.Control(task, () -> { }); ObjectResult result = new ObjectResult(); MigrationTransfer transfer = new MigrationTransfer();
                MigrationTransfer.Digest before = transfer.digest(source, config.sourceSchema, table.source, table.columns, control);
                transfer.baseline(source, config, table, result, control);
                // Cancel at the first persisted commit boundary; recover only after a read-only content/ownership review.
                MigrationTransfer.Control[] cancellation = new MigrationTransfer.Control[1];
                cancellation[0] = new MigrationTransfer.Control(task, () -> { if (task.committedRows >= 2) cancellation[0].cancelled.set(true); });
                try { transfer.copy(source, target, config, table, result, cancellation[0]); fail("Expected cancellation after 2 committed rows"); } catch (MigrationTransfer.Cancelled expected) { }
                assertEquals(2, result.committedRows);
                transfer.reviewResume(source, target, config, table, result, control);
                String targetName = qualified(config.targetSchema, table.target);
                try (Statement statement = target.createStatement()) { statement.executeUpdate("UPDATE " + targetName + " SET \"MAPPED_NOTE\"='tampered' WHERE \"KEY_ID\"=1"); } target.commit();
                try { transfer.reviewResume(source, target, config, table, result, control); fail("Conflicting existing row must block recovery"); } catch (com.jd.common.tools.base.excption.BusinessException expected) { }
                try (PreparedStatement statement = target.prepareStatement("UPDATE " + targetName + " SET \"MAPPED_NOTE\"=? WHERE \"KEY_ID\"=1")) { statement.setString(1, "中文 ' 😀 尾空格  "); statement.executeUpdate(); } target.commit();
                transfer.reviewResume(source, target, config, table, result, control);
                transfer.copy(source, target, config, table, result, control, true);
                assertEquals(2, result.reusedRows);
                for (Column column : table.columns) {
                    MigrationTransfer.Digest left = transfer.digest(source, config.sourceSchema, table.source, Collections.singletonList(column), control);
                    TablePlan fieldPlan = new TablePlan(); fieldPlan.columns.add(column);
                    MigrationTransfer.Digest right = transfer.digest(target, config.targetSchema, table.target, targetColumns(fieldPlan), control);
                    assertEquals("Content differs for " + column.name + "/" + column.kind, left.sum, right.sum);
                }
                transfer.verify(source, target, config, table, result, control);
                for (DdlOperation operation : table.postDdl) {
                    try (Statement statement = target.createStatement()) { statement.execute(operation.sql); }
                    MigrationConstraints.verify(target, table, operation);
                }
                assertEquals(3, table.postDdl.size());
                MigrationTransfer.Digest after = transfer.digest(source, config.sourceSchema, table.source, table.columns, control);
                assertEquals(before.rows, after.rows); assertEquals(before.sum, after.sum); assertEquals(3, result.committedRows);
                assertEquals("FULL_CONTENT_HASH_PASS", result.verification); assertTrue(task.streamedBytes > 128 * 1024);
                config.scope = "DATA"; assertFalse("Data-only migration must reject nonempty target", metadata.assess(source, target, config, sourceType, targetType).isReady()); config.scope = "ALL";
                // Existing target objects must never become an implicit overwrite.
                Assessment conflict = metadata.assess(source, target, config, sourceType, targetType); assertFalse(conflict.isReady());
                config.conflict = "SKIP"; Assessment skip = metadata.assess(source, target, config, sourceType, targetType); assertTrue(skip.isReady()); assertTrue(skip.tables.get(0).skipped);
                config.conflict = "STOP";
                task.id = 999000L + (sourceType.equals("DM") ? 0 : 2) + (targetType.equals("DM") ? 0 : 1);
                task.sourceName = config.sourceSchema; task.targetName = config.targetSchema; task.direction = report.direction;
                task.status = "MIG_COMPLETED"; task.stage = "FINISHED"; task.createdAt = task.startedAt = report.checkedAt; task.finishedAt = System.currentTimeMillis();
                task.message = "独立迁移引擎真实回归已通过（隔离测试报告，不是已部署应用任务）";
                result.source = table.source; result.target = table.target; result.created = true; task.objects.add(result);
                task.logs.add("专用 QA 对象将在测试结束时删除；未修改既有业务表，未修改账号授权或配额。");
                java.nio.file.Path output = new File(root, "jd-dbms-bisnuess/target/migration-live-" + sourceType + "-" + targetType + ".json").toPath();
                java.nio.file.Files.write(output, JSON.toJSONBytes(task));
                System.out.println("LIVE " + report.direction + ": field rename, unique/check/index, cancel/PK recovery/conflict block, 3 rows, 128KiB BLOB, Unicode CLOB, NULL/empty LOB, content and source-unchanged PASS; DB " + report.sourceVersion + " -> " + report.targetVersion);
            } catch (Exception error) { throw new AssertionError(MigrationConnections.safeError(error)); }
            finally {
                try { if (!target.getAutoCommit()) target.rollback(); }
                finally {
                    try { if (targetCreated) drop(target, config.targetSchema, mapping.target, targetType); }
                    finally { if (sourceCreated) drop(source, config.sourceSchema, mapping.source, sourceType); }
                }
            }
            assertNull(MigrationMetadata.objectType(source, config.sourceSchema, mapping.source));
            assertNull(MigrationMetadata.objectType(target, config.targetSchema, mapping.target));
        }
    }

    private void fixture(Connection source, String table) throws Exception {
        try (PreparedStatement insert = source.prepareStatement("INSERT INTO " + table + " VALUES (?, ?, ?, TO_TIMESTAMP(?, 'SYYYY-MM-DD HH24:MI:SS.FF6'), ?, ?, ?, ?, ?)")) {
            insert.setInt(1, 1); insert.setBigDecimal(2, new BigDecimal("1234567890123456789.123456789")); insert.setString(3, "中文 ' 😀 尾空格  ");
            insert.setString(4, "+2024-02-29 23:59:59.123456"); insert.setBytes(5, new byte[] { 0, 1, 127, -1 });
            long length = 128 * 1024L;
            insert.setBinaryStream(6, new InputStream() { long index; @Override public int read() { return index < length ? (int) ((index++ * 31) & 255) : -1; } }, length);
            String clob = String.join("", Collections.nCopies(10000, "中文 😀 𠮷 ' 换行\n")); insert.setCharacterStream(7, new StringReader(clob));
            insert.setNString(8, "国家字符 😀 𠮷"); insert.setNCharacterStream(9, new StringReader(clob)); assertEquals(1, insert.executeUpdate());
        }
        try (Statement statement = source.createStatement()) {
            statement.executeUpdate("INSERT INTO " + table + " (\"ID\", \"PAYLOAD\", \"DETAIL\", \"NATIONAL_LOB\") VALUES (2, EMPTY_BLOB(), EMPTY_CLOB(), EMPTY_CLOB())");
            statement.executeUpdate("INSERT INTO " + table + " (\"ID\") VALUES (3)");
        }
    }


    private void drop(Connection connection, String schema, String name, String type) throws SQLException {
        if (!name.matches("JD_QA_M0929_[ST][A-F0-9]{8}")) throw new IllegalArgumentException("Not an owned QA fixture");
        try (Statement statement = connection.createStatement()) { statement.execute("DROP TABLE " + qualified(schema, name) + (type.equals("ORACLE") ? " PURGE" : "")); }
    }
}
