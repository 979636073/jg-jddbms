package com.jd.biz.domain.core.dmp;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DmpToolRegistryServiceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldCompareMultiPartVersionsNumerically() {
        assertTrue(DmpToolRegistryService.compareVersions("19.10", "19.3") > 0);
        assertTrue(DmpToolRegistryService.compareVersions("8.1.3.100", "8.1.2.70") > 0);
        assertTrue(DmpToolRegistryService.compareVersions("12.2.0", "12.2") == 0);
    }

    @Test
    public void shouldRegisterDisableAndDeleteDetectedToolPackage() throws Exception {
        DmpToolRegistryService service = new DmpToolRegistryService();
        Field rootField = DmpToolRegistryService.class.getDeclaredField("configuredRoot");
        rootField.setAccessible(true);
        rootField.set(service, tempFolder.newFolder("tools").getAbsolutePath());

        MultipartFile packageFile = multipartFile("oracle-legacy.zip", oracleLegacyZip());
        DmpToolPackage toolPackage = service.upload(packageFile, "19.3");

        assertEquals("ORACLE", toolPackage.getDatabaseType());
        assertEquals(DmpToolRegistryService.ORACLE_LEGACY, toolPackage.getToolFamily());
        assertEquals("19.3", toolPackage.getVersion());
        assertEquals("WINDOWS", toolPackage.getOs());
        assertEquals("X86_64", toolPackage.getArch());
        assertTrue(toolPackage.getEnabled());

        service.setEnabled(toolPackage.getId(), false);
        assertFalse(service.list().get(0).getEnabled());
        service.delete(toolPackage.getId());
        assertTrue(service.list().isEmpty());
    }

    private byte[] oracleLegacyZip() throws Exception {
        byte[] executable = new byte[512];
        executable[0] = 'M';
        executable[1] = 'Z';
        ByteBuffer.wrap(executable, 60, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(128);
        executable[128] = 'P';
        executable[129] = 'E';
        ByteBuffer.wrap(executable, 132, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) 0x8664);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : new String[]{"bin/exp.exe", "bin/imp.exe"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(executable);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private MultipartFile multipartFile(String name, byte[] content) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return name;
            }

            @Override
            public String getContentType() {
                return "application/zip";
            }

            @Override
            public boolean isEmpty() {
                return content.length == 0;
            }

            @Override
            public long getSize() {
                return content.length;
            }

            @Override
            public byte[] getBytes() {
                return content;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(content);
            }

            @Override
            public void transferTo(File dest) throws IOException {
                try (FileOutputStream output = new FileOutputStream(dest)) {
                    output.write(content);
                }
            }
        };
    }
}
