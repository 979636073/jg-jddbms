package com.jd.biz.migration;

import com.jd.common.tools.base.excption.BusinessException;
import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import org.apache.commons.io.input.ReaderInputStream;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

/** One bounded writer; LOB streams are consumed before the source cursor advances. */
public class MigrationTransfer {
    private static final long BUFFER_LIMIT = 4L * 1024 * 1024;
    private static final BigInteger MODULUS = BigInteger.ONE.shiftLeft(256);
    private static final String TIME_FORMAT = "SYYYY-MM-DD HH24:MI:SS.FF6";

    static String selectColumns(List<Column> columns) {
        return columns.stream().map(column -> {
            String expression = column.sourceExpression == null ? quote(column.name) : column.sourceExpression;
            return "TIMESTAMP".equals(column.kind) ? "TO_CHAR(CAST(" + expression + " AS TIMESTAMP(6)), '" + TIME_FORMAT + "')" : expression;
        }).collect(Collectors.joining(", "));
    }

    public static class Control {
        public final AtomicBoolean cancelled;
        final AtomicBoolean stopped;
        final Semaphore lobSlot;
        public volatile boolean committing;
        public volatile boolean checkpointFailed;
        private boolean cancelObserved;
        public final Task task;
        private final Runnable persist;
        private volatile long lastProgress;
        private final long[] nextWrite;

        public Control(Task task, Runnable persist) {
            this.task = task; this.persist = persist; cancelled = new AtomicBoolean(); stopped = new AtomicBoolean();
            lobSlot = new Semaphore(1); nextWrite = new long[] { System.nanoTime() };
        }
        private Control(Control parent) {
            task = parent.task; persist = parent.persist; cancelled = parent.cancelled; stopped = parent.stopped;
            lobSlot = parent.lobSlot; nextWrite = parent.nextWrite;
        }
        Control worker() { return new Control(this); }
        public void check() { if (cancelled.get() || stopped.get() || Thread.currentThread().isInterrupted()) { cancelObserved = true; throw new Cancelled(); } }
        public void checkpoint() { synchronized (task) {
            if (task.inFlightCommits > 0 && !"DDL_COMMITTING".equals(task.stage)) task.stage = "COMMITTING";
            try { persist.run(); lastProgress = System.currentTimeMillis(); }
            catch (RuntimeException error) { checkpointFailed = true; throw error; }
        } }
        public void bytes(long count) {
            synchronized (task) { task.streamedBytes += count; }
            if (System.currentTimeMillis() - lastProgress >= 1000) checkpoint();
            check();
        }
        void pace(int rowsPerSecond) throws InterruptedException {
            check();
            if (rowsPerSecond == 0) return;
            long due;
            synchronized (nextWrite) {
                due = Math.max(nextWrite[0], System.nanoTime()); nextWrite[0] = due + 1000000000L / rowsPerSecond;
            }
            long remaining;
            boolean waiting = false;
            while ((remaining = due - System.nanoTime()) > 0) {
                if (!waiting) { task.stage = "THROTTLED"; checkpoint(); waiting = true; }
                Thread.sleep(Math.max(1, Math.min(100, remaining / 1000000)));
                check();
                if (System.currentTimeMillis() - lastProgress >= 1000) checkpoint();
            }
            if (waiting) { task.stage = "DATA"; checkpoint(); }
        }
        void beginCommit(String stage) {
            synchronized (task) { committing = true; task.inFlightCommits++; task.stage = stage; checkpoint(); }
        }
        void endCommit(String stage) {
            synchronized (task) { committing = false; task.inFlightCommits--; task.stage = stage; checkpoint(); }
        }
    }

    public static class Cancelled extends RuntimeException { }

    public void copy(Connection source, Connection target, Config config, TablePlan table, ObjectResult result, Control control) throws Exception {
        copy(source, target, config, table, result, control, false);
    }

