package com.jd;

import cn.hutool.core.lang.UUID;
import com.jd.common.tools.common.model.ConfigJson;
import com.jd.common.tools.common.util.ConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Indexed;

/**
 * 启动程序
 */
@EnableIntegration
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class})
// 显式扫描主工程和外部 CAS 兼容包；直接声明 ComponentScan 会覆盖 SpringBootApplication 默认扫描范围。
@ComponentScan(basePackages = {"com.jd", "com.easy.cas.client"})
@Indexed
@EnableCaching
@EnableScheduling
@EnableAsync
@Slf4j
public class Application
{
    public static void main(String[] args)
    {
        long s1 = System.currentTimeMillis();
        String currentVersion = ConfigUtils.getLocalVersion();
        ConfigJson configJson = ConfigUtils.getConfig();

        // The unique ID of the entire system will not change after multiple starts
        if (StringUtils.isBlank(configJson.getSystemUuid())) {
            configJson.setSystemUuid(UUID.fastUUID().toString(true));
            ConfigUtils.setConfig(configJson);
        }

        // Represents that the current version has been successfully launched
        if (StringUtils.isNotBlank(currentVersion) && StringUtils.equals(currentVersion,
                configJson.getLatestStartupSuccessVersion())) {
            // Flyway doesn't need to start every time to increase startup speed
            //args = ArrayUtils.add(args, "--spring.flyway.enabled=false");
            log.info("The current version {} has been successfully launched once and will no longer load Flyway.",
                    currentVersion);
        }
        System.out.println("启动耗时：" + (System.currentTimeMillis() - s1) + "ms");
        SpringApplication.run(Application.class, args);
        System.out.println("项目启动成功");
    }
}
