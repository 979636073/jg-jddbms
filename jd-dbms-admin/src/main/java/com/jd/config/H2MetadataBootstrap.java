package com.jd.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Initializes only a new embedded metadata database, before application beans access it. */
@Order(Ordered.LOWEST_PRECEDENCE)
public class H2MetadataBootstrap implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment environment = event.getEnvironment();
        if (!Arrays.asList(environment.getActiveProfiles()).contains("h2")) {
            return;
        }
        try {
            Path directory = Paths.get(environment.getProperty("JDDBMS_DATA_DIR", "./data/h2"))
                    .toAbsolutePath().normalize();
            Files.createDirectories(directory);
            boolean existing = Files.exists(directory.resolve("jddbms.mv.db"));
            String url = environment.getProperty("spring.datasource.druid.master.url");
            Class.forName("org.h2.Driver");
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                if (existing) {
                    try (Statement statement = connection.createStatement();
                         ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM JDDBMS.JDDBMS_BOOTSTRAP")) {
                        if (!result.next() || result.getInt(1) != 1) {
                            throw new IllegalStateException("H2 元数据库初始化标记无效");
                        }
                    }
                    return;
                }
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ClassPathResource("db/h2/schema.sql"), "UTF-8"));
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ClassPathResource("db/h2/seed.sql"), "UTF-8"));
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE JDDBMS.JDDBMS_BOOTSTRAP(VERSION INT NOT NULL)");
                    statement.execute("INSERT INTO JDDBMS.JDDBMS_BOOTSTRAP(VERSION) VALUES(1)");
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("H2 元数据库初始化失败；原达梦库未修改。请检查 data/h2 目录和初始化脚本。", error);
        }
    }
}