    public void copy(Connection source, Connection target, Config config, TablePlan table, ObjectResult result, Control control, boolean resume) throws Exception {
        config = scoped(config, table);
        if (config.rowsPerSecond > 0) config.commitSize = Math.min(config.commitSize, config.rowsPerSecond);
        boolean lob = table.columns.stream().anyMatch(c -> Arrays.asList("BLOB", "CLOB", "NCLOB").contains(c.kind));
        Charset dmEncoding = lob ? dmEncoding(target) : null;
        String names = names(table.columns.stream().map(MigrationDialect::targetName).collect(Collectors.toList()));
        String select = "SELECT " + selectColumns(table.columns) + " FROM " + qualified(config.sourceSchema, table.source) + " JD_SRC" + MigrationFilter.where(table);
        String insert = "INSERT INTO " + qualified(config.targetSchema, table.target) + " (" + names + ") VALUES ("
                + table.columns.stream().map(c -> "TIMESTAMP".equals(c.kind) ? "TO_TIMESTAMP(?, '" + TIME_FORMAT + "')" : "?").collect(Collectors.joining(",")) + ")";
        long estimate = table.columns.stream().mapToLong(c -> "TEXT".equals(c.kind) ? c.length * 4L : "BINARY".equals(c.kind) ? c.length : 64L).sum();
        int fetchSize = lob ? 1 : (int) Math.max(1, Math.min(config.fetchSize, BUFFER_LIMIT / Math.max(1, estimate)));
        result.stage = "COPYING"; control.task.stage = "DATA"; control.checkpoint();
        long pending = 0, batchBytes = 0;
        try (PreparedStatement read = MigrationMetadata.prepare(source, select);
             PreparedStatement write = MigrationMetadata.prepare(target, insert);
             PreparedStatement existing = resume ? keyQuery(target, config.targetSchema, table.target, targetColumns(table), table, false) : null) {
            MigrationFilter.bind(read, table, 1);
            read.setFetchSize(fetchSize);
            try (ResultSet rows = read.executeQuery()) {
                while (rows.next()) {
                    control.check();
                    if (resume && matchingRow(rows, table.columns, existing, table, control)) { result.reusedRows++; continue; }
                    control.pace(config.rowsPerSecond);
                    List<AutoCloseable> resources = new ArrayList<>();
                    try {
                        long bytes = bind(rows, target, write, table.columns, resources, control, dmEncoding);
                        if (bytes > BUFFER_LIMIT) throw new BusinessException("单行非 LOB 字段超过 4MB 首版内存预算，请调整结构");
                        if (lob) {
                            if (write.executeUpdate() != 1) throw new BusinessException("目标写入行数异常");
                        } else {
                            write.addBatch(); batchBytes += bytes;
                        }
                        result.readRows++; pending++;
                    } finally {
                        for (int i = resources.size() - 1; i >= 0; i--) resources.get(i).close();
                    }
                    if (pending >= config.commitSize || batchBytes >= BUFFER_LIMIT) {
                        control.check(); if (!lob) executeBatch(write, pending);
                        commit(target, pending, result, control); pending = 0; batchBytes = 0;
                    }
                    if (System.currentTimeMillis() - control.lastProgress >= 1000) control.checkpoint();
                }
                if (pending > 0) {
                    control.check(); if (!lob) executeBatch(write, pending); commit(target, pending, result, control);
                }
            }
        } catch (Exception e) {
            try { target.rollback(); } catch (SQLException rollbackFailure) { control.committing = true; }
            // Some JDBC drivers wrap a cancellation thrown by a LOB stream as a conversion SQLException.
            if (control.cancelObserved && !control.committing && !control.checkpointFailed) throw new Cancelled();
            throw e;
        }
    }

    static void executeBatch(PreparedStatement statement, long pending) throws SQLException {
        int[] results = statement.executeBatch();
        if (results.length != pending) throw new SQLException("Unexpected insert batch count", "HY000");
        for (int result : results) {
            if (result != 1 && result != Statement.SUCCESS_NO_INFO) throw new SQLException("Unexpected insert batch result", "HY000");
        }
        statement.clearBatch();
    }

