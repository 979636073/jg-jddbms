package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.GetBlobRequest;
import com.jd.biz.util.FileUtils;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DlTemplateBlobTest {

    @After
    public void clearContext() {
        Chat2DBContext.removeContext();
    }

    @Test
    public void readsWholeBlobWithBoundRowId() throws Exception {
        byte[] payload = new byte[300 * 1024 + 1];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) i;
        AtomicReference<String> sql = new AtomicReference<>();
        AtomicReference<String> rowId = new AtomicReference<>();
        useConnection(payload, sql, rowId);

        GetBlobRequest request = request("AA' OR '1'='1");
        String encoded = new DlTemplateServiceImpl().getBlobData(request);

        assertArrayEquals(payload, Base64.getDecoder().decode(encoded));
        assertEquals("SELECT \"IMAGE_DATA\" FROM \"QA\".\"T\" WHERE ROWID = ?", sql.get());
        assertEquals("AA' OR '1'='1", rowId.get());
    }

    @Test
    public void keepsEmptyBlobDistinctFromNull() throws Exception {
        useConnection(new byte[0], new AtomicReference<>(), new AtomicReference<>());
        assertEquals("", new DlTemplateServiceImpl().getBlobData(request("AA")));
        Chat2DBContext.removeContext();
        useConnection(null, new AtomicReference<>(), new AtomicReference<>());
        assertNull(new DlTemplateServiceImpl().getBlobData(request("AA")));
        assertEquals("", FileUtils.getBlobType(new byte[0]));
        assertEquals("", FileUtils.getBlobType(new byte[]{1, 2, 3}));
    }

    @Test
    public void bindsNumericDmRowIdAsLong() throws Exception {
        AtomicReference<String> rowId = new AtomicReference<>();
        useConnection(new byte[]{1}, new AtomicReference<>(), rowId);
        assertEquals("AQ==", new DlTemplateServiceImpl().getBlobData(request("42")));
        assertEquals("42", rowId.get());
    }

    @Test(expected = BusinessException.class)
    public void rejectsInvalidBlobDownloadBeforeWritingResponse() {
        new DlTemplateServiceImpl().exportFile("invalid-base64!", "QA", ".bin", null);
    }

    @Test
    public void detectsArchiveExtensionFromEntries() throws IOException {
        DlTemplateServiceImpl service = new DlTemplateServiceImpl();
        assertEquals(".jar", service.parseFileType(archive("META-INF/MANIFEST.MF")));
        assertEquals(".docx", service.parseFileType(archive("word/document.xml")));
        assertEquals(".xlsx", service.parseFileType(archive("xl/workbook.xml")));
        assertEquals(".pptx", service.parseFileType(archive("ppt/presentation.xml")));
        assertEquals(".zip", service.parseFileType(archive("plain.txt")));
    }

    private String archive(String entryName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(1);
            zip.closeEntry();
        }
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private GetBlobRequest request(String rowId) {
        GetBlobRequest request = new GetBlobRequest();
        request.setSchemaName("QA");
        request.setTableName("T");
        request.setBlobColumn("IMAGE_DATA");
        request.setRowId(rowId);
        return request;
    }

    private void useConnection(byte[] payload, AtomicReference<String> sql, AtomicReference<String> rowId) {
        ResultSet result = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class}, (proxy, method, args) -> {
                    if ("next".equals(method.getName())) return true;
                    if ("getBinaryStream".equals(method.getName()))
                        return payload == null ? null : new ByteArrayInputStream(payload);
                    return defaultValue(method.getReturnType());
                });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class}, (proxy, method, args) -> {
                    if ("setString".equals(method.getName())) rowId.set((String) args[1]);
                    if ("setLong".equals(method.getName())) rowId.set(String.valueOf(args[1]));
                    if ("executeQuery".equals(method.getName())) return result;
                    return defaultValue(method.getReturnType());
                });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class[]{Connection.class}, (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        sql.set((String) args[0]);
                        return statement;
                    }
                    return defaultValue(method.getReturnType());
                });
        ConnectInfo info = new ConnectInfo();
        info.setDbType("oracle");
        info.setDriverConfig(new DriverConfig());
        info.setConnection(connection);
        Chat2DBContext.putContext(info);
    }

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
