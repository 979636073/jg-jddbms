package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.GetBlobRequest;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

import static org.junit.Assert.*;

public class BlobReadServiceTest {
    private final BlobReadService service = new BlobReadService();
    private int statementCloses, resultCloses, connectionCloses, cancels;
    private int columnType = Types.BLOB;
    private List<String> primaryKeys = Collections.singletonList("ID");

    @After public void clearContext() { Chat2DBContext.removeContext(); }

    @Test public void previewStopsAfterLimitWithoutReadingLargeBody() throws Exception {
        CountingInput input = new CountingInput(128L * 1024 * 1024);
        context(input);
        BlobReadService.Preview preview = service.preview(request());
        assertTrue(preview.isTooLarge());
        assertNull(preview.getBase64());
        assertEquals(24, input.read);
        assertEquals(input.size, preview.getByteLength());
        assertEquals(1, input.closes);
        assertEquals(1, statementCloses);
        assertEquals(1, resultCloses);
    }

    @Test public void distinguishesNullEmptyAndPreviewLimit() throws Exception {
        context(null);
        assertTrue(service.preview(request()).isNullValue());
        context(new CountingInput(0));
        assertEquals("", service.preview(request()).getBase64());
        context(new CountingInput(BlobReadService.PREVIEW_LIMIT));
        BlobReadService.Preview exact = service.preview(request());
        assertFalse(exact.isTooLarge());
        assertEquals(BlobReadService.PREVIEW_LIMIT, Base64.getDecoder().decode(exact.getBase64()).length);
    }

    @Test public void boundsPreviewEvenWhenDriverCannotReportLength() throws Exception {
        columnType = Types.VARBINARY;
        CountingInput input = new CountingInput(128L * 1024 * 1024);
        context(input);
        BlobReadService.Preview preview = service.preview(request());
        assertTrue(preview.isTooLarge());
        assertNull(preview.getBase64());
        assertEquals(BlobReadService.PREVIEW_LIMIT + 1, input.read);
        assertEquals(1, input.closes);
    }

    @Test public void downloadsLargeBodyWithSmallChunksAndClosesOnDisconnect() throws Exception {
        CountingInput input = new CountingInput(96L * 1024 * 1024);
        context(input);
        final long[] written = {0};
        ServletOutputStream output = output((bytes, offset, size) -> { assertTrue(size <= 8192); written[0] += size; });
        service.download(request(), response(output));
        assertEquals(input.size, written[0]);
        assertEquals(1, input.closes);
        CountingInput disconnected = new CountingInput(1024 * 1024);
        context(disconnected);
        try {
            service.download(request(), response(output((bytes, offset, size) -> { throw new IOException("client disconnected"); })));
            fail("Expected disconnected download to stop");
        } catch (IOException expected) { assertEquals("client disconnected", expected.getMessage()); }
        assertEquals(8192, disconnected.read);
        assertEquals(1, disconnected.closes);
        assertEquals(2, statementCloses);
        assertEquals(2, resultCloses);
    }

