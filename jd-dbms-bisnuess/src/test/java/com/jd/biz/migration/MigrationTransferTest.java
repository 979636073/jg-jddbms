package com.jd.biz.migration;

import org.junit.Test;
import com.jd.common.tools.base.excption.BusinessException;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;

public class MigrationTransferTest {
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }
    static Object unused(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) return false; if (type == int.class) return 0; if (type == long.class) return 0L; return null;
    }
    static class Database {
        List<Object[]> rows = new ArrayList<>(), pending = new ArrayList<>();
        int commits, rollbacks, batches, updates, largestChunk, fetchSize;
        boolean failCommit, failBatch, shortBatch, dm;
        Runnable onWrite = () -> { };
        Connection connection() {
            return proxy(Connection.class, (object, method, args) -> {
                switch (method.getName()) {
                    case "getMetaData": return proxy(DatabaseMetaData.class, (m, call, params) -> call.getName().equals("getDatabaseProductName") ? (dm ? "DM DBMS" : "TEST") : unused(call));
                    case "prepareStatement": return statement((String) args[0]);
                    case "commit": commits++; if (failCommit) throw new SQLException("credential / business payload", "08006", 99); rows.addAll(pending); pending.clear(); return null;
                    case "rollback": rollbacks++; pending.clear(); return null;
                    case "createBlob": return new SerialBlob(new byte[0]);
                    case "createClob": return new SerialClob(new char[0]);
                    default: return unused(method);
                }
            });
        }
        PreparedStatement statement(String sql) {
            Map<Integer, Object> values = new TreeMap<>(); List<Object[]> batch = new ArrayList<>();
            return proxy(PreparedStatement.class, (object, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer && !name.equals("setFetchSize")) {
                    Object value = name.equals("setNull") ? null : args[1];
                    if (name.equals("setObject") && value instanceof InputStream && ((Integer) args[2] == Types.CLOB || (Integer) args[2] == Types.NCLOB)) value = new InputStreamReader((InputStream) value, java.nio.charset.StandardCharsets.UTF_8);
                    values.put((Integer) args[0], value); return null;
                }
                switch (name) {
                    case "setFetchSize": fetchSize = (Integer) args[0]; return null;
                    case "executeQuery": return result(sql.equals("SELECT UNICODE") ? Collections.singletonList(new Object[] { 1 }) : sql.contains("ALL_TRIGGERS") || sql.startsWith("SELECT 1") ? Collections.emptyList() : rows);
                    case "addBatch": batch.add(values.values().toArray()); return null;
                    case "clearBatch": batch.clear(); return null;
                    case "executeBatch":
                        batches++; if (failBatch) throw new SQLException("failed", "23000");
                        pending.addAll(batch); onWrite.run(); int[] counts = new int[shortBatch ? 0 : batch.size()]; Arrays.fill(counts, 1); return counts;
                    case "executeUpdate":
                        updates++; Object[] row = values.values().toArray();
                        for (int i = 0; i < row.length; i++) {
                            if (row[i] instanceof InputStream) {
                                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
                                while ((count = ((InputStream) row[i]).read(buffer)) != -1) { largestChunk = Math.max(largestChunk, count); out.write(buffer, 0, count); }
                                row[i] = out.toByteArray();
                            } else if (row[i] instanceof Reader) {
                                StringBuilder text = new StringBuilder(); char[] buffer = new char[4096]; int count;
                                while ((count = ((Reader) row[i]).read(buffer)) != -1) { largestChunk = Math.max(largestChunk, count); text.append(buffer, 0, count); }
                                row[i] = text.toString();
                            } else if (row[i] instanceof Blob) row[i] = ((Blob) row[i]).length() == 0 ? new byte[0] : ((Blob) row[i]).getBytes(1, (int) ((Blob) row[i]).length());
                            else if (row[i] instanceof Clob) row[i] = ((Clob) row[i]).length() == 0 ? "" : ((Clob) row[i]).getSubString(1, (int) ((Clob) row[i]).length());
                        }
                        pending.add(row); onWrite.run(); return 1;
                    default: return unused(method);
                }
            });
        }
        ResultSet result(List<Object[]> items) {
            int[] index = { -1 };
            return proxy(ResultSet.class, (object, method, args) -> {
                if (method.getName().equals("next")) return ++index[0] < items.size();
                if (method.getName().startsWith("get") && args != null && args[0] instanceof Integer) {
                    Object value = items.get(index[0])[(Integer) args[0] - 1];
                    if (value != null && method.getName().equals("getBlob")) return new SerialBlob((byte[]) value);
                    if (value != null && method.getName().equals("getClob")) return new SerialClob(((String) value).toCharArray());
                    return value;
                }
                return unused(method);
            });
        }
    }
    static TablePlan table(String... kinds) {
        TablePlan table = new TablePlan(); table.source = "SOURCE"; table.target = "TARGET";
        for (String kind : kinds) { Column column = new Column(); column.name = "C" + table.columns.size(); column.kind = kind; column.length = 100; table.columns.add(column); } return table;
    }
    static MigrationTransfer.Control control() { Task task = new Task(); task.config = MigrationDialectTest.config(); return new MigrationTransfer.Control(task, () -> { }); }

    @Test public void batchesAndCountsOnlyCommittedRowsThenVerifiesMultiset() throws Exception {
        Database source = new Database(), target = new Database();
        source.rows.add(new Object[] { new BigDecimal("1.00"), "中文 😀" }); source.rows.add(new Object[] { new BigDecimal("2"), null }); source.rows.add(new Object[] { new BigDecimal("1"), "中文 😀" });
        MigrationTransfer.Control control = control(); control.task.config.commitSize = 2; ObjectResult result = new ObjectResult(); TablePlan table = table("NUMBER", "TEXT");
        MigrationTransfer transfer = new MigrationTransfer(); transfer.copy(source.connection(), target.connection(), control.task.config, table, result, control);
        assertEquals(2, target.commits); assertEquals(3, result.committedRows); assertEquals(3, control.task.committedRows);
        Collections.reverse(target.rows); transfer.verify(source.connection(), target.connection(), control.task.config, table, result, control);
        assertEquals("FULL_CONTENT_HASH_PASS", result.verification);
        target.rows.set(0, new Object[] { new BigDecimal("2"), null });
        try { transfer.verify(source.connection(), target.connection(), control.task.config, table, result, control); fail(); } catch (Exception expected) { assertEquals("CONTENT_MISMATCH", result.verification); }
    }
    @Test public void appendVerifiesInitialTargetPlusSourceWithoutLosingDuplicateCounts() throws Exception {
        Database source = new Database(), target = new Database(); source.rows.add(new Object[] { BigDecimal.ONE }); target.rows.add(new Object[] { BigDecimal.ONE });
        MigrationTransfer transfer = new MigrationTransfer(); TablePlan table = table("NUMBER"); MigrationTransfer.Control control = control(); ObjectResult result = new ObjectResult();
        result.targetBaseline = MigrationTransfer.signature(transfer.digest(target.connection(), control.task.config.targetSchema, table.target, table.columns, control));
        transfer.copy(source.connection(), target.connection(), control.task.config, table, result, control); transfer.verify(source.connection(), target.connection(), control.task.config, table, result, control);
        assertEquals("APPENDED_CONTENT_HASH_PASS", result.verification); assertEquals(2, result.targetRows); assertEquals(1, result.sourceRows);
        target.rows.add(new Object[] { BigDecimal.ONE });
        try { transfer.verify(source.connection(), target.connection(), control.task.config, table, result, control); fail(); } catch (BusinessException expected) { }
    }
    @Test public void streamsLargeLobsAndPreservesNullEmptyAndUnicode() throws Exception {
        Database source = new Database(), target = new Database(); byte[] blob = new byte[1024 * 1024 + 17]; new Random(7).nextBytes(blob);
        String text = String.join("", Collections.nCopies(20000, "中文😀"));
        source.rows.add(new Object[] { blob, text }); source.rows.add(new Object[] { new byte[0], "" }); source.rows.add(new Object[] { null, null });
        TablePlan table = table("BLOB", "CLOB"); MigrationTransfer.Control control = control(); ObjectResult result = new ObjectResult();
        MigrationTransfer transfer = new MigrationTransfer(); transfer.copy(source.connection(), target.connection(), control.task.config, table, result, control);
        assertEquals(1, source.fetchSize); assertEquals(0, target.batches); assertEquals(3, target.updates); assertTrue(target.largestChunk <= 8192);
        assertEquals(blob.length + text.length() * 2L, control.task.streamedBytes); assertArrayEquals(blob, (byte[]) target.rows.get(0)[0]);
        assertArrayEquals(new byte[0], (byte[]) target.rows.get(1)[0]); assertEquals("", target.rows.get(1)[1]); assertNull(target.rows.get(2)[0]);
        transfer.verify(source.connection(), target.connection(), control.task.config, table, result, control); assertEquals("FULL_CONTENT_HASH_PASS", result.verification);
    }
    @Test public void streamsMultipleDmCharacterLobsAcrossSurrogateBoundaries() throws Exception {
        Database source = new Database(), target = new Database(); target.dm = true;
        String text = String.join("", Collections.nCopies(10000, "中文 😀 𠮷 ' 换行\n"));
        source.rows.add(new Object[] { text, text });
        TablePlan table = table("CLOB", "NCLOB"); MigrationTransfer.Control control = control(); ObjectResult result = new ObjectResult();
        MigrationTransfer transfer = new MigrationTransfer(); transfer.copy(source.connection(), target.connection(), control.task.config, table, result, control);
        assertEquals(text, target.rows.get(0)[0]); assertEquals(text, target.rows.get(0)[1]);
        assertEquals(text.length() * 4L, control.task.streamedBytes);
        transfer.verify(source.connection(), target.connection(), control.task.config, table, result, control);
        assertEquals("FULL_CONTENT_HASH_PASS", result.verification);
    }
    @Test public void cancellationRollsBackUncommittedBatch() throws Exception {
        Database source = new Database(), target = new Database(); source.rows.add(new Object[] { new BigDecimal("1") });
        MigrationTransfer.Control control = control(); target.onWrite = () -> control.cancelled.set(true); ObjectResult result = new ObjectResult();
        try { new MigrationTransfer().copy(source.connection(), target.connection(), control.task.config, table("NUMBER"), result, control); fail(); }
        catch (MigrationTransfer.Cancelled expected) { assertEquals(0, result.committedRows); assertEquals(1, target.rollbacks); assertTrue(target.rows.isEmpty()); }
    }
    @Test public void driverWrappedLobCancellationRemainsCancelledAfterSuccessfulRollback() throws Exception {
        Database source = new Database(), target = new Database(); source.rows.add(new Object[] { new byte[8192] }); MigrationTransfer.Control control = control();
        Connection delegate = target.connection();
        Connection wrapped = proxy(Connection.class, (object, method, args) -> {
            try {
                Object value = method.invoke(delegate, args);
                if (!(value instanceof PreparedStatement)) return value;
                PreparedStatement statement = (PreparedStatement) value;
                return proxy(PreparedStatement.class, (o, call, values) -> {
                    if (call.getName().equals("executeUpdate")) control.cancelled.set(true);
                    try { return call.invoke(statement, values); }
                    catch (InvocationTargetException error) { if (error.getCause() instanceof MigrationTransfer.Cancelled) throw new SQLException("Wrapped stream conversion", "HY096", 6007); throw error.getCause(); }
                });
            } catch (InvocationTargetException error) { throw error.getCause(); }
        });
        ObjectResult result = new ObjectResult();
        try { new MigrationTransfer().copy(source.connection(), wrapped, control.task.config, table("BLOB"), result, control); fail(); }
        catch (MigrationTransfer.Cancelled expected) { assertEquals(0, result.committedRows); assertEquals(1, target.rollbacks); assertTrue(target.rows.isEmpty()); assertFalse(control.committing); }
    }
    @Test public void cancellationDoesNotClaimPreviouslyCommittedDataWasReverted() throws Exception {
        Database source = new Database(), target = new Database(); source.rows.add(new Object[] { new BigDecimal("1") }); source.rows.add(new Object[] { new BigDecimal("2") });
        MigrationTransfer.Control control = control(); control.task.config.commitSize = 1; target.onWrite = () -> { if (target.commits == 1) control.cancelled.set(true); };
        ObjectResult result = new ObjectResult();
        try { new MigrationTransfer().copy(source.connection(), target.connection(), control.task.config, table("NUMBER"), result, control); fail(); }
        catch (MigrationTransfer.Cancelled expected) { assertEquals(1, result.committedRows); assertEquals(1, target.rows.size()); }
    }
    @Test public void commitFailureRemainsUncertainAndDoesNotIncrementCounters() throws Exception {
        Database source = new Database(), target = new Database(); source.rows.add(new Object[] { new BigDecimal("1") }); target.failCommit = true;
        MigrationTransfer.Control control = control(); ObjectResult result = new ObjectResult();
        try { new MigrationTransfer().copy(source.connection(), target.connection(), control.task.config, table("NUMBER"), result, control); fail(); }
        catch (SQLException expected) { assertTrue(control.committing); assertEquals("COMMITTING", control.task.stage); assertEquals(0, result.committedRows); assertFalse(MigrationConnections.safeError(expected).contains("credential")); }
    }
    @Test public void batchFailureOrMissingCountsNeverClaimsSuccess() throws Exception {
        for (boolean missing : new boolean[] { false, true }) {
            Database source = new Database(), target = new Database(); source.rows.add(new Object[] { new BigDecimal("1") }); target.failBatch = !missing; target.shortBatch = missing;
            MigrationTransfer.Control control = control(); ObjectResult result = new ObjectResult();
            try { new MigrationTransfer().copy(source.connection(), target.connection(), control.task.config, table("NUMBER"), result, control); fail(); }
            catch (SQLException expected) { assertEquals(0, result.committedRows); assertEquals(0, target.commits); assertEquals(1, target.rollbacks); }
        }
    }
}