    static void commit(Connection target, long rows, ObjectResult result, Control control) throws SQLException {
        control.check();
        result.stage = "COMMITTING"; control.beginCommit("COMMITTING");
        target.commit();
        synchronized (control.task) { result.committedRows += rows; control.task.committedRows += rows; result.stage = "COPYING"; control.endCommit("DATA"); }
    }

    private long bind(ResultSet rows, Connection target, PreparedStatement statement, List<Column> columns,
                      List<AutoCloseable> resources, Control control, Charset dmEncoding) throws Exception {
        long bytes = 0;
        for (int i = 0; i < columns.size(); i++) {
            control.check(); Column column = columns.get(i); int index = i + 1;
            switch (column.kind) {
                case "NUMBER":
                    BigDecimal number = rows.getBigDecimal(index); statement.setBigDecimal(index, number); bytes += 64; break;
                case "TEXT":
                    String text = rows.getString(index);
                    if (text != null && text.isEmpty()) throw new BusinessException("发现空字符串，目标 NULL 语义可能不同，需明确转换");
                    if (column.targetType != null && column.targetType.startsWith("N")) statement.setNString(index, text);
                    else statement.setString(index, text);
                    bytes += text == null ? 1 : text.length() * 4L; break;
                case "BINARY":
                    byte[] binary = rows.getBytes(index); statement.setBytes(index, binary); bytes += binary == null ? 1 : binary.length; break;
                case "TIMESTAMP":
                    // Civil time never passes through the JVM timezone or driver Calendar conversion.
                    statement.setString(index, rows.getString(index)); bytes += 64; break;
                case "BLOB": {
                    Blob blob = rows.getBlob(index);
                    if (blob == null) { statement.setNull(index, Types.BLOB); break; }
                    resources.add(blob::free);
                    if (blob.length() == 0) {
                        Blob empty = target.createBlob(); resources.add(empty::free); statement.setBlob(index, empty);
                    } else {
                        InputStream stream = counted(blob.getBinaryStream(), control); resources.add(stream);
                        statement.setBinaryStream(index, stream, blob.length());
                    }
                    break;
                }
                case "CLOB": case "NCLOB": {
                    Clob clob = rows.getClob(index);
                    boolean national = "NCLOB".equals(column.kind);
                    if (clob == null) { statement.setNull(index, national ? Types.NCLOB : Types.CLOB); break; }
                    resources.add(clob::free);
                    if (clob.length() == 0) {
                        Clob empty = national ? target.createNClob() : target.createClob(); resources.add(empty::free);
                        if (national) statement.setNClob(index, (NClob) empty); else statement.setClob(index, empty);
                    } else {
                        Reader reader = counted(clob.getCharacterStream(), control); resources.add(reader);
                        // JDBC/SQL CLOB length units can differ for surrogate pairs. Consume to EOF.
                        if (dmEncoding != null) {
                            // DM's Reader binding encodes independent chunks and splits surrogate pairs.
                            InputStream encoded = new ReaderInputStream(reader, dmEncoding, 4096); resources.add(encoded);
                            statement.setObject(index, encoded, national ? Types.NCLOB : Types.CLOB);
                        } else if (national) statement.setNCharacterStream(index, reader);
                        else statement.setCharacterStream(index, reader);
                    }
                    break;
                }
                default: throw new BusinessException("字段绑定类型未实现");
            }
        }
        return bytes;
    }

