package com.jd.biz.controller.system.util;


import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.dtflys.forest.Forest;
import com.dtflys.forest.utils.TypeReference;
import com.jd.biz.controller.system.vo.AppVersionVO;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.common.util.ConfigUtils;
import com.jd.spi.ssh.SSHManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 系统工具包
 *
 * @author Jiaju Zhuang
 */
@Slf4j
public class SystemUtils {

    /**
     * 停止当前应用
     */
    public static void stop() {
        new Thread(() -> {
            log.info("1秒以后退出应用");
            // 1秒以后自动退出应用
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // 直接系统退出
            log.info("开始退出系统应用");
            SSHManager.close();
            try {
                System.exit(0);
            } catch (Exception ignore) {
            }
        }).start();
    }

    private static final OkHttpClient client = new OkHttpClient();

    private static final String VERSION_URL = "https://sqlgpt.cn/api/version.json";

    private static final String ZIP_FILE_PATH = ConfigUtils.APP_PATH + File.separator + "versions" + File.separator;

    public static void upgrade(AppVersionVO appVersion) {

        String appPath = ConfigUtils.APP_PATH;

        log.info("appPath: {}", appPath);
        if (StringUtils.isBlank(appPath) || !appPath.contains("app")) {
            return;
        }
        try {
            String zipPath = ZIP_FILE_PATH + appVersion.getVersion() + ".zip";

            HttpUtil.downloadFile(appVersion.getHotUpgradeUrl(), ZIP_FILE_PATH + appVersion.getVersion() + ".zip");

            safeUnzip(new File(zipPath), new File(appPath));

            FileUtil.del(zipPath);

            ConfigUtils.updateVersion(appVersion.getVersion());
        } catch (Exception e) {
            log.error("checkVersionUpdates error", e);
        }
    }

    private static void safeUnzip(File archive, File destination) throws IOException {
        Path root = destination.toPath().toAbsolutePath().normalize();
        int entries = 0;
        long totalBytes = 0;
        try (ZipFile zipFile = new ZipFile(archive)) {
            java.util.Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++entries > 10000) {
                    throw new IOException("升级包文件数量超出限制");
                }
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("升级包包含非法路径");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream input = zipFile.getInputStream(entry); OutputStream output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        totalBytes += read;
                        if (totalBytes > 1024L * 1024 * 1024) {
                            throw new IOException("升级包解压大小超出限制");
                        }
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static final String LATEST_VERSION_URL = "http://test.sqlgpt.cn/gateway/api/client/version/check/v3?version=%s&type=%s&userId=%s";

    public static AppVersionVO getLatestVersion(String version, String type, String userId) {
        String url = String.format(LATEST_VERSION_URL, version, type, userId);
        DataResult<AppVersionVO> result = Forest.get(url)
                .connectTimeout(Duration.ofMillis(5000))
                .readTimeout(Duration.ofMillis(10000))
                .execute(new TypeReference<DataResult<AppVersionVO>>() {
                });
        return result.getData();
    }

}
