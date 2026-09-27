package com.jd.biz.domain.core.dmp;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.jd.common.config.HzbConfig;
import com.jd.common.tools.base.excption.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class DmpToolRegistryService {

    public static final String ORACLE_DATAPUMP = "ORACLE_DATAPUMP";
    public static final String ORACLE_LEGACY = "ORACLE_LEGACY";
    public static final String DM_DEXP = "DM_DEXP";
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_DETECTION_FAILED = "DETECTION_FAILED";

    private static final Pattern VERSION_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,3}(?:\\.\\d{1,4}){1,4})(?!\\d)");
    private static final int MAX_ZIP_ENTRIES = 10000;

    @Value("${dmp.tools.root:}")
    private String configuredRoot;

    public synchronized List<DmpToolPackage> list() {
        List<DmpToolPackage> packages = readRegistry();
        packages.forEach(item -> item.setCurrentPlatformCompatible(
                currentOs().equalsIgnoreCase(item.getOs()) && currentArch().equalsIgnoreCase(item.getArch())));
        packages.sort(Comparator.comparing(DmpToolPackage::getCreatedTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return packages;
    }

    public synchronized DmpToolPackage upload(MultipartFile file, String fallbackVersion) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择客户端工具包");
        }
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("tool.zip");
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new BusinessException("客户端工具包必须为 ZIP 格式");
        }

        Path staging = root().resolve("staging").resolve(UUID.randomUUID().toString()).normalize();
        Path archive = staging.resolve("package.zip");
        try {
            Files.createDirectories(staging);
            try (InputStream input = new BufferedInputStream(file.getInputStream())) {
                Files.copy(input, archive, StandardCopyOption.REPLACE_EXISTING);
            }
            String checksum = sha256(archive);
            Path extracted = staging.resolve("content");
            unzip(archive, extracted);
            DetectedTool detected = detect(extracted, fallbackVersion);

            String id = UUID.randomUUID().toString();
            Path destination = root().resolve("packages").resolve(id).normalize();
            Files.createDirectories(destination.getParent());
            Files.move(extracted, destination, StandardCopyOption.ATOMIC_MOVE);

            DmpToolPackage toolPackage = new DmpToolPackage();
            toolPackage.setId(id);
            toolPackage.setDatabaseType(detected.databaseType);
            toolPackage.setToolFamily(detected.toolFamily);
            toolPackage.setVersion(detected.version);
            toolPackage.setOs(detected.os);
            toolPackage.setArch(detected.arch);
            toolPackage.setExportExecutable(destination.resolve(detected.exportRelativePath).toString());
            toolPackage.setImportExecutable(destination.resolve(detected.importRelativePath).toString());
            toolPackage.setPackagePath(destination.toString());
            toolPackage.setOriginalFileName(originalName);
            toolPackage.setChecksum(checksum);
            toolPackage.setEnabled(STATUS_AVAILABLE.equals(detected.status));
            toolPackage.setStatus(detected.status);
            toolPackage.setStatusMessage(detected.message);
            toolPackage.setCreatedTime(LocalDateTime.now().toString());
            toolPackage.setCurrentPlatformCompatible(currentOs().equalsIgnoreCase(detected.os)
                    && currentArch().equalsIgnoreCase(detected.arch));

            List<DmpToolPackage> packages = readRegistry();
            packages.add(toolPackage);
            writeRegistry(packages);
            return toolPackage;
        } catch (BusinessException e) {
            deleteRecursively(staging);
            throw e;
        } catch (Exception e) {
            deleteRecursively(staging);
            throw new BusinessException("客户端工具包处理失败：" + e.getMessage());
        } finally {
            deleteRecursively(staging);
        }
    }

    public synchronized DmpToolPackage setEnabled(String id, boolean enabled) {
        List<DmpToolPackage> packages = readRegistry();
        DmpToolPackage toolPackage = packages.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("客户端工具不存在"));
        if (enabled && !STATUS_AVAILABLE.equals(toolPackage.getStatus())) {
            throw new BusinessException("检测失败的客户端工具不能启用");
        }
        toolPackage.setEnabled(enabled);
        writeRegistry(packages);
        return toolPackage;
    }

    public synchronized void delete(String id) {
        List<DmpToolPackage> packages = readRegistry();
        DmpToolPackage toolPackage = packages.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("客户端工具不存在"));
        packages.remove(toolPackage);
        writeRegistry(packages);

        Path packagesRoot = root().resolve("packages").toAbsolutePath().normalize();
        Path target = Paths.get(toolPackage.getPackagePath()).toAbsolutePath().normalize();
        if (target.startsWith(packagesRoot) && !target.equals(packagesRoot)) {
            deleteRecursively(target);
        }
    }

    public synchronized DmpToolPackage select(String id, String databaseType, String toolFamily,
                                               boolean export, String serverVersion) {
        List<DmpToolPackage> packages = readRegistry();
        if (StrUtil.isNotBlank(id)) {
            DmpToolPackage selected = packages.stream()
                    .filter(item -> id.equals(item.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("指定的客户端工具不存在"));
            validateSelected(selected, databaseType, toolFamily, export);
            return selected;
        }

        String currentOs = currentOs();
        String currentArch = currentArch();
        List<DmpToolPackage> candidates = packages.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> STATUS_AVAILABLE.equals(item.getStatus()))
                .filter(item -> databaseType.equalsIgnoreCase(item.getDatabaseType()))
                .filter(item -> StrUtil.isBlank(toolFamily) || toolFamily.equalsIgnoreCase(item.getToolFamily()))
                .filter(item -> currentOs.equalsIgnoreCase(item.getOs()))
                .filter(item -> currentArch.equalsIgnoreCase(item.getArch()))
                .filter(item -> Files.isRegularFile(Paths.get(export ? item.getExportExecutable() : item.getImportExecutable())))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return null;
        }

        Comparator<DmpToolPackage> comparator = (left, right) -> compareVersions(left.getVersion(), right.getVersion());
        List<DmpToolPackage> compatible = candidates.stream()
                .filter(item -> StrUtil.isBlank(serverVersion) || compareVersions(item.getVersion(), serverVersion) <= 0)
                .collect(Collectors.toList());
        if (!compatible.isEmpty()) {
            candidates = compatible;
        }
        candidates.sort(comparator.reversed());
        return candidates.get(0);
    }

    private void validateSelected(DmpToolPackage selected, String databaseType, String toolFamily, boolean export) {
        if (!Boolean.TRUE.equals(selected.getEnabled()) || !STATUS_AVAILABLE.equals(selected.getStatus())) {
            throw new BusinessException("指定的客户端工具当前不可用");
        }
        if (!databaseType.equalsIgnoreCase(selected.getDatabaseType())) {
            throw new BusinessException("指定工具与当前数据库类型不匹配");
        }
        if (StrUtil.isNotBlank(toolFamily) && !toolFamily.equalsIgnoreCase(selected.getToolFamily())) {
            throw new BusinessException("指定工具与选择的DMP格式不匹配");
        }
        if (!currentOs().equalsIgnoreCase(selected.getOs()) || !currentArch().equalsIgnoreCase(selected.getArch())) {
            throw new BusinessException("指定工具与当前运行平台不匹配");
        }
        Path executable = Paths.get(export ? selected.getExportExecutable() : selected.getImportExecutable());
        if (!Files.isRegularFile(executable)) {
            throw new BusinessException("指定工具的可执行文件不存在");
        }
    }

    private DetectedTool detect(Path extracted, String fallbackVersion) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(extracted)) {
            files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        Path expdp = findExecutable(files, "expdp");
        Path impdp = findExecutable(files, "impdp");
        Path exp = findExecutable(files, "exp");
        Path imp = findExecutable(files, "imp");
        Path dexp = findExecutable(files, "dexp");
        Path dimp = findExecutable(files, "dimp");

        int familyCount = (expdp != null && impdp != null ? 1 : 0)
                + (exp != null && imp != null ? 1 : 0)
                + (dexp != null && dimp != null ? 1 : 0);
        if (familyCount == 0) {
            throw new BusinessException("未找到完整的导出/导入工具对（expdp/impdp、exp/imp或dexp/dimp）");
        }
        if (familyCount > 1) {
            throw new BusinessException("一个工具包只能包含一种工具族，请拆分后分别上传");
        }

        Path exportExecutable;
        Path importExecutable;
        String databaseType;
        String family;
        if (expdp != null && impdp != null) {
            exportExecutable = expdp;
            importExecutable = impdp;
            databaseType = "ORACLE";
            family = ORACLE_DATAPUMP;
        } else if (exp != null && imp != null) {
            exportExecutable = exp;
            importExecutable = imp;
            databaseType = "ORACLE";
            family = ORACLE_LEGACY;
        } else {
            exportExecutable = dexp;
            importExecutable = dimp;
            databaseType = "DM";
            family = DM_DEXP;
        }

        makeExecutable(exportExecutable);
        makeExecutable(importExecutable);
        BinaryPlatform platform = detectPlatform(exportExecutable);
        VersionResult versionResult = detectVersion(exportExecutable, platform);
        String version = versionResult.version;
        String message = versionResult.output;
        if (StrUtil.isBlank(version) && isValidVersion(fallbackVersion)) {
            version = fallbackVersion.trim();
            message = "未能从客户端输出中解析版本，已使用上传时填写的版本。";
        }
        String status = StrUtil.isBlank(version) ? STATUS_DETECTION_FAILED : STATUS_AVAILABLE;
        if (StrUtil.isBlank(message)) {
            message = STATUS_AVAILABLE.equals(status) ? "客户端工具检测通过" : "无法读取客户端版本，请填写版本后重新上传";
        }
        return new DetectedTool(databaseType, family, version == null ? "UNKNOWN" : version,
                platform.os, platform.arch, extracted.relativize(exportExecutable),
                extracted.relativize(importExecutable), status, message);
    }

    private Path findExecutable(List<Path> files, String name) {
        String plain = name.toLowerCase(Locale.ROOT);
        String windows = plain + ".exe";
        return files.stream()
                .filter(path -> {
                    String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return fileName.equals(plain) || fileName.equals(windows);
                })
                .findFirst().orElse(null);
    }

    private BinaryPlatform detectPlatform(Path executable) throws IOException {
        byte[] header = new byte[256];
        int length;
        try (InputStream input = Files.newInputStream(executable)) {
            length = input.read(header);
        }
        if (length >= 20 && header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
            boolean littleEndian = header[5] == 1;
            ByteBuffer buffer = ByteBuffer.wrap(header).order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            int machine = buffer.getShort(18) & 0xffff;
            return new BinaryPlatform("LINUX", machine == 183 ? "ARM64" : machine == 62 ? "X86_64" : "UNKNOWN");
        }
        if (length >= 64 && header[0] == 'M' && header[1] == 'Z') {
            int peOffset = ByteBuffer.wrap(header, 60, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (peOffset + 6 <= length) {
                int machine = ByteBuffer.wrap(header, peOffset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff;
                return new BinaryPlatform("WINDOWS", machine == 0xaa64 ? "ARM64" : machine == 0x8664 ? "X86_64" : "X86");
            }
            return new BinaryPlatform("WINDOWS", "UNKNOWN");
        }
        return new BinaryPlatform(currentOs(), currentArch());
    }

    private VersionResult detectVersion(Path executable, BinaryPlatform platform) {
        if (!currentOs().equalsIgnoreCase(platform.os) || !currentArch().equalsIgnoreCase(platform.arch)) {
            return new VersionResult(null, "工具包平台与当前服务不同，无法执行自动版本检测");
        }
        Path output = executable.getParent().resolve(".version-detection.log");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(executable.toString(), "HELP=Y");
            builder.directory(executable.getParent().toFile());
            configureDetectionEnvironment(builder, executable);
            builder.redirectErrorStream(true);
            builder.redirectOutput(output.toFile());
            process = builder.start();
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            String text = Files.exists(output)
                    ? new String(Files.readAllBytes(output), StandardCharsets.UTF_8)
                    : "";
            String version = parseVersion(text, executable.getFileName().toString());
            return new VersionResult(version, compact(text));
        } catch (Exception e) {
            return new VersionResult(null, "版本检测命令执行失败：" + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            try {
                Files.deleteIfExists(output);
            } catch (IOException ignored) {
            }
        }
    }

    private void configureDetectionEnvironment(ProcessBuilder builder, Path executable) {
        Path bin = executable.toAbsolutePath().getParent();
        Path home = bin != null && bin.getFileName() != null
                && "bin".equalsIgnoreCase(bin.getFileName().toString()) ? bin.getParent() : bin;
        if (bin == null || home == null) {
            return;
        }
        String pathKey = builder.environment().containsKey("Path") ? "Path" : "PATH";
        String originalPath = Optional.ofNullable(builder.environment().get(pathKey)).orElse("");
        builder.environment().put(pathKey, bin + java.io.File.pathSeparator + originalPath);
        builder.environment().put("ORACLE_HOME", home.toString());
        builder.environment().put("DM_HOME", home.toString());
        if ("LINUX".equals(currentOs())) {
            String originalLibraries = Optional.ofNullable(builder.environment().get("LD_LIBRARY_PATH")).orElse("");
            builder.environment().put("LD_LIBRARY_PATH",
                    home.resolve("lib") + java.io.File.pathSeparator + originalLibraries);
        }
    }

    private String parseVersion(String output, String executableName) {
        if (StrUtil.isBlank(output)) {
            return null;
        }
        String toolName = executableName.toLowerCase(Locale.ROOT).replace(".exe", "");
        for (String line : output.split("\\r?\\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (!lower.contains("release") && !lower.contains("version") && !lower.contains("版本")
                    && !lower.contains(toolName)) {
                continue;
            }
            Matcher matcher = VERSION_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String compact(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 300 ? compact.substring(0, 300) : compact;
    }

    private boolean isValidVersion(String version) {
        return StrUtil.isNotBlank(version) && version.trim().matches("[0-9]+(?:\\.[0-9A-Za-z_-]+)*");
    }

    private void makeExecutable(Path path) {
        if ("LINUX".equals(currentOs())) {
            path.toFile().setExecutable(true, false);
        }
    }

    private void unzip(Path archive, Path destination) throws IOException {
        Files.createDirectories(destination);
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new BusinessException("工具包文件数量超过限制");
                }
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) {
                    throw new BusinessException("工具包包含非法路径");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder value = new StringBuilder();
        for (byte item : digest.digest()) {
            value.append(String.format("%02x", item));
        }
        return value.toString();
    }

    private List<DmpToolPackage> readRegistry() {
        Path registry = registryFile();
        if (!Files.isRegularFile(registry)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(JSON.parseArray(new String(Files.readAllBytes(registry), StandardCharsets.UTF_8), DmpToolPackage.class));
        } catch (Exception e) {
            throw new BusinessException("客户端工具清单读取失败：" + e.getMessage());
        }
    }

    private void writeRegistry(List<DmpToolPackage> packages) {
        Path registry = registryFile();
        Path temp = registry.resolveSibling("registry.json.tmp");
        try {
            Files.createDirectories(registry.getParent());
            Files.write(temp, JSON.toJSONString(packages, JSONWriter.Feature.PrettyFormat).getBytes(StandardCharsets.UTF_8));
            Files.move(temp, registry, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new BusinessException("客户端工具清单保存失败：" + e.getMessage());
        }
    }

    private Path registryFile() {
        return root().resolve("registry.json");
    }

    private Path root() {
        String root = StrUtil.isBlank(configuredRoot)
                ? Paths.get(HzbConfig.getProfile(), "dmp-tools").toString()
                : configuredRoot;
        return Paths.get(root).toAbsolutePath().normalize();
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException e) {
                    log.warn("删除临时客户端文件失败：{}", item, e);
                }
            });
        } catch (IOException e) {
            log.warn("清理客户端目录失败：{}", path, e);
        }
    }

    static int compareVersions(String left, String right) {
        int[] leftParts = numericParts(left);
        int[] rightParts = numericParts(right);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? leftParts[i] : 0;
            int rightValue = i < rightParts.length ? rightParts[i] : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static int[] numericParts(String version) {
        if (version == null) {
            return new int[0];
        }
        Matcher matcher = Pattern.compile("\\d+").matcher(version);
        List<Integer> parts = new ArrayList<>();
        while (matcher.find()) {
            try {
                parts.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                parts.add(Integer.MAX_VALUE);
            }
        }
        int[] values = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            values[i] = parts.get(i);
        }
        return values;
    }

    private String currentOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "WINDOWS" : "LINUX";
    }

    private String currentArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.contains("aarch64") || arch.contains("arm64") ? "ARM64"
                : arch.contains("64") ? "X86_64" : "X86";
    }

    private static final class BinaryPlatform {
        private final String os;
        private final String arch;

        private BinaryPlatform(String os, String arch) {
            this.os = os;
            this.arch = arch;
        }
    }

    private static final class VersionResult {
        private final String version;
        private final String output;

        private VersionResult(String version, String output) {
            this.version = version;
            this.output = output;
        }
    }

    private static final class DetectedTool {
        private final String databaseType;
        private final String toolFamily;
        private final String version;
        private final String os;
        private final String arch;
        private final Path exportRelativePath;
        private final Path importRelativePath;
        private final String status;
        private final String message;

        private DetectedTool(String databaseType, String toolFamily, String version, String os, String arch,
                             Path exportRelativePath, Path importRelativePath, String status, String message) {
            this.databaseType = databaseType;
            this.toolFamily = toolFamily;
            this.version = version;
            this.os = os;
            this.arch = arch;
            this.exportRelativePath = exportRelativePath;
            this.importRelativePath = importRelativePath;
            this.status = status;
            this.message = message;
        }
    }
}
