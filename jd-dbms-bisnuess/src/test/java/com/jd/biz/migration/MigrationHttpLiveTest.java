package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.common.core.domain.entity.SysUser;
import com.jd.common.core.domain.model.LoginUser;
import com.jd.common.core.redis.RedisCache;
import com.jd.common.utils.SecurityUtil;
import com.jd.framework.config.*;
import com.jd.framework.config.properties.PermitAllUrlProperties;
import com.jd.framework.security.filter.JwtAuthenticationTokenFilter;
import com.jd.framework.security.handle.*;
import com.jd.framework.web.service.TokenService;
import com.jd.framework.web.exception.GlobalExceptionHandler;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import javax.annotation.Resource;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

/** Real loopback HTTP + unchanged production SecurityConfig/JWT/Redis serializer; isolated DB fixtures. */
public class MigrationHttpLiveTest {
    private static MigrationService service;
    private static MigrationConnections connections;
    private static final String QA_PASSWORD = UUID.randomUUID().toString();

    @Configuration
    @EnableAutoConfiguration(excludeName = { "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration", "com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure", "com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration", "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration" })
    @Import({ SecurityConfig.class, RedisConfig.class, RedisCache.class, TokenService.class, JwtAuthenticationTokenFilter.class,
            AuthenticationEntryPointImpl.class, LogoutSuccessHandlerImpl.class, PermitAllUrlProperties.class, GlobalExceptionHandler.class, QaLogin.class })
    public static class QaApplication {
        @Bean public MigrationService migrationService() { return service; }
        @Bean public MigrationController migrationController() { return new MigrationController(service, connections, new MigrationMetadata()); }
        @Bean public UserDetailsService userDetailsService() {
            final String hash = new BCryptPasswordEncoder().encode(QA_PASSWORD);
            return name -> {
                SysUser user = new SysUser(); user.setUserName(name); user.setPassword(hash); user.setUserId("owner".equals(name) ? 42L : 43L);
                return new LoginUser(user.getUserId(), 1L, user, Collections.emptySet());
            };
        }
        @Bean public CorsFilter corsFilter() { return new CorsFilter(new UrlBasedCorsConfigurationSource()); }
    }
    @RestController
    public static class QaLogin {
        @Resource private AuthenticationManager authenticationManager;
        @Resource private TokenService tokenService;
        @PostMapping("/login") public Map<String, String> login(@RequestBody Map<String, String> body) {
            org.springframework.security.core.Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(body.get("username"), body.get("password")));
            return Collections.singletonMap("token", tokenService.createToken((LoginUser) authentication.getPrincipal()));
        }
    }

    @Test public void authenticatedHttpStartsPersistsAndRejectsUnauthenticatedAndOtherOwner() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_MIGRATION_HTTP")));
        MigrationLiveTest environment = new MigrationLiveTest(); DataSource dm = environment.environment("DM"); MigrationConnections raw = new MigrationConnections();
        String prefix = "JQA_HTTP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT), taskTable = prefix + "_M", sourceTable = prefix + "_S", targetTable = prefix + "_T";
        boolean metadataCreated = false, sourceCreated = false; ServletWebServerApplicationContext app = null;
        try (Connection database = raw.open(dm)) {
            try {
                ddl(database, "CREATE TABLE " + qualified(dm.getUserName(), taskTable) + " (ID BIGINT IDENTITY(1,1) PRIMARY KEY, GMT_CREATE TIMESTAMP, GMT_MODIFIED TIMESTAMP, DATA_SOURCE_ID BIGINT, DATABASE_NAME VARCHAR(128), SCHEMA_NAME VARCHAR(128), TABLE_NAME VARCHAR(128), DELETED CHAR(1), USER_ID BIGINT, TASK_TYPE VARCHAR(64), TASK_STATUS VARCHAR(64), TASK_PROGRESS VARCHAR(64), TASK_NAME VARCHAR(256), DOWNLOAD_URL VARCHAR(512), CONTENT BLOB)"); metadataCreated = true;
                ddl(database, "CREATE TABLE " + qualified(dm.getUserName(), sourceTable) + " (ID NUMBER(10,0) PRIMARY KEY)"); sourceCreated = true;
                for (int i = 1; i <= 50; i++) ddl(database, "INSERT INTO " + qualified(dm.getUserName(), sourceTable) + " VALUES(" + i + ")");
                connections = new MigrationConnections() { @Override public DataSource authorized(Long id) { if (SecurityUtil.getUserId() != 42L || id != 1L && id != 2L) throw new IllegalStateException("Not QA owner or datasource"); return dm; } };
                service = new MigrationService(MigrationWorkflowLiveTest.persistentMapper(raw, dm, taskTable), connections, new MigrationMetadata());
                app = (ServletWebServerApplicationContext) new SpringApplicationBuilder(QaApplication.class).properties(
                        "spring.config.location=optional:classpath:/migration-qa-unused.properties", "server.address=127.0.0.1", "server.port=0", "spring.main.banner-mode=off", "logging.level.root=WARN",
                        "spring.redis.host=" + Optional.ofNullable(System.getenv("JDDBMS_MIGRATION_HOST")).orElse("127.0.0.1"), "spring.redis.port=36379",
                        "token.header=Authorization", "token.secret=" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""), "token.expireTime=30").run();
                int port = app.getWebServer().getPort();
                JSONObject anonymous = http(port, "GET", "/api/migration/tasks", null, null); assertEquals(401, anonymous.getIntValue("code"));
                String owner = http(port, "POST", "/login", credentials("owner"), null).getString("token"), other = http(port, "POST", "/login", credentials("other"), null).getString("token"); assertNotNull(owner); assertNotNull(other);
                Config config = new Config(); config.name = "真实认证 HTTP QA"; config.sourceId = 1L; config.targetId = 2L; config.sourceSchema = config.targetSchema = dm.getUserName(); config.rowsPerSecond = 10; config.commitSize = 1; config.parallelism = 2;
                Mapping mapping = new Mapping(); mapping.source = sourceTable; mapping.target = targetTable; config.tables.add(mapping);
                Task draft = task(http(port, "POST", "/api/migration/tasks", config, owner));
                JSONObject forbidden = http(port, "GET", "/api/migration/tasks/" + draft.id, null, other); assertFalse(forbidden.toJSONString(), forbidden.getBooleanValue("success"));
                Task checked = task(http(port, "POST", "/api/migration/tasks/" + draft.id + "/precheck", null, owner)); assertEquals("MIG_READY", checked.status);
                StartRequest request = new StartRequest(); request.fingerprint = checked.assessment.fingerprint; request.offlineConfirmed = true;
                task(http(port, "POST", "/api/migration/tasks/" + draft.id + "/start", request, owner));
                assertFalse(http(port, "POST", "/api/migration/tasks/" + draft.id + "/start", request, owner).getBooleanValue("success"));
                // Fresh independent HTTP GETs simulate reloads; no request-local security context is shared with the background writer.
                Task finished = null; long deadline = System.currentTimeMillis() + 30000;
                while (System.currentTimeMillis() < deadline) { finished = task(http(port, "GET", "/api/migration/tasks/" + draft.id, null, owner)); if (!MigrationService.ACTIVE.contains(finished.status)) break; Thread.sleep(100); }
                assertEquals(finished.message, "MIG_COMPLETED", finished.status); assertEquals(50, finished.committedRows);
                assertEquals("MIG_COMPLETED", task(http(port, "GET", "/api/migration/tasks/" + draft.id + "/report", null, owner)).status);
                System.out.println("REAL HTTP AUTH PASS: BCrypt login; production SecurityConfig/JWT/Redis; anonymous=401; other owner denied; duplicate start denied; actual JDBC/TASK; fresh HTTP requests; 50-row full verification");
            } finally {
                if (app != null) app.close(); else if (service != null) service.shutdown(); SecurityContextHolder.clearContext();
                if (MigrationMetadata.objectType(database, dm.getUserName(), targetTable) != null) ddl(database, "DROP TABLE " + qualified(dm.getUserName(), targetTable));
                if (sourceCreated) ddl(database, "DROP TABLE " + qualified(dm.getUserName(), sourceTable));
                if (metadataCreated) ddl(database, "DROP TABLE " + qualified(dm.getUserName(), taskTable));
            }
        }
    }
    private static Map<String, String> credentials(String username) { Map<String, String> value = new HashMap<>(); value.put("username", username); value.put("password", QA_PASSWORD); return value; }
    private static Task task(JSONObject response) { assertTrue(response.toJSONString(), response.getBooleanValue("success")); return response.getObject("data", Task.class); }
    private static void ddl(Connection connection, String sql) throws SQLException { try (Statement statement = connection.createStatement()) { statement.execute(sql); } }
    private static JSONObject http(int port, String method, String path, Object body, String token) throws IOException {
        HttpURLConnection request = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection(); request.setRequestMethod(method); request.setConnectTimeout(5000); request.setReadTimeout(10000);
        request.setRequestProperty("Content-Type", "application/json"); if (token != null) request.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) { request.setDoOutput(true); try (OutputStream output = request.getOutputStream()) { output.write(JSON.toJSONBytes(body)); } }
        try (InputStream input = request.getResponseCode() >= 400 ? request.getErrorStream() : request.getInputStream()) { ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); return JSON.parseObject(output.toByteArray()); }
        finally { request.disconnect(); }
    }
}