    private Charset dmEncoding(Connection target) throws SQLException {
        DatabaseMetaData metadata = target.getMetaData();
        if (!"DM DBMS".equals(metadata.getDatabaseProductName())) return null;
        try (PreparedStatement statement = MigrationMetadata.prepare(target, "SELECT UNICODE"); ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw new BusinessException("无法读取目标达梦字符集，不能猜测 LOB 编码");
            int unicode = rows.getInt(1);
            if (unicode == 1) return StandardCharsets.UTF_8;
            if (unicode == 0) return Charset.forName("GB18030");
            throw new BusinessException("目标达梦字符集尚未适配流式 LOB 编码");
        }
    }

    private InputStream counted(InputStream stream, Control control) {
        return new FilterInputStream(stream) {
            @Override public int read() throws IOException { control.check(); int value = in.read(); if (value >= 0) control.bytes(1); return value; }
            @Override public int read(byte[] buffer, int off, int len) throws IOException { control.check(); int count = in.read(buffer, off, len); if (count > 0) control.bytes(count); return count; }
        };
    }
    private Reader counted(Reader reader, Control control) {
        return new FilterReader(reader) {
            @Override public int read() throws IOException { control.check(); int value = in.read(); if (value >= 0) control.bytes(2); return value; }
            @Override public int read(char[] buffer, int off, int len) throws IOException { control.check(); int count = in.read(buffer, off, len); if (count > 0) control.bytes(count * 2L); return count; }
        };
    }

    public void verify(Connection source, Connection target, Config config, TablePlan table, ObjectResult result, Control control) throws Exception {
        config = scoped(config, table);
        result.stage = "VERIFYING"; control.task.stage = "VERIFICATION"; control.checkpoint();
        Digest sourceDigest = sourceDigest(source, config.sourceSchema, table, control);
        Digest targetDigest = digest(target, config.targetSchema, table.target, targetColumns(table), control);
        result.sourceRows = sourceDigest.rows; result.targetRows = targetDigest.rows;
        long expectedRows = sourceDigest.rows; BigInteger expectedSum = sourceDigest.sum;
        if (result.targetBaseline != null) {
            String[] baseline = result.targetBaseline.split(":", 2);
            expectedRows += Long.parseLong(baseline[0]); expectedSum = expectedSum.add(new BigInteger(baseline[1], 16)).mod(MODULUS);
        }
        boolean equal = expectedRows == targetDigest.rows && expectedSum.equals(targetDigest.sum);
        result.verification = equal ? (result.targetBaseline == null ? "FULL_CONTENT_HASH_PASS" : "APPENDED_CONTENT_HASH_PASS") : "CONTENT_MISMATCH";
        result.stage = equal ? "COMPLETED" : "FAILED";
        control.checkpoint();
        if (!equal) throw new BusinessException("全量内容摘要或行数不一致，不能标记迁移完成");
        if (result.sourceBaseline != null && !result.sourceBaseline.equals(signature(sourceDigest))) {
            result.verification = "SOURCE_CHANGED"; result.stage = "FAILED"; control.checkpoint();
            throw new BusinessException("源端数据在迁移期间变化，不能验收");
        }
    }

    static class Digest { long rows; BigInteger sum = BigInteger.ZERO; }

    static String signature(Digest digest) { return digest.rows + ":" + digest.sum.toString(16); }

    void baseline(Connection source, Config config, TablePlan table, ObjectResult result, Control control) throws Exception {
        config = scoped(config, table);
        control.task.stage = "SOURCE_BASELINE"; result.stage = "SOURCE_BASELINE"; control.checkpoint();
        String actual = signature(sourceDigest(source, config.sourceSchema, table, control));
        if (result.sourceBaseline != null && !result.sourceBaseline.equals(actual)) throw new BusinessException("源端内容与原任务基线不同，禁止恢复");
        result.sourceBaseline = actual; result.sourceDefinition = MigrationDialect.hash(table.sourceDdl);
        control.checkpoint();
    }