    @Test public void cancelOnlyAffectsTheSameUserAndConnectionAndReleasesResources() throws Exception {
        GetBlobRequest request = request();
        CountingInput input = new CountingInput(1024 * 1024) {
            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                try {
                    Chat2DBContext.getConnectInfo().setLoginUser("other");
                    assertFalse(service.cancel(request));
                    Chat2DBContext.getConnectInfo().setLoginUser("test");
                    request.setDataSourceId(2L);
                    assertFalse(service.cancel(request));
                    request.setDataSourceId(1L);
                    assertTrue(service.cancel(request));
                    assertEquals(1, connectionCloses);
                    assertFalse(service.cancel(request));
                } catch (SQLException e) { throw new IOException(e); }
                return super.read(buffer, offset, length);
            }
        };
        context(input);
        try { service.preview(request); fail("Expected cancelled read"); }
        catch (BusinessException expected) { assertTrue(expected.getMessage().contains("取消")); }
        assertEquals(1, cancels);
        assertEquals(1, connectionCloses);
        assertEquals(1, input.closes);
        assertEquals(1, resultCloses);
        assertEquals(1, statementCloses);
        assertFalse(service.cancel(request));
    }

    @Test public void usesPrimaryKeyAndAliasesInsteadOfQueryOrderOrRowNumber() throws Exception {
        Connection connection = context(new CountingInput(4));
        GetBlobRequest request = query("SELECT ID AS K, PAYLOAD AS P FROM \"TEST-TEST\".T ORDER BY ID DESC",
                Arrays.asList("K", "P"), Arrays.asList("27", "A"));
        request.setBlobColumn("P"); request.setRowIndex(100);
        BlobReadService.Lookup lookup = service.lookup(request, connection);
        assertEquals("SELECT \"PAYLOAD\" FROM \"TEST-TEST\".\"T\" WHERE \"ID\" = ?", lookup.sql);
        assertEquals(Collections.singletonList("27"), lookup.values);
    }

    @Test public void rejectsAmbiguousResultsAndMissingPrimaryKeys() throws Exception {
        Connection connection = context(new CountingInput(4));
        for (String sql : new String[]{"SELECT PAYLOAD FROM T", "SELECT a.ID, a.PAYLOAD FROM T a JOIN T b ON a.ID=b.ID",
                "SELECT DISTINCT ID, PAYLOAD FROM T", "SELECT ID, PAYLOAD FROM T UNION ALL SELECT ID, PAYLOAD FROM T",
                "SELECT ID, PAYLOAD FROM T GROUP BY ID,PAYLOAD", "DELETE FROM T"}) {
            GetBlobRequest request = query(sql, Collections.singletonList("PAYLOAD"), Collections.singletonList("A"));
            try { service.lookup(request, connection); fail(sql); }
            catch (BusinessException expected) { assertTrue(expected.getMessage().contains("主键")); }
        }
    }

    @Test public void bindsEveryCompositeKeyWithoutChangingStringValues() throws Exception {
        primaryKeys = Arrays.asList("ID", "SEQ");
        Connection connection = context(new CountingInput(4));
        GetBlobRequest request = query("SELECT ID,SEQ,PAYLOAD FROM T", Arrays.asList("ID", "SEQ", "PAYLOAD"), Arrays.asList("<tag>&'", "2", "A"));
        BlobReadService.Lookup lookup = service.lookup(request, connection);
        assertEquals("SELECT \"PAYLOAD\" FROM \"TEST-TEST\".\"T\" WHERE \"ID\" = ? AND \"SEQ\" = ?", lookup.sql);
        assertEquals(Arrays.asList("<tag>&'", "2"), lookup.values);
        request.setResultColumns(Arrays.asList("ID", "PAYLOAD"));
        request.setResultValues(Arrays.asList("<tag>&'", "A"));
        try { service.lookup(request, connection); fail("Partial composite key must be rejected"); }
        catch (BusinessException expected) { assertTrue(expected.getMessage().contains("主键")); }
    }

    private GetBlobRequest request() {
        GetBlobRequest request = new GetBlobRequest();
        request.setDataSourceId(1L); request.setSchemaName("TEST-TEST"); request.setTableName("T");
        request.setBlobColumn("PAYLOAD"); request.setRowId("27"); request.setReadId("read-1");
        return request;
    }
    private GetBlobRequest query(String sql, List<String> columns, List<String> values) {
        GetBlobRequest request = request(); request.setRowId(null); request.setSql(sql);
        request.setResultColumns(columns); request.setResultValues(values); return request;
    }
    private Connection context(InputStream input) {
        final int[] row = {0};
        ResultSet result = proxy(ResultSet.class, (method, args) -> {
            if (method.equals("next")) return row[0]++ == 0;
            if (method.equals("getBinaryStream")) return input;
            if (method.equals("getMetaData")) return proxy(ResultSetMetaData.class, (name, parameters) -> columnType);
            if (method.equals("getBlob")) return input == null ? null : proxy(Blob.class, (name, parameters) ->
                    name.equals("getBinaryStream") ? input : name.equals("length") ? ((CountingInput) input).size : null);
            if (method.equals("close")) resultCloses++;
            return null;
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> {
            if (method.equals("executeQuery")) return result;
            if (method.equals("close")) statementCloses++;
            if (method.equals("cancel")) cancels++;
            return null;
        });
        DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (method, args) -> {
            final int[] keyRow = {0};
            return proxy(ResultSet.class, (name, parameters) -> name.equals("next") ? keyRow[0]++ < primaryKeys.size() : name.equals("getString") ? primaryKeys.get(keyRow[0] - 1) : null);
        });
        Connection connection = proxy(Connection.class, (method, args) -> {
            if (method.equals("close")) connectionCloses++;
            return method.equals("isClosed") ? false : method.equals("prepareStatement") ? statement : method.equals("getMetaData") ? metadata : null;
        });
        ConnectInfo info = new ConnectInfo(); info.setDbType("DM"); info.setLoginUser("test");
        info.setDriverConfig(new DriverConfig()); info.setConnection(connection); Chat2DBContext.putContext(info);
        return connection;
    }
    private HttpServletResponse response(ServletOutputStream output) { return proxy(HttpServletResponse.class, (method, args) -> method.equals("getOutputStream") ? output : null); }
    private ServletOutputStream output(Write write) {
        return new ServletOutputStream() {
            public boolean isReady() { return true; }
            public void setWriteListener(WriteListener listener) { }
            public void write(int value) throws IOException { write.write(new byte[]{(byte) value}, 0, 1); }
            public void write(byte[] bytes, int offset, int length) throws IOException { write.write(bytes, offset, length); }
        };
    }
    @FunctionalInterface private interface Write { void write(byte[] bytes, int offset, int length) throws IOException; }
    @FunctionalInterface private interface Call { Object call(String name, Object[] args) throws Throwable; }
    private <T> T proxy(Class<T> type, Call call) { return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
            (object, method, args) -> call.call(method.getName(), args))); }
    private static class CountingInput extends InputStream {
        final long size; long read; int closes;
        CountingInput(long size) { this.size = size; }
        public int read() { if (read == size) return -1; read++; return 0; }
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (read == size) return -1;
            int count = (int) Math.min(length, size - read); Arrays.fill(buffer, offset, offset + count, (byte) 0); read += count; return count;
        }
        public void close() { closes++; }
    }
}
