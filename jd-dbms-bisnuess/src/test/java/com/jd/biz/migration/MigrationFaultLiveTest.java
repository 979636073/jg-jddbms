package com.jd.biz.migration;

import com.jd.biz.domain.api.model.DataSource;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

/** Faults affect only this run's sockets, writer connections and uniquely named QA tables. */
public class MigrationFaultLiveTest {
    @Test public void actualNetworkDisconnectAndUncertainCommitStopAndCanBeReviewed() throws Exception {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);
        MigrationLiveTest environment = new MigrationLiveTest(); DataSource dm = environment.environment("DM"); MigrationConnections raw = new MigrationConnections();
        String prefix = "JQA_NET_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT), store = prefix + "_M", source = prefix + "_S";
        List<String> created = new ArrayList<>(); MigrationService service = null;
        try (Connection database = raw.open(dm)) {
            try {
                execute(database, "CREATE TABLE " + qualified(dm.getUserName(), store) + " (ID BIGINT IDENTITY(1,1) PRIMARY KEY, GMT_CREATE TIMESTAMP, GMT_MODIFIED TIMESTAMP, DATA_SOURCE_ID BIGINT, DATABASE_NAME VARCHAR(128), SCHEMA_NAME VARCHAR(128), TABLE_NAME VARCHAR(128), DELETED CHAR(1), USER_ID BIGINT, TASK_TYPE VARCHAR(64), TASK_STATUS VARCHAR(64), TASK_PROGRESS VARCHAR(64), TASK_NAME VARCHAR(256), DOWNLOAD_URL VARCHAR(512), CONTENT BLOB)"); created.add(store);
                execute(database, "CREATE TABLE " + qualified(dm.getUserName(), source) + " (ID NUMBER(10,0) PRIMARY KEY)"); created.add(source);
                execute(database, "INSERT INTO " + qualified(dm.getUserName(), source) + " VALUES(1)"); execute(database, "INSERT INTO " + qualified(dm.getUserName(), source) + " VALUES(2)");
                Matcher address = Pattern.compile("jdbc:dm://([^:/]+):(\\d+)").matcher(dm.getUrl()); assertTrue("Supported dedicated QA DM URL", address.find());
                for (String phase : Arrays.asList("BATCH", "COMMIT", "COMMIT_RESPONSE")) {
                    String target = prefix + ("BATCH".equals(phase) ? "_B" : "COMMIT".equals(phase) ? "_C" : "_A"); created.add(target); AtomicBoolean armed = new AtomicBoolean(true);
                    try (CutProxy proxy = new CutProxy(address.group(1), Integer.parseInt(address.group(2)))) {
                        DataSource forwarded = new DataSource(); forwarded.setType("DM"); forwarded.setDriverConfig(dm.getDriverConfig()); forwarded.setUrl(dm.getUrl().replace(address.group(), "jdbc:dm://127.0.0.1:" + proxy.port()));
                        forwarded.setUserName(dm.getUserName()); forwarded.setPassword(dm.getPassword()); forwarded.setExtendInfo(dm.getExtendInfo());
                        MigrationConnections connections = new MigrationConnections() {
                            @Override public DataSource authorized(Long id) { return dm; }
                            @Override public Connection open(DataSource ignored) throws SQLException {
                                // Source and target descriptors are distinct by identity in the service.
                                return raw.open(ignored);
                            }
                        };
                        DataSource sourceInfo = dm, targetInfo = forwarded;
                        MigrationConnections routed = new MigrationConnections() {
                            @Override public DataSource authorized(Long id) { return id == 1L ? sourceInfo : targetInfo; }
                            @Override public DataSource authorized(Long id, com.jd.biz.domain.api.enums.DataSourceAction action) { return authorized(id); }
                            @Override public Connection open(DataSource info) throws SQLException {
                                Connection delegate = connections.open(info);
                                if (info != targetInfo) return delegate;
                                return MigrationTransferTest.proxy(Connection.class, (object, method, args) -> {
                                    if ("COMMIT".equals(phase) && method.getName().equals("commit") && armed.compareAndSet(true, false)) proxy.cut();
                                    try {
                                        Object result = method.invoke(delegate, args);
                                        if ("COMMIT_RESPONSE".equals(phase) && method.getName().equals("commit") && armed.compareAndSet(true, false)) { proxy.cut(); throw new SQLException("QA response failure after actual commit", "08006"); }
                                        if ("BATCH".equals(phase) && result instanceof PreparedStatement && ((String) args[0]).startsWith("INSERT")) {
                                            PreparedStatement statement = (PreparedStatement) result;
                                            return MigrationTransferTest.proxy(PreparedStatement.class, (o, call, values) -> {
                                                if (call.getName().equals("executeBatch") && armed.compareAndSet(true, false)) proxy.cut();
                                                try { return call.invoke(statement, values); } catch (InvocationTargetException error) { throw error.getCause(); }
                                            });
                                        }
                                        return result;
                                    } catch (InvocationTargetException error) { throw error.getCause(); }
                                });
                            }
                        };
                        service = new MigrationService(MigrationWorkflowLiveTest.persistentMapper(raw, dm, store), routed, new MigrationMetadata());
                        Config config = new Config(); config.name = "隔离 TCP 断连 " + phase; config.sourceId = 1L; config.targetId = 2L; config.sourceSchema = config.targetSchema = dm.getUserName(); config.commitSize = 1; config.parallelism = 2;
                        Mapping table = new Mapping(); table.source = source; table.target = target; config.tables.add(table);
                        Task draft = service.save(null, 42L, config), checked = service.precheck(draft.id, 42L); assertEquals("MIG_READY", checked.status);
                        StartRequest start = new StartRequest(); start.offlineConfirmed = true; start.fingerprint = checked.assessment.fingerprint; service.start(draft.id, 42L, start);
                        Task failed = await(service, draft.id); assertFalse(armed.get()); assertEquals("MIG_NEEDS_REVIEW", failed.status); assertEquals(0, failed.committedRows);
                        assertNotEquals("MIG_COMPLETED", failed.status); assertNotNull(MigrationMetadata.objectType(database, dm.getUserName(), target));
                        Task reviewed = service.reviewObject(draft.id, 42L, failed.objects.get(0).source); assertNotNull(reviewed.message, reviewed.resumeFingerprint);
                        start.sourceObject = reviewed.resumeObject; start.fingerprint = reviewed.resumeFingerprint; start.reviewConfirmed = true;
                        service.resume(draft.id, 42L, start); Task finished = await(service, draft.id); assertEquals(finished.message, "MIG_COMPLETED", finished.status); assertEquals(0, finished.inFlightCommits);
                        if ("COMMIT_RESPONSE".equals(phase)) assertEquals("The real committed row must be reused, not duplicated", 1, finished.objects.get(0).reusedRows);
                        System.out.println("ACTUAL SOCKET CUT " + phase + " PASS: no success/phantom commits; all writers joined; fresh-connection PK review/resume verified");
                        service.shutdown(); service = null;
                    } catch (Exception | AssertionError error) { System.out.println("QA FAULT FAILURE " + phase + ": " + (error instanceof IllegalStateException ? error.getMessage() : MigrationConnections.safeError(error instanceof Exception ? (Exception) error : new Exception(error)))); throw error; }
                }
            } finally {
                if (service != null) service.shutdown(); Collections.reverse(created);
                for (String name : created) { assertTrue(name.startsWith(prefix + "_")); if (MigrationMetadata.objectType(database, dm.getUserName(), name) != null) execute(database, "DROP TABLE " + qualified(dm.getUserName(), name)); }
            }
        }
    }
    private static Task await(MigrationService service, Long id) throws Exception {
        long deadline = System.currentTimeMillis() + 120000; Task task = null; while (System.currentTimeMillis() < deadline) { task = service.get(id, 42L); if (!MigrationService.ACTIVE.contains(task.status)) { Thread.sleep(25); return task; } Thread.sleep(50); } throw new IllegalStateException("Fault QA timed out: " + task.status + "/" + task.stage);
    }
    private static void execute(Connection connection, String sql) throws SQLException { try (Statement statement = connection.createStatement()) { statement.execute(sql); } }
    static class CutProxy implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final ExecutorService threads = Executors.newCachedThreadPool();
        private final String host; private final int upstream;
        CutProxy(String host, int upstream) throws IOException { this.host = host; this.upstream = upstream; server.setSoTimeout(500); threads.submit(() -> {
            while (!server.isClosed()) try { Socket client = server.accept(), remote = new Socket(this.host, this.upstream); client.setTcpNoDelay(true); remote.setTcpNoDelay(true); sockets.add(client); sockets.add(remote); threads.submit(() -> relay(client, remote)); threads.submit(() -> relay(remote, client)); }
            catch (SocketTimeoutException idle) { }
            catch (IOException error) { if (!server.isClosed()) throw new IllegalStateException(error); }
        }); }
        int port() { return server.getLocalPort(); }
        private void relay(Socket from, Socket to) { try { byte[] buffer = new byte[8192]; int count; InputStream input = from.getInputStream(); OutputStream output = to.getOutputStream(); while ((count = input.read(buffer)) >= 0) { output.write(buffer, 0, count); output.flush(); } } catch (IOException ignored) { } finally { closeSocket(from); closeSocket(to); } }
        private void closeSocket(Socket socket) {
            try { socket.setSoLinger(true, 0); } catch (IOException ignored) { }
            // Explicit shutdown also wakes relay reads in the ARM64 emulator; close alone left orphan relay threads.
            try { socket.shutdownOutput(); } catch (IOException ignored) { }
            try { socket.shutdownInput(); } catch (IOException ignored) { }
            try { socket.close(); } catch (IOException ignored) { }
        }
        void cut() { for (Socket socket : sockets) closeSocket(socket); sockets.clear(); }
        @Override public void close() throws IOException { server.close(); cut(); threads.shutdownNow(); }
    }
}