    void reviewResume(Connection source, Connection target, Config config, TablePlan table, ObjectResult result, Control control) throws Exception {
        config = scoped(config, table);
        if (table.primaryKey.stream().anyMatch(key -> table.columns.stream().anyMatch(column -> key.equals(column.name) && column.sourceExpression != null))) throw new BusinessException("转换主键不支持行级续迁，请使用新目标重新迁移");
        if (table.primaryKey.isEmpty() || table.primaryKey.stream().anyMatch(n -> table.columns.stream().noneMatch(c -> c.name.equals(n) && Arrays.asList("NUMBER", "TEXT", "BINARY", "TIMESTAMP").contains(c.kind))))
            throw new BusinessException("受限恢复要求已适配的稳定主键；无主键表不能自动续传");
        if (result.sourceBaseline == null || result.sourceDefinition == null) throw new BusinessException("旧任务没有写入前源端基线，不能安全恢复");
        if (!result.sourceDefinition.equals(MigrationDialect.hash(new MigrationMetadata().sourceDdl(source, config.sourceSchema, table.source)))) throw new BusinessException("源端定义变化，禁止恢复");
        if (!result.sourceBaseline.equals(signature(sourceDigest(source, config.sourceSchema, table, control)))) throw new BusinessException("源端内容变化，禁止恢复");
        MigrationMetadata.assertNoTriggers(target, config.targetSchema, table.target);
        // Validate every existing target row against the source before any new write. Extra rows and changed values block recovery.
        List<Column> targetFields = targetColumns(table);
        try (PreparedStatement read = MigrationMetadata.prepare(target, "SELECT " + selectColumns(targetFields) + " FROM " + qualified(config.targetSchema, table.target));
             PreparedStatement lookup = keyQuery(source, config.sourceSchema, table.source, table.columns, table, true)) {
            read.setFetchSize(1);
            try (ResultSet rows = read.executeQuery()) {
                while (rows.next()) if (!matchingRow(rows, table.columns, lookup, table, control)) throw new BusinessException("目标含非源端数据，禁止恢复");
            }
        }
    }

    private PreparedStatement keyQuery(Connection connection, String schema, String name, List<Column> selected, TablePlan table, boolean filtered) throws SQLException {
        String predicate = table.primaryKey.stream().map(key -> {
            int index = java.util.stream.IntStream.range(0, table.columns.size()).filter(i -> table.columns.get(i).name.equals(key)).findFirst().getAsInt();
            return quote(selected.get(index).name) + "=" + ("TIMESTAMP".equals(table.columns.get(index).kind) ? "TO_TIMESTAMP(?, '" + TIME_FORMAT + "')" : "?");
        }).collect(Collectors.joining(" AND "));
        String filter = filtered ? MigrationFilter.predicate(table) : "";
        PreparedStatement query = MigrationMetadata.prepare(connection, "SELECT " + selectColumns(selected) + " FROM " + qualified(schema, name) + " JD_SRC WHERE " + predicate + (filter.isEmpty() ? "" : " AND " + filter));
        try { if (filtered) MigrationFilter.bind(query, table, table.primaryKey.size() + 1); return query; }
        catch (RuntimeException | SQLException e) { query.close(); throw e; }
    }

    private boolean matchingRow(ResultSet row, List<Column> columns, PreparedStatement lookup, TablePlan table, Control control) throws Exception {
        int parameter = 0;
        for (String key : table.primaryKey) {
            int index = java.util.stream.IntStream.range(0, columns.size()).filter(i -> columns.get(i).name.equals(key)).findFirst().getAsInt();
            Column column = columns.get(index); parameter++;
            if ("NUMBER".equals(column.kind)) lookup.setBigDecimal(parameter, row.getBigDecimal(index + 1));
            else if ("BINARY".equals(column.kind)) lookup.setBytes(parameter, row.getBytes(index + 1));
            else if (column.targetType != null && column.targetType.startsWith("N")) lookup.setNString(parameter, row.getString(index + 1));
            else lookup.setString(parameter, row.getString(index + 1));
            if (row.wasNull()) throw new BusinessException("主键含 NULL，禁止恢复");
        }
        try (ResultSet found = lookup.executeQuery()) {
            if (!found.next()) return false;
            if (!Arrays.equals(rowDigest(row, columns, control), rowDigest(found, columns, control)) || found.next()) throw new BusinessException("既有目标主键数据不一致或不唯一，禁止覆盖/恢复");
            return true;
        }
    }

