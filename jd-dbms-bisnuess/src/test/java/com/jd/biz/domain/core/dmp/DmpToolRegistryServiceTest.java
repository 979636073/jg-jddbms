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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DmpToolRegistryServiceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void shouldAcceptChineseZipNamesInUtf8AndWindowsGbk() throws Exception {
        for (String encoding : new String[]{"UTF-8", "GBK"}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, java.nio.charset.Charset.forName(encoding))) {
                for (String name : new String[]{"客户端/bin/dexp", "客户端/bin/dimp"}) {
                    zip.putNextEntry(new ZipEntry(name)); zip.write(new byte[]{1, 2, 3}); zip.closeEntry();
                }
            }
            DmpToolRegistryService service = serviceWithRoot("chinese-" + encoding);
            DmpToolPackage result = service.upload(multipartFile("达梦客户端.zip", bytes.toByteArray()),
                    "8.1.3", null, null, "LINUX:ARM64");
            assertEquals("DM", result.getDatabaseType());
            assertTrue(Files.isRegularFile(Paths.get(result.getExportExecutable())));
            assertEquals(1, service.list().size());
        }
    }

    @Test
    public void shouldCompareMultiPartVersionsNumerically() {
        assertTrue(DmpToolRegistryService.compareVersions("19.10", "19.3") > 0);
        assertTrue(DmpToolRegistryService.compareVersions("8.1.3.100", "8.1.2.70") > 0);
        assertTrue(DmpToolRegistryService.compareVersions("12.2.0", "12.2") == 0);
    }

    @Test
    public void shouldRecognizeDamengMajorVersionBanner() {
        DmpToolRegistryService service = new DmpToolRegistryService();
        assertEquals("8", service.parseVersion("dexp V8\n用户名:", "dexp.exe"));
        assertEquals("8.1.3.100", service.parseVersion("dexp V8.1.3.100", "dexp.exe"));
    }

    @Test
    public void shouldReadMatchingDamengCompanionBuildVersion() throws Exception {
        DmpToolRegistryService service = new DmpToolRegistryService();
        File bin = tempFolder.newFolder("dm-bin");
        Path executable = new File(bin, "dexp.exe").toPath();
        Files.write(executable, new byte[]{1});
        Files.write(new File(bin, "dmserver.exe").toPath(),
                "other V8.0.0.0 V8.1.4.200-Build(2026.03.30-320063)ENT".getBytes("US-ASCII"));
        assertEquals("8.1.4.200", service.detectDamengCompanionVersion(executable, "8"));
        assertEquals(null, service.detectDamengCompanionVersion(executable, "9"));
    }

    @Test
    public void shouldRegisterDisableAndDeleteDetectedToolPackage() throws Exception {
        DmpToolRegistryService service = new DmpToolRegistryService();
        Field rootField = DmpToolRegistryService.class.getDeclaredField("configuredRoot");
        rootField.setAccessible(true);
        rootField.set(service, tempFolder.newFolder("tools").getAbsolutePath());

        MultipartFile packageFile = multipartFile("oracle-legacy.zip", oracleLegacyZip());
        DmpToolPackage toolPackage = service.upload(packageFile, "19.3", null, null, "LINUX:ARM64");

        assertEquals("ORACLE", toolPackage.getDatabaseType());
        assertEquals(DmpToolRegistryService.ORACLE_LEGACY, toolPackage.getToolFamily());
        assertEquals("19.3", toolPackage.getVersion());
        assertEquals("WINDOWS", toolPackage.getOs());
        assertEquals("X86_64", toolPackage.getArch());
        assertTrue(toolPackage.getEnabled());
        assertTrue(Files.isRegularFile(Paths.get(toolPackage.getExportExecutable())));
        assertTrue(Files.isRegularFile(Paths.get(toolPackage.getImportExecutable())));
        assertEquals(1, service.list().size());

        try {
            service.delete(toolPackage.getId());
            fail("Enabled tool must not be deleted");
        } catch (com.jd.common.tools.base.excption.BusinessException expected) {
            assertTrue(expected.getMessage().contains("停用"));
        }

        service.setEnabled(toolPackage.getId(), false);
        assertFalse(service.list().get(0).getEnabled());
        service.delete(toolPackage.getId());
        assertTrue(service.list().isEmpty());
    }

    @Test
    public void shouldRejectChineseArchiveTraversalForBothEncodings() throws Exception {
        for (String encoding : new String[]{"UTF-8", "GBK"}) {
            for (String name : new String[]{"../越界.txt", "客户端/../../越界.txt", "客户端\\..\\..\\越界.txt", "C:/越界.txt", "/越界.txt"}) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (ZipOutputStream zip = new ZipOutputStream(bytes, java.nio.charset.Charset.forName(encoding))) {
                    zip.putNextEntry(new ZipEntry(name)); zip.write(1); zip.closeEntry();
                }
                DmpToolRegistryService service = serviceWithRoot("paths-" + java.util.UUID.randomUUID());
                try {
                    service.upload(multipartFile("客户端.zip", bytes.toByteArray()), "8.1.3", null, null, "LINUX:ARM64");
                    fail("Archive path must not escape its package directory: " + name);
                } catch (com.jd.common.tools.base.excption.BusinessException expected) {
                    assertTrue(expected.getMessage().contains("路径"));
                }
                assertTrue(service.list().isEmpty());
            }
        }
    }

    @Test
    public void shouldAllowValidUploadAfterCorruptArchiveFails() throws Exception {
        DmpToolRegistryService service = serviceWithRoot("failed-upload-retry");
        try {
            service.upload(multipartFile("broken.zip", new byte[]{1, 2, 3}), "19.3", null, null, "WINDOWS:X86_64");
            fail("Corrupt archive must be rejected");
        } catch (com.jd.common.tools.base.excption.BusinessException expected) {
            assertTrue(expected.getMessage().contains("客户端工具包处理失败"));
        }
        assertTrue(service.list().isEmpty());
        service.upload(multipartFile("valid.zip", oracleLegacyZip()), "19.3", null, null, "WINDOWS:X86_64");
        assertEquals(1, service.list().size());
    }

    @Test
    public void shouldSelectOldestExportToolAndNewestImportTool() throws Exception {
        DmpToolRegistryService service = serviceWithRoot("version-selection-tools");
        DmpToolPackage oldest = service.upload(multipartFile("old.zip", oracleLegacyZipForHost(false)),
                "11.2", null, null, "WINDOWS:X86_64");
        DmpToolPackage newest = service.upload(multipartFile("new.zip", oracleLegacyZipForHost(true)),
                "19.3", null, null, "WINDOWS:X86_64");

        assertEquals(oldest.getId(), service.select(null, "ORACLE",
                DmpToolRegistryService.ORACLE_LEGACY, true, null).getId());
        assertEquals(newest.getId(), service.select(null, "ORACLE",
                DmpToolRegistryService.ORACLE_LEGACY, false, null).getId());
        assertEquals(newest.getId(), service.select(newest.getId(), "ORACLE",
                DmpToolRegistryService.ORACLE_LEGACY, true, null).getId());
    }

    @Test
    public void shouldUseUploadedPlatformOnlyWhenFileHeaderCannotBeDetected() throws Exception {
        DmpToolRegistryService service = new DmpToolRegistryService();
        Field rootField = DmpToolRegistryService.class.getDeclaredField("configuredRoot");
        rootField.setAccessible(true);
        rootField.set(service, tempFolder.newFolder("platform-tools").getAbsolutePath());

        DmpToolPackage toolPackage = service.upload(
                multipartFile("oracle-legacy.zip", oracleLegacyZipWithUnknownHeader()),
                "19.3", null, null, "LINUX:ARM64");

        assertEquals("LINUX", toolPackage.getOs());
        assertEquals("ARM64", toolPackage.getArch());
        assertTrue(toolPackage.getStatusMessage().contains("已使用上传时填写的运行平台"));
    }

    @Test
    public void shouldRejectWrongArchiveChecksumBeforeExtracting() throws Exception {
        DmpToolRegistryService service = new DmpToolRegistryService();
        Field rootField = DmpToolRegistryService.class.getDeclaredField("configuredRoot");
        rootField.setAccessible(true);
        rootField.set(service, tempFolder.newFolder("checksum-tools").getAbsolutePath());
        try {
            service.upload(multipartFile("oracle-legacy.zip", oracleLegacyZip()), "19.3",
                    "https://example.org/official", "0000000000000000000000000000000000000000000000000000000000000000");
            fail("Wrong checksum must be rejected");
        } catch (com.jd.common.tools.base.excption.BusinessException expected) {
            assertTrue(expected.getMessage().contains("SHA-256"));
        }
        assertTrue(service.list().isEmpty());
    }

    @Test
    public void shouldRejectAnotherPackageWithSameToolVersionAndPlatform() throws Exception {
        DmpToolRegistryService service = serviceWithRoot("duplicate-tools");
        service.upload(multipartFile("first.zip", oracleLegacyZip()), "19.3", null, null, "WINDOWS:X86_64");
        try {
            service.upload(multipartFile("same.zip", oracleLegacyZip()), "19.3", null, null,
                    "WINDOWS:X86_64");
            fail("Identical archive must be rejected");
        } catch (com.jd.common.tools.base.excption.BusinessException expected) {
            assertTrue(expected.getMessage().contains("已上传"));
        }
        try {
            service.upload(multipartFile("second.zip", oracleLegacyZipWithMarker()), "19.3", null, null,
                    "WINDOWS:X86_64");
            fail("Duplicate version and platform must be rejected");
        } catch (com.jd.common.tools.base.excption.BusinessException expected) {
            assertTrue(expected.getMessage().contains("同版本、同平台"));
        }
        assertEquals(1, service.list().size());
    }

    @Test
    public void shouldEditVersionAndKeepDetectedPlatform() throws Exception {
        DmpToolRegistryService service = serviceWithRoot("edit-tools");
        DmpToolPackage original = service.upload(multipartFile("first.zip", oracleLegacyZip()),
                "19.3", null, null, "WINDOWS:X86_64");
        DmpToolPackage updated = service.update(original.getId(), "19.4", "WINDOWS:X86_64");
        assertEquals("19.4", updated.getVersion());
        try {
            service.update(original.getId(), "19.5", "LINUX:ARM64");
            fail("Detected binary platform must not be overwritten");
        } catch (com.jd.common.tools.base.excption.BusinessException expected) {
            assertTrue(expected.getMessage().contains("文件头不一致"));
        }
        assertEquals("19.4", service.list().get(0).getVersion());
    }

    @Test
    public void shouldRejectEditThatWouldDuplicateAnotherVersion() throws Exception {
        DmpToolRegistryService service = serviceWithRoot("edit-duplicate-tools");
        service.upload(multipartFile("first.zip", oracleLegacyZip()), "19.3", null, null,
                "WINDOWS:X86_64");
        DmpToolPackage second = service.upload(multipartFile("second.zip", oracleLegacyZipWithMarker()),
                "19.4", null, null, "WINDOWS:X86_64");
        try {
            service.update(second.getId(), "19.3", "WINDOWS:X86_64");
            fail("Edit must not create a duplicate version");
        } catch (com.jd.common.tools.base.excption.BusinessException expected) {
            assertTrue(expected.getMessage().contains("同版本、同平台"));
        }
        assertEquals("19.4", service.list().stream()
                .filter(item -> item.getId().equals(second.getId()))
                .findFirst().get().getVersion());
    }

    private DmpToolRegistryService serviceWithRoot(String name) throws Exception {
        DmpToolRegistryService service = new DmpToolRegistryService();
        Field rootField = DmpToolRegistryService.class.getDeclaredField("configuredRoot");
        rootField.setAccessible(true);
        rootField.set(service, tempFolder.newFolder(name).getAbsolutePath());
        return service;
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

    private byte[] oracleLegacyZipForHost(boolean marker) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        boolean arm64 = System.getProperty("os.arch", "").toLowerCase().matches(".*(aarch64|arm64).*");
        byte[] executable = new byte[512];
        if (windows) {
            executable[0] = 'M'; executable[1] = 'Z';
            ByteBuffer.wrap(executable, 60, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(128);
            executable[128] = 'P'; executable[129] = 'E';
            ByteBuffer.wrap(executable, 132, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) (arm64 ? 0xaa64 : 0x8664));
        } else {
            executable[0] = 0x7f; executable[1] = 'E'; executable[2] = 'L'; executable[3] = 'F'; executable[5] = 1;
            ByteBuffer.wrap(executable, 18, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) (arm64 ? 183 : 62));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : new String[]{windows ? "bin/exp.exe" : "bin/exp", windows ? "bin/imp.exe" : "bin/imp"}) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(executable); zip.closeEntry();
            }
            if (marker) { zip.putNextEntry(new ZipEntry("marker.txt")); zip.write(new byte[]{1}); zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }

    private byte[] oracleLegacyZipWithMarker() throws Exception {
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
            zip.putNextEntry(new ZipEntry("marker.txt"));
            zip.write(new byte[]{1});
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private byte[] oracleLegacyZipWithUnknownHeader() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : new String[]{"bin/exp", "bin/imp"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(new byte[]{1, 2, 3, 4});
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
