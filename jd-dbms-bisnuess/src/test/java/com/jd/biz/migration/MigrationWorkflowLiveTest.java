package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.repository.mapper.TaskMapper;
import com.jd.biz.domain.repository.entity.TaskDO;
import com.jd.common.core.domain.model.LoginUser;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.utils.SecurityUtil;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;

/** MVC → real service → real JDBC → actual MyBatis TASK persistence, isolated QA tables only. */
public class MigrationWorkflowLiveTest {
    @Test public void dmToDm() throws Exception { run("DM", "DM"); }
    @Test public void dmToOracle() throws Exception { run("DM", "ORACLE"); }
    @Test public void oracleToOracle() throws Exception { run("ORACLE", "ORACLE"); }
    @Test public void oracleToDm() throws Exception { run("ORACLE", "DM"); }

    static void login(long id) { LoginUser user = new LoginUser(); user.setUserId(id);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList())); }

    private void run(String sourceType, String targetType) throws Exception {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);
        MigrationLiveTest environment = new MigrationLiveTest(); DataSource sourceInfo = environment.environment(sourceType), targetInfo = environment.environment(targetType), storeInfo = environment.environment("DM");
        MigrationConnections raw = new MigrationConnections();
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT), taskName = "JD_QA_X0929_M" + token;
        Config config = new Config(); config.name = "真实 MVC " + sourceType + "-" + targetType; config.sourceId = 700001L; config.targetId = 700002L;
        config.sourceSchema = sourceInfo.getUserName(); config.targetSchema = targetInfo.getUserName(); config.commitSize = 1;
        config.parallelism = 3;
        MigrationConnections connections = new MigrationConnections() {
            @Override public DataSource authorized(Long id) {
                if (SecurityUtil.getUserId() != 700000L) throw new BusinessException("测试身份无权访问数据源");
                if (id.equals(config.sourceId)) return sourceInfo; if (id.equals(config.targetId)) return targetInfo; throw new BusinessException("不存在的数据源");
            }
        };
        List<Mapping> createdSource = new ArrayList<>(); MigrationService service = null;
        try (Connection source = raw.open(sourceInfo); Connection target = raw.open(targetInfo); Connection store = raw.open(storeInfo)) {
            boolean extended = supportsObjects(source, sourceType) & supportsObjects(target, targetType);
            assertNull(MigrationMetadata.objectType(store, storeInfo.getUserName(), taskName)); boolean storeCreated = false;
            try {
                try (Statement ddl = store.createStatement()) {
                    ddl.execute("CREATE TABLE " + qualified(storeInfo.getUserName(), taskName) + " (ID BIGINT IDENTITY(1,1) PRIMARY KEY, GMT_CREATE TIMESTAMP, GMT_MODIFIED TIMESTAMP, DATA_SOURCE_ID BIGINT, DATABASE_NAME VARCHAR(128), SCHEMA_NAME VARCHAR(128), TABLE_NAME VARCHAR(128), DELETED CHAR(1), USER_ID BIGINT, TASK_TYPE VARCHAR(64), TASK_STATUS VARCHAR(64), TASK_PROGRESS VARCHAR(64), TASK_NAME VARCHAR(256), DOWNLOAD_URL VARCHAR(512), CONTENT BLOB)"); storeCreated = true;
                }
                TaskMapper mapper = persistentMapper(raw, storeInfo, taskName);
                service = new MigrationService(mapper, connections, new MigrationMetadata());
                MockMvc mvc = MockMvcBuilders.standaloneSetup(new MigrationController(service, connections, new MigrationMetadata())).build(); login(700000L);
                Mapping parent = mapping(config, token, "P", "TABLE"), child = mapping(config, token, "C", "TABLE");
                try (Statement ddl = source.createStatement()) {
                    create(source, parent, "CREATE TABLE " + qualified(config.sourceSchema, parent.source) + " (ID NUMBER(10,0) PRIMARY KEY, NOTE VARCHAR(40), CONSTRAINT " + quote("JD_QA_U" + token) + " UNIQUE (NOTE))", createdSource);
                    create(source, child, "CREATE TABLE " + qualified(config.sourceSchema, child.source) + " (ID NUMBER(10,0) PRIMARY KEY, P_ID NUMBER(10,0), CONSTRAINT " + quote("JD_QA_F" + token) + " FOREIGN KEY(P_ID) REFERENCES " + qualified(config.sourceSchema, parent.source) + "(ID) ON DELETE CASCADE)", createdSource);
                    ddl.executeUpdate("INSERT INTO " + qualified(config.sourceSchema, parent.source) + " VALUES (1, '中文')");
                    ddl.executeUpdate("INSERT INTO " + qualified(config.sourceSchema, parent.source) + " VALUES (3, '筛选之外')");
                    ddl.executeUpdate("INSERT INTO " + qualified(config.sourceSchema, child.source) + " VALUES (2, 1)");
                }
                FieldMapping renamed = new FieldMapping(); renamed.source = "ID"; renamed.target = "KEY_ID"; parent.columns.add(renamed);
                parent.filters.add(MigrationFilterTest.filter("ID", "EQ", "1"));
                Mapping view = null, procedure = null, function = null, pack = null, trigger = null;
                if (extended) {
                Mapping sequence = mapping(config, token, "Q", "SEQUENCE"); definition(source, config, sequence, " START WITH 100 INCREMENT BY 1 MAXVALUE 999999 MINVALUE 1 NOCYCLE CACHE 5", createdSource);
                view = mapping(config, token, "V", "VIEW");
                create(source, view, "CREATE VIEW " + qualified(config.sourceSchema, view.source) + " AS SELECT ID, NOTE FROM " + qualified(config.sourceSchema, parent.source), createdSource);
                view.manualDdl = "CREATE VIEW " + qualified(config.targetSchema, view.target) + " AS SELECT KEY_ID, NOTE FROM " + qualified(config.targetSchema, parent.target);
                Mapping synonym = mapping(config, token, "N", "SYNONYM");
                create(source, synonym, "CREATE SYNONYM " + qualified(config.sourceSchema, synonym.source) + " FOR " + qualified(config.sourceSchema, parent.source), createdSource);
                synonym.manualDdl = "CREATE SYNONYM " + qualified(config.targetSchema, synonym.target) + " FOR " + qualified(config.targetSchema, parent.target);
                procedure = mapping(config, token, "R", "PROCEDURE"); definition(source, config, procedure, " (X OUT NUMBER) AS BEGIN X := 7; END;", createdSource);
                function = mapping(config, token, "F", "FUNCTION"); definition(source, config, function, " (X NUMBER) RETURN NUMBER AS BEGIN RETURN X + 1; END;", createdSource);
                pack = mapping(config, token, "K", "PACKAGE");
                definition(source, config, pack, " AS FUNCTION F(X NUMBER) RETURN NUMBER; END;", createdSource);
                String bodySource = "CREATE PACKAGE BODY " + qualified(config.sourceSchema, pack.source) + " AS FUNCTION F(X NUMBER) RETURN NUMBER AS BEGIN RETURN X + 2; END; END;";
                try (Statement ddl = source.createStatement()) { ddl.execute(bodySource); }
                pack.manualDdl += "\n/\nCREATE PACKAGE BODY " + qualified(config.targetSchema, pack.target) + " AS FUNCTION F(X NUMBER) RETURN NUMBER AS BEGIN RETURN X + 2; END; END;\n/";
                trigger = mapping(config, token, "G", "TRIGGER");
                create(source, trigger, "CREATE TRIGGER " + qualified(config.sourceSchema, trigger.source) + " BEFORE INSERT ON " + qualified(config.sourceSchema, child.source) + " FOR EACH ROW BEGIN NULL; END;", createdSource);
                try (Statement ddl = source.createStatement()) { ddl.execute("ALTER TRIGGER " + qualified(config.sourceSchema, trigger.source) + " DISABLE"); }
                trigger.manualDdl = "CREATE TRIGGER " + qualified(config.targetSchema, trigger.target) + " BEFORE INSERT ON " + qualified(config.targetSchema, child.target) + " FOR EACH ROW BEGIN NULL; END;";
                }
                for (Mapping mapping : config.tables) mapping.ddlConfirmed = true;
                Task draft = task(mvc, "POST", "/api/migration/tasks", config);
                Task checked = task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/precheck", null);
                assertEquals("Blockers: " + JSON.toJSONString(checked.assessment), "MIG_READY", checked.status);
                StartRequest start = new StartRequest(); start.fingerprint = checked.assessment.fingerprint; start.offlineConfirmed = true;
                task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/start", start);
                try { task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/start", start); fail("Duplicate execution must fail"); } catch (Exception expected) { }
                long deadline = System.currentTimeMillis() + 30000; Task finished;
                do { finished = task(mvc, "GET", "/api/migration/tasks/" + draft.id, null); if (!MigrationService.ACTIVE.contains(finished.status)) break; Thread.sleep(50); } while (System.currentTimeMillis() < deadline);
                assertEquals(finished.message, "MIG_COMPLETED", finished.status); assertEquals(extended ? 9 : 2, finished.objects.size()); assertEquals(2, finished.committedRows);
                if (extended) {
                assertEquals(1L, scalar(target, "SELECT " + quote("KEY_ID") + " FROM " + qualified(config.targetSchema, view.target)));
                assertEquals(4L, scalar(target, "SELECT " + qualified(config.targetSchema, function.target) + "(3) FROM DUAL"));
                assertEquals(5L, scalar(target, "SELECT " + qualified(config.targetSchema, pack.target) + ".F(3) FROM DUAL"));
                try (CallableStatement call = target.prepareCall("{call " + qualified(config.targetSchema, procedure.target) + "(?)}")) { call.registerOutParameter(1, Types.NUMERIC); call.execute(); assertEquals(7, call.getInt(1)); }
                try (PreparedStatement status = MigrationMetadata.prepare(target, "SELECT STATUS FROM ALL_TRIGGERS WHERE OWNER=? AND TRIGGER_NAME=?", config.targetSchema, trigger.target); ResultSet rows = status.executeQuery()) { assertTrue(rows.next()); assertFalse(MigrationObjects.triggerEnabled(rows.getString(1))); }
                }
                Task downloaded = task(mvc, "GET", "/api/migration/tasks/" + draft.id + "/report", null); assertEquals("MIG_COMPLETED", downloaded.status);
                login(700003L); try { task(mvc, "GET", "/api/migration/tasks/" + draft.id + "/report", null); fail("Other users cannot download reports"); } catch (Exception expected) { }
                try { task(mvc, "GET", "/api/migration/connection/700001", null); fail("Other users cannot open data sources"); } catch (Exception expected) { }
                login(700000L); assertEquals("MIG_COMPLETED", service.get(draft.id, 700000L).status);
                targetPolicies(mvc, source, target, config, token, createdSource);
                independentFailureAndObjectRetry(mvc, source, target, config, token, createdSource);
                // Simulate a persisted in-flight commit record and restart the service against the same actual QA TASK table.
                finished.status = "MIG_RUNNING"; finished.stage = "COMMITTING";
                TaskDO interrupted = new TaskDO(); interrupted.setId(finished.id); interrupted.setTaskStatus(finished.status); interrupted.setTaskProgress(finished.stage); interrupted.setContent(JSON.toJSONBytes(finished)); mapper.updateById(interrupted);
                service.shutdown(); service = new MigrationService(mapper, connections, new MigrationMetadata()); service.recoverInterrupted();
                assertEquals("MIG_NEEDS_REVIEW", service.get(finished.id, 700000L).status);
                mvc = MockMvcBuilders.standaloneSetup(new MigrationController(service, connections, new MigrationMetadata())).build();
                if (!extended) {
                    try (Statement ddl = target.createStatement()) { ddl.executeUpdate("INSERT INTO " + qualified(config.targetSchema, parent.target) + " VALUES (3, '筛选之外')"); }
                    Task outside = task(mvc, "POST", "/api/migration/tasks/" + finished.id + "/resume-check", null);
                    assertNull("Even an identical source row outside the filter must block recovery", outside.resumeFingerprint);
                    try (Statement ddl = target.createStatement()) { ddl.executeUpdate("DELETE FROM " + qualified(config.targetSchema, parent.target) + " WHERE KEY_ID=3"); }
                }
                Task review = task(mvc, "POST", "/api/migration/tasks/" + finished.id + "/resume-check", null);
                if (!extended) {
                    assertNotNull(review.message, review.resumeFingerprint); StartRequest resume = new StartRequest(); resume.fingerprint = review.resumeFingerprint; resume.offlineConfirmed = resume.reviewConfirmed = true;
                    task(mvc, "POST", "/api/migration/tasks/" + finished.id + "/resume", resume);
                    deadline = System.currentTimeMillis() + 30000;
                    do { finished = task(mvc, "GET", "/api/migration/tasks/" + draft.id, null); if (!MigrationService.ACTIVE.contains(finished.status)) break; Thread.sleep(50); } while (System.currentTimeMillis() < deadline);
                    assertEquals(finished.message, "MIG_COMPLETED", finished.status); assertEquals(2, finished.attempts); assertEquals(2, finished.committedRows);
                    assertEquals(2, finished.objects.stream().mapToLong(o -> o.reusedRows).sum());
                } else { assertNull("Mixed program tasks cannot automatically resume", review.resumeFingerprint); finished = downloaded; }
                Path evidence = Paths.get(System.getProperty("user.dir"), "jd-dbms-bisnuess", "target", "migration-workflow-" + sourceType + "-" + targetType + ".json"); Files.write(evidence, JSON.toJSONBytes(finished));
                System.out.println("WORKFLOW " + sourceType + "-" + targetType + " PASS: actual MVC/service/TASK persistence; FK; " + (extended ? "view/synonym/sequence/procedure/function/package/body/disabled trigger" : "complex objects NOT TESTED: Oracle QA privileges missing") + "; duplicate/ownership checks");
            } finally {
                SecurityContextHolder.clearContext(); if (service != null) service.shutdown();
                // Only uniquely named objects created for this run, never users, grants or pre-existing TASK rows.
                List<Mapping> reverse = new ArrayList<>(config.tables); Collections.reverse(reverse);
                for (Mapping mapping : reverse) if (MigrationMetadata.objectType(target, config.targetSchema, mapping.target) != null) drop(target, config.targetSchema, mapping.target, mapping.type, targetType);
                Collections.reverse(createdSource); for (Mapping mapping : createdSource) drop(source, config.sourceSchema, mapping.source, mapping.type, sourceType);
                if (storeCreated) drop(store, storeInfo.getUserName(), taskName, "TABLE", "DM");
            }
        }
    }

    private static boolean supportsObjects(Connection connection, String type) throws SQLException {
        if (!"ORACLE".equals(type)) return true;
        Set<String> privileges = new HashSet<>(); try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT PRIVILEGE FROM SESSION_PRIVS")) { while (rows.next()) privileges.add(rows.getString(1)); }
        List<String> missing = new ArrayList<>(); for (String privilege : Arrays.asList("CREATE SEQUENCE", "CREATE VIEW", "CREATE SYNONYM", "CREATE PROCEDURE", "CREATE TRIGGER")) if (!privileges.contains(privilege)) missing.add(privilege);
        if (!missing.isEmpty()) System.out.println("Oracle complex-object QA NOT TESTED, missing existing privileges: " + missing); return missing.isEmpty();
    }

    private static void targetPolicies(MockMvc mvc, Connection source, Connection target, Config original, String token, List<Mapping> created) throws Exception {
        Mapping mapping = mapping(original, token, "H", "TABLE");
        create(source, mapping, "CREATE TABLE " + qualified(original.sourceSchema, mapping.source) + " (ID NUMBER(10,0) PRIMARY KEY, NOTE VARCHAR(40), CREATED_AT TIMESTAMP(6))", created);
        String targetName = qualified(original.targetSchema, mapping.target);
        try (Statement ddl = source.createStatement()) { ddl.executeUpdate("INSERT INTO " + qualified(original.sourceSchema, mapping.source) + " VALUES (1, '源数据', TIMESTAMP '2026-09-29 12:30:45.123456')"); ddl.executeUpdate("INSERT INTO " + qualified(original.sourceSchema, mapping.source) + " VALUES (2, '范围外数据', TIMESTAMP '2025-01-01 00:00:00')"); }
        mapping.filters.add(MigrationFilterTest.filter("ID", "EQ", "1"));
        mapping.filters.add(MigrationFilterTest.filter("NOTE", "LIKE", "源%"));
        mapping.filters.add(MigrationFilterTest.filter("CREATED_AT", "GE", "2026-01-01 00:00:00"));
        mapping.filters.add(MigrationFilterTest.filter("CREATED_AT", "LT", "2027-01-01 00:00:00.000001"));
        mapping.filters.add(MigrationFilterTest.filter("NOTE", "IS_NOT_NULL", null));
        assertNull(MigrationMetadata.objectType(target, original.targetSchema, mapping.target));
        try (Statement ddl = target.createStatement()) { ddl.execute("CREATE TABLE " + targetName + " (ID NUMBER(10,0) PRIMARY KEY, NOTE VARCHAR(40), CREATED_AT TIMESTAMP(6))"); ddl.executeUpdate("INSERT INTO " + targetName + " VALUES (9, '既有数据', NULL)"); }
        for (String policy : Arrays.asList("APPEND", "CLEAR", "REBUILD")) {
            Config config = JSON.parseObject(JSON.toJSONBytes(original), Config.class); config.tables = new ArrayList<>(Collections.singletonList(mapping));
            config.scope = "ALL"; config.conflict = "STOP"; config.name = "QA 单表策略与筛选 " + policy; config.rowsPerSecond = 10;
            mapping.scope = "REBUILD".equals(policy) ? "ALL" : "DATA"; mapping.conflict = policy;
            Task draft = task(mvc, "POST", "/api/migration/tasks", config), checked = task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/precheck", null);
            assertEquals(JSON.toJSONString(checked.assessment), "MIG_READY", checked.status);
            long before = scalar(target, "SELECT COUNT(*) FROM " + targetName);
            StartRequest start = new StartRequest(); start.fingerprint = checked.assessment.fingerprint; start.offlineConfirmed = true;
            try { task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/start", start); fail("High-risk target changes require separate confirmation"); } catch (Exception expected) { }
            assertEquals(before, scalar(target, "SELECT COUNT(*) FROM " + targetName));
            start.targetChangeConfirmed = true; task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/start", start);
            Task finished; long deadline = System.currentTimeMillis() + 30000;
            do { finished = task(mvc, "GET", "/api/migration/tasks/" + draft.id, null); if (!MigrationService.ACTIVE.contains(finished.status)) break; Thread.sleep(50); } while (System.currentTimeMillis() < deadline);
            assertEquals(finished.message, "MIG_COMPLETED", finished.status);
            assertEquals(1, finished.objects.get(0).sourceRows); assertEquals(policy, finished.assessment.tables.get(0).conflict);
            assertEquals(0, scalar(target, "SELECT COUNT(*) FROM " + targetName + " WHERE ID=2"));
            assertEquals("APPEND".equals(policy) ? 2 : 1, scalar(target, "SELECT COUNT(*) FROM " + targetName));
            assertEquals("APPEND".equals(policy) ? "APPENDED_CONTENT_HASH_PASS" : "FULL_CONTENT_HASH_PASS", finished.objects.get(0).verification);
            if ("APPEND".equals(policy)) assertEquals(1, scalar(target, "SELECT COUNT(*) FROM " + targetName + " WHERE ID=9 AND NOTE='既有数据'"));
            if ("CLEAR".equals(policy)) assertTrue(finished.objects.get(0).appliedDdl.contains("TRUNCATE_TABLE"));
            if ("REBUILD".equals(policy)) assertTrue(finished.objects.get(0).appliedDdl.contains("DROP_TABLE"));
        }
        // A reference from a table outside the selected plan must block, never be cascade-deleted.
        Mapping reference = mapping(original, token, "E", "TABLE"); String referenceName = qualified(original.targetSchema, reference.target);
        assertNull(MigrationMetadata.objectType(target, original.targetSchema, reference.target));
        try (Statement ddl = target.createStatement()) { ddl.execute("CREATE TABLE " + referenceName + " (ID NUMBER(10,0), FOREIGN KEY(ID) REFERENCES " + targetName + "(ID))"); }
        Config blocked = JSON.parseObject(JSON.toJSONBytes(original), Config.class); blocked.tables = new ArrayList<>(Collections.singletonList(mapping)); blocked.scope = "ALL"; blocked.conflict = "REBUILD";
        Task draft = task(mvc, "POST", "/api/migration/tasks", blocked), checked = task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/precheck", null);
        assertEquals("MIG_BLOCKED", checked.status); assertEquals(1, scalar(target, "SELECT COUNT(*) FROM " + targetName));
        System.out.println("TARGET POLICIES PASS: per-object overrides of global STOP; bound numeric/text filters and filtered full-content hash; append preserves existing rows; clear/rebuild verified; independent confirmation and outside FK block");
    }

    static TaskMapper persistentMapper(MigrationConnections connections, DataSource store, String taskName) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean(); MybatisConfiguration configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true); configuration.addMapper(TaskMapper.class); factory.setConfiguration(configuration);
        factory.setDataSource(new AbstractDataSource() { @Override public Connection getConnection() throws SQLException { return connections.open(store); }
            @Override public Connection getConnection(String user, String password) throws SQLException { return getConnection(); } });
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor(); DynamicTableNameInnerInterceptor tables = new DynamicTableNameInnerInterceptor();
        tables.setTableNameHandler((sql, name) -> { if (!"TASK".equalsIgnoreCase(name)) throw new IllegalStateException("Unexpected QA metadata table"); return qualified(store.getUserName(), taskName); });
        interceptor.addInnerInterceptor(tables); interceptor.addInnerInterceptor(new PaginationInnerInterceptor()); factory.setPlugins(interceptor); SqlSessionFactory sessions = factory.getObject();
        return MigrationTransferTest.proxy(TaskMapper.class, (object, method, args) -> { try (SqlSession session = sessions.openSession(true)) { try { return method.invoke(session.getMapper(TaskMapper.class), args); } catch (InvocationTargetException error) { throw error.getCause(); } } });
    }

    private static void independentFailureAndObjectRetry(MockMvc mvc, Connection source, Connection target, Config original, String token, List<Mapping> created) throws Exception {
        Mapping bad = mapping(original, token, "B", "TABLE"), good = mapping(original, token, "Z", "TABLE"), dependent = mapping(original, token, "W", "TABLE");
        for (Mapping item : Arrays.asList(bad, good, dependent)) {
            create(source, item, "CREATE TABLE " + qualified(original.sourceSchema, item.source) + " (ID NUMBER(10,0) PRIMARY KEY)", created);
            assertNull(MigrationMetadata.objectType(target, original.targetSchema, item.target));
            try (Statement ddl = target.createStatement()) { ddl.execute("CREATE TABLE " + qualified(original.targetSchema, item.target) + " (ID NUMBER(10,0) PRIMARY KEY" + (item == bad ? ", CONSTRAINT " + quote("JD_QA_BAD_" + token) + " CHECK(ID < 2)" : "") + ")"); }
        }
        try (Statement rows = source.createStatement()) {
            rows.executeUpdate("INSERT INTO " + qualified(original.sourceSchema, bad.source) + " VALUES(1)"); rows.executeUpdate("INSERT INTO " + qualified(original.sourceSchema, bad.source) + " VALUES(2)");
            rows.executeUpdate("INSERT INTO " + qualified(original.sourceSchema, good.source) + " VALUES(10)"); rows.executeUpdate("INSERT INTO " + qualified(original.sourceSchema, dependent.source) + " VALUES(20)");
        }
        dependent.dependencies.add(qualified(original.sourceSchema, bad.source));
        Config config = JSON.parseObject(JSON.toJSONBytes(original), Config.class); config.tables = new ArrayList<>(Arrays.asList(bad, good, dependent));
        config.scope = "DATA"; config.conflict = "STOP"; config.errorPolicy = "CONTINUE_INDEPENDENT"; config.name = "QA 独立失败与单对象重跑";
        Task draft = task(mvc, "POST", "/api/migration/tasks", config), checked = task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/precheck", null);
        assertEquals(JSON.toJSONString(checked.assessment), "MIG_READY", checked.status);
        StartRequest start = new StartRequest(); start.fingerprint = checked.assessment.fingerprint; start.offlineConfirmed = true;
        task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/start", start);
        Task finished = awaitTask(mvc, draft.id); assertEquals(finished.message, "MIG_PARTIAL", finished.status);
        ObjectResult failed = finished.objects.stream().filter(o -> o.source.equals(qualified(original.sourceSchema, bad.source))).findFirst().get();
        ObjectResult successful = finished.objects.stream().filter(o -> o.source.equals(qualified(original.sourceSchema, good.source))).findFirst().get();
        ObjectResult blocked = finished.objects.stream().filter(o -> o.source.equals(qualified(original.sourceSchema, dependent.source))).findFirst().get();
        assertEquals("FAILED", failed.stage); assertEquals("COMPLETED", successful.stage); assertEquals("DEPENDENCY_BLOCKED", blocked.stage);
        assertEquals(1, scalar(target, "SELECT COUNT(*) FROM " + qualified(original.targetSchema, good.target)));
        assertEquals(0, scalar(target, "SELECT COUNT(*) FROM " + qualified(original.targetSchema, dependent.target)));
        Files.write(Paths.get(System.getProperty("user.dir"), "jd-dbms-bisnuess", "target", "migration-failure-" + original.sourceSchema + "-" + original.targetSchema + ".json"), JSON.toJSONBytes(finished));
        // Fix only the deliberately added QA check, not any application/business object; existing committed rows remain untouched.
        try (Statement ddl = target.createStatement()) { ddl.execute("ALTER TABLE " + qualified(original.targetSchema, bad.target) + " DROP CONSTRAINT " + quote("JD_QA_BAD_" + token)); }
        StartRequest retry = new StartRequest(); retry.sourceObject = failed.source;
        Task review = task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/object-retry-check", retry);
        assertNotNull(review.message, review.resumeFingerprint); retry.fingerprint = review.resumeFingerprint; retry.offlineConfirmed = retry.reviewConfirmed = true;
        StartRequest wrong = JSON.parseObject(JSON.toJSONBytes(retry), StartRequest.class); wrong.sourceObject = successful.source;
        try { task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/resume", wrong); fail("Review must bind to exactly one object"); } catch (Exception expected) { }
        login(700003L); try { task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/object-retry-check", retry); fail("Other owners cannot retry"); } catch (Exception expected) { } finally { login(700000L); }
        task(mvc, "POST", "/api/migration/tasks/" + draft.id + "/resume", retry); finished = awaitTask(mvc, draft.id);
        assertEquals(finished.message, "MIG_PARTIAL", finished.status); assertEquals(2, finished.attempts); assertEquals(3, finished.committedRows);
        assertEquals(2, scalar(target, "SELECT COUNT(*) FROM " + qualified(original.targetSchema, bad.target)));
        assertEquals(1, scalar(target, "SELECT COUNT(*) FROM " + qualified(original.targetSchema, good.target)));
        assertEquals(0, scalar(target, "SELECT COUNT(*) FROM " + qualified(original.targetSchema, dependent.target)));
        assertTrue(finished.objects.stream().filter(o -> o.source.equals(failed.source)).allMatch(o -> "COMPLETED".equals(o.stage)));
        assertTrue(finished.objects.stream().filter(o -> o.source.equals(successful.source)).allMatch(o -> o.committedRows == successful.committedRows && o.readRows == successful.readRows));
        System.out.println("INDEPENDENT FAILURE / OBJECT RETRY PASS: constraint failure; independent table completes; dependent blocked; stable-key retry; successful target unchanged; exact review/ownership checks");
    }

    private static Task awaitTask(MockMvc mvc, Long id) throws Exception {
        long deadline = System.currentTimeMillis() + 30000; Task task;
        do { task = task(mvc, "GET", "/api/migration/tasks/" + id, null); if (!MigrationService.ACTIVE.contains(task.status)) return task; Thread.sleep(50); } while (System.currentTimeMillis() < deadline);
        fail("Task deadline exceeded"); return task;
    }
    private static Mapping mapping(Config config, String token, String suffix, String type) { Mapping mapping = new Mapping(); mapping.source = "JD_QA_X0929_S" + token + "_" + suffix; mapping.target = "JD_QA_X0929_T" + token + "_" + suffix;
        mapping.sourceSchema = config.sourceSchema; mapping.targetSchema = config.targetSchema; mapping.type = type; config.tables.add(mapping); return mapping; }
    private static void definition(Connection source, Config config, Mapping mapping, String body, List<Mapping> created) throws SQLException { create(source, mapping, "CREATE " + mapping.type + " " + qualified(config.sourceSchema, mapping.source) + body, created);
        mapping.manualDdl = "CREATE " + mapping.type + " " + qualified(config.targetSchema, mapping.target) + body; }
    private static void create(Connection source, Mapping mapping, String sql, List<Mapping> created) throws SQLException { assertNull(MigrationMetadata.objectType(source, mapping.sourceSchema, mapping.source)); try (Statement statement = source.createStatement()) { statement.execute(sql); created.add(mapping); } }
    private static long scalar(Connection connection, String sql) throws SQLException { try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(sql)) { assertTrue(rows.next()); return rows.getLong(1); } }
    private static Task task(MockMvc mvc, String method, String url, Object body) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), url).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(JSON.toJSONBytes(body));
        JSONObject response = JSON.parseObject(mvc.perform(request).andReturn().getResponse().getContentAsByteArray());
        assertTrue(response.toJSONString(), response.getBooleanValue("success")); return response.getObject("data", Task.class);
    }
    private static void drop(Connection connection, String schema, String name, String type, String engine) throws SQLException {
        if (!name.matches("JD_QA_X0929_(?:[ST][A-F0-9]{8}_[PCQVNRFKGHEBZW]|M[A-F0-9]{8})")) throw new IllegalArgumentException("Not an owned QA fixture");
        try (Statement ddl = connection.createStatement()) { ddl.execute("DROP " + type + " " + qualified(schema, name) + ("TABLE".equals(type) && "ORACLE".equals(engine) ? " PURGE" : "")); }
    }
}