    Digest digest(Connection connection, String schema, String table, List<Column> columns, Control control) throws Exception {
        return digest(connection, schema, table, columns, null, control);
    }

    private Digest sourceDigest(Connection connection, String schema, TablePlan table, Control control) throws Exception {
        return digest(connection, schema, table.source, table.columns, table, control);
    }

    private Digest digest(Connection connection, String schema, String table, List<Column> columns, TablePlan filter, Control control) throws Exception {
        String sql = "SELECT " + selectColumns(columns) + " FROM " + qualified(schema, table) + " JD_SRC" + (filter == null ? "" : MigrationFilter.where(filter));
        Digest output = new Digest();
        try (PreparedStatement statement = MigrationMetadata.prepare(connection, sql)) {
            if (filter != null) MigrationFilter.bind(statement, filter, 1);
            statement.setFetchSize(1);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    control.check();
                    output.sum = output.sum.add(new BigInteger(1, rowDigest(rows, columns, control))).mod(MODULUS); output.rows++;
                    if (System.currentTimeMillis() - control.lastProgress >= 1000) control.checkpoint();
                }
            }
        }
        return output;
    }

    private byte[] rowDigest(ResultSet rows, List<Column> columns, Control control) throws Exception {
        MessageDigest row = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < columns.size(); i++) {
                        MessageDigest value = MessageDigest.getInstance("SHA-256"); Column column = columns.get(i); int index = i + 1;
                        value.update(column.kind.getBytes(StandardCharsets.US_ASCII));
                        switch (column.kind) {
                            case "NUMBER":
                                BigDecimal number = rows.getBigDecimal(index);
                                scalar(value, number == null ? null : number.stripTrailingZeros().toPlainString()); break;
                            case "TIMESTAMP":
                                scalar(value, rows.getString(index)); break;
                            case "BINARY":
                                byte[] binary = rows.getBytes(index); value.update((byte) (binary == null ? 0 : 1)); if (binary != null) value.update(binary); break;
                            case "BLOB": {
                                Blob blob = rows.getBlob(index); value.update((byte) (blob == null ? 0 : 1));
                                if (blob != null) {
                                    try (InputStream stream = blob.getBinaryStream()) {
                                        byte[] buffer = new byte[8192]; int count;
                                        while ((count = stream.read(buffer)) != -1) { control.check(); value.update(buffer, 0, count); }
                                    } finally { blob.free(); }
                                }
                                break;
                            }
                            case "CLOB": case "NCLOB": {
                                Clob clob = rows.getClob(index); value.update((byte) (clob == null ? 0 : 1));
                                if (clob != null) {
                                    try (Reader reader = clob.getCharacterStream()) { characters(value, reader, control); }
                                    finally { clob.free(); }
                                }
                                break;
                            }
                            default: scalar(value, rows.getString(index));
                        }
                        row.update(value.digest());
        }
        return row.digest();
    }

    private static void scalar(MessageDigest digest, String value) {
        digest.update((byte) (value == null ? 0 : 1));
        if (value != null) for (int i = 0; i < value.length(); i++) { digest.update((byte) (value.charAt(i) >>> 8)); digest.update((byte) value.charAt(i)); }
    }
    private static void characters(MessageDigest digest, Reader reader, Control control) throws IOException {
        char[] buffer = new char[4096]; int count;
        while ((count = reader.read(buffer)) != -1) {
            control.check();
            for (int i = 0; i < count; i++) { digest.update((byte) (buffer[i] >>> 8)); digest.update((byte) buffer[i]); }
        }
    }
}
