package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.repository.entity.TaskDO;
import com.jd.biz.domain.repository.mapper.TaskMapper;
import com.jd.common.tools.base.excption.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Test;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;

public class MigrationServiceTest {
    static class Repository {
        volatile TaskDO row;
        final CountDownLatch finished = new CountDownLatch(1);
        Repository() { TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "migration-test"), TaskDO.class); }
        TaskMapper mapper() {
            return MigrationTransferTest.proxy(TaskMapper.class, (object, method, args) -> {
                synchronized (this) {
                    if (method.getName().equals("selectOne")) {
                        AbstractWrapper<?, ?, ?> query = (AbstractWrapper<?, ?, ?>) args[0]; query.getSqlSegment();
                        String sql = query.getSqlSegment().toUpperCase(Locale.ROOT);
                        assertTrue(sql.contains("USER_ID")); assertTrue(sql.contains("TASK_TYPE"));
                        return row != null && query.getParamNameValuePairs().values().contains(row.getUserId())
                                && query.getParamNameValuePairs().values().contains(row.getId()) ? row : null;
                    }
                    if (method.getName().equals("insert")) { row = (TaskDO) args[0]; row.setId(100L); return 1; }
                    if (method.getName().equals("updateById") || method.getName().equals("update")) {
                        TaskDO patch = (TaskDO) args[0];
                        if (method.getName().equals("update")) {
                            AbstractWrapper<?, ?, ?> query = (AbstractWrapper<?, ?, ?>) args[1]; query.getSqlSegment();
                            if (!query.getParamNameValuePairs().values().contains(row.getTaskStatus())) return 0;
                        }
                        row.setContent(patch.getContent()); row.setTaskStatus(patch.getTaskStatus()); row.setTaskProgress(patch.getTaskProgress());
                        if (!MigrationService.ACTIVE.contains(row.getTaskStatus()) && !Arrays.asList("MIG_DRAFT", "MIG_READY", "MIG_BLOCKED").contains(row.getTaskStatus())) finished.countDown();
                        return 1;
                    }
                    if (method.getName().equals("selectList")) return Collections.singletonList(row);
                    return MigrationTransferTest.unused(method);
                }
            });
        }
    }
    static class Connections extends MigrationConnections {
        MigrationTransferTest.Database source = new MigrationTransferTest.Database(), target = new MigrationTransferTest.Database();
        int authorizationChecks;
        @Override public DataSource authorized(Long id) {
            authorizationChecks++; DataSource source = new DataSource(); source.setId(id); source.setType("DM"); source.setAlias("QA " + id); return source;
        }
        @Override public Connection open(DataSource source) { return source.getId() == 1L ? this.source.connection() : target.connection(); }
    }
    static class Metadata extends MigrationMetadata {
        CountDownLatch entered, release;
        boolean changed;
        @Override public Assessment assess(Connection source, Connection target, Config config, String sourceType, String targetType) {
            if (entered != null) {
                entered.countDown(); try { if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test wait expired"); } catch (InterruptedException e) { throw new IllegalStateException(e); }
            }
            Assessment report = new Assessment(); report.checkedAt = System.currentTimeMillis(); report.fingerprint = changed ? "changed" : "fingerprint";
            TablePlan table = MigrationTransferTest.table("NUMBER"); table.source = config.tables.get(0).source; table.target = config.tables.get(0).target; table.sourceDdl = "CREATE TABLE QA (V NUMBER)"; report.tables.add(table); return report;
        }
    }
    static Task ready(MigrationService service) {
        Config config = MigrationDialectTest.config(); config.scope = "DATA";
        Task task = service.save(null, 42L, config); return service.precheck(task.id, 42L);
    }
    static StartRequest request() { StartRequest request = new StartRequest(); request.offlineConfirmed = true; request.fingerprint = "fingerprint"; return request; }

    @Test public void taskLookupIsScopedToItsOwnerAndType() {
        Repository repository = new Repository(); MigrationService service = new MigrationService(repository.mapper(), new Connections(), new Metadata());
        try {
            Task task = service.save(null, 42L, MigrationDialectTest.config()); assertEquals("MIG_DRAFT", service.get(task.id, 42L).status);
            try { service.get(task.id, 43L); fail(); } catch (BusinessException expected) { }
            assertFalse(new String(repository.row.getContent(), java.nio.charset.StandardCharsets.UTF_8).contains("password"));
        } finally { service.shutdown(); }
    }
    @Test public void startRequiresConfirmationFreshAssessmentAndMatchingFingerprint() {
        Task task = new Task(); task.config = MigrationDialectTest.config(); task.status = "MIG_READY"; task.assessment = new Assessment(); task.assessment.tables.add(new TablePlan());
        task.assessment.fingerprint = "fingerprint"; task.assessment.checkedAt = System.currentTimeMillis();
        StartRequest request = request(); MigrationService.validateStart(task, request);
        request.offlineConfirmed = false;
        try { MigrationService.validateStart(task, request); fail(); } catch (BusinessException expected) { }
        request.offlineConfirmed = true; request.fingerprint = "old";
        try { MigrationService.validateStart(task, request); fail(); } catch (BusinessException expected) { }
        request.fingerprint = "fingerprint"; task.assessment.checkedAt -= 11 * 60 * 1000L;
        try { MigrationService.validateStart(task, request); fail(); } catch (BusinessException expected) { }
    }
    @Test public void duplicateStartAndEditingAnExecutingTaskAreRejected() throws Exception {
        Repository repository = new Repository(); Connections connections = new Connections(); Metadata metadata = new Metadata();
        MigrationService service = new MigrationService(repository.mapper(), connections, metadata);
        try {
            Task task = ready(service); metadata.entered = new CountDownLatch(1); metadata.release = new CountDownLatch(1);
            service.start(task.id, 42L, request()); assertTrue(metadata.entered.await(2, TimeUnit.SECONDS));
            try { service.start(task.id, 42L, request()); fail(); } catch (BusinessException expected) { }
            try { service.save(task.id, 42L, task.config); fail(); } catch (BusinessException expected) { }
            assertEquals(6, connections.authorizationChecks); metadata.release.countDown(); assertTrue(repository.finished.await(2, TimeUnit.SECONDS));
            assertEquals("MIG_COMPLETED", service.get(task.id, 42L).status);
        } finally { if (metadata.release != null) metadata.release.countDown(); service.shutdown(); }
    }
    @Test public void metadataDriftStopsBeforeAnyTargetWrites() throws Exception {
        Repository repository = new Repository(); Connections connections = new Connections(); Metadata metadata = new Metadata();
        MigrationService service = new MigrationService(repository.mapper(), connections, metadata);
        try {
            Task task = ready(service); metadata.changed = true; service.start(task.id, 42L, request());
            assertTrue(repository.finished.await(2, TimeUnit.SECONDS)); assertEquals("MIG_FAILED", service.get(task.id, 42L).status);
            assertEquals(0, connections.target.batches); assertEquals(0, connections.target.commits);
        } finally { service.shutdown(); }
    }
    @Test public void uncertainCommitHasAnExplicitReviewState() throws Exception {
        Repository repository = new Repository(); Connections connections = new Connections(); connections.source.rows.add(new Object[] { java.math.BigDecimal.ONE }); connections.target.failCommit = true;
        MigrationService service = new MigrationService(repository.mapper(), connections, new Metadata());
        try {
            Task task = ready(service); service.start(task.id, 42L, request()); assertTrue(repository.finished.await(2, TimeUnit.SECONDS));
            Task result = service.get(task.id, 42L); assertEquals("MIG_NEEDS_REVIEW", result.status); assertEquals(0, result.committedRows); assertEquals("MIG_NEEDS_REVIEW", result.objects.get(0).stage);
        } finally { service.shutdown(); }
    }
    @Test public void restartDoesNotAutomaticallyResumeOrMarkSuccess() {
        Repository repository = new Repository(); MigrationService service = new MigrationService(repository.mapper(), new Connections(), new Metadata());
        try {
            Task task = service.save(null, 42L, MigrationDialectTest.config()); task.status = "MIG_RUNNING"; task.stage = "COMMITTING";
            repository.row.setTaskStatus(task.status); repository.row.setContent(JSON.toJSONBytes(task)); service.recoverInterrupted();
            assertEquals("MIG_NEEDS_REVIEW", service.get(task.id, 42L).status);
            task.stage = "DATA"; repository.row.setTaskStatus("MIG_RUNNING"); repository.row.setContent(JSON.toJSONBytes(task)); service.recoverInterrupted();
            assertEquals("MIG_INTERRUPTED", service.get(task.id, 42L).status);
        } finally { service.shutdown(); }
    }
    @Test public void destructiveOrAppendPoliciesRequireASeparateTargetConfirmation() {
        Task task = new Task(); task.config = MigrationDialectTest.config(); task.status = "MIG_READY"; task.assessment = new Assessment(); task.assessment.tables.add(new TablePlan());
        task.assessment.checkedAt = System.currentTimeMillis(); task.assessment.fingerprint = "fingerprint";
        for (String strategy : new String[] { "APPEND", "CLEAR", "REBUILD" }) {
            task.config.conflict = strategy; StartRequest request = request();
            try { MigrationService.validateStart(task, request); fail(); } catch (BusinessException expected) { }
            request.targetChangeConfirmed = true; MigrationService.validateStart(task, request);
        }
    }
    @Test public void resumeNeedsOwnerFreshReviewAndBothExplicitConfirmations() {
        Repository repository = new Repository(); MigrationService service = new MigrationService(repository.mapper(), new Connections(), new Metadata());
        try {
            Task task = service.save(null, 42L, MigrationDialectTest.config()); task.status = "MIG_FAILED"; task.resumeFingerprint = "resume"; task.resumeCheckedAt = System.currentTimeMillis();
            repository.row.setTaskStatus(task.status); repository.row.setContent(JSON.toJSONBytes(task));
            StartRequest request = request(); request.fingerprint = "resume";
            try { service.resume(task.id, 42L, request); fail(); } catch (BusinessException expected) { }
            request.reviewConfirmed = true; request.offlineConfirmed = false;
            try { service.resume(task.id, 42L, request); fail(); } catch (BusinessException expected) { }
            request.offlineConfirmed = true;
            try { service.resume(task.id, 43L, request); fail(); } catch (BusinessException expected) { }
            task.resumeCheckedAt -= 11 * 60 * 1000L; repository.row.setContent(JSON.toJSONBytes(task));
            try { service.resume(task.id, 42L, request); fail(); } catch (BusinessException expected) { }
        } finally { service.shutdown(); }
    }

    @Test public void onlyCertainSqlFailuresCanContinue() {
        assertTrue(MigrationService.certainSqlFailure(new java.sql.SQLException("constraint", "23000")));
        assertTrue(MigrationService.certainSqlFailure(new java.sql.SQLException("data", "22001")));
        assertTrue(MigrationService.certainSqlFailure(new java.sql.SQLException("syntax", "42000")));
        for (String state : new String[] { null, "08006", "HY000", "57014", "40001" }) assertFalse(MigrationService.certainSqlFailure(new java.sql.SQLException("unknown", state)));
        java.sql.SQLException chain = new java.sql.SQLException("constraint", "23000"); chain.setNextException(new java.sql.SQLException("connection", "08006"));
        assertFalse(MigrationService.certainSqlFailure(chain));
        assertFalse(MigrationService.certainSqlFailure(new MigrationTransfer.Cancelled()));
        assertFalse(MigrationService.certainSqlFailure(new BusinessException("source drift")));
    }

    @Test public void dependenciesAndForeignKeysDoNotTreatPartialObjectsAsSuccessful() {
        Task task = new Task(); task.assessment = new Assessment();
        TablePlan parent = new TablePlan(); parent.targetSchema = "QA"; parent.target = "P";
        TablePlan child = new TablePlan(); child.targetSchema = "QA"; child.target = "C";
        task.assessment.tables.add(parent); task.assessment.tables.add(child);
        ObjectResult failed = new ObjectResult(); failed.source = "\"QA\".\"P\""; failed.stage = "FAILED";
        ObjectResult copied = new ObjectResult(); copied.source = "\"QA\".\"C\""; copied.stage = "COMPLETED";
        task.objects.add(failed); task.objects.add(copied);
        DdlOperation fk = new DdlOperation(); fk.foreignKey = true; fk.referenceSchema = "QA"; fk.referenceTable = "P"; child.postDdl.add(fk);
        assertFalse(MigrationService.dependencyBlocked(task, child, false)); assertTrue(MigrationService.dependencyBlocked(task, child, true));
        child.dependencies.add(failed.source); assertTrue(MigrationService.dependencyBlocked(task, child, false));
        failed.stage = "SKIPPED"; assertTrue(MigrationService.dependencyBlocked(task, child, true));
        failed.stage = "COMPLETED"; assertFalse(MigrationService.dependencyBlocked(task, child, true));
    }

    @Test public void continuePolicyKeepsIndependentTableAndBlocksDependent() throws Exception { executionPolicy(true); }
    @Test public void defaultPolicyStopsBeforeIndependentTable() throws Exception { executionPolicy(false); }

    private void executionPolicy(boolean continueIndependent) throws Exception {
        Repository repository = new Repository(); Connections connections = new Connections();
        connections.source.rows.add(new Object[] { java.math.BigDecimal.ONE });
        Metadata metadata = new Metadata() {
            @Override public Assessment assess(Connection read, Connection write, Config config, String sourceType, String targetType) {
                Assessment report = super.assess(read, write, config, sourceType, targetType);
                TablePlan bad = report.tables.get(0); bad.sourceSchema = config.sourceSchema; bad.targetSchema = config.targetSchema;
                TablePlan dependent = MigrationTransferTest.table("NUMBER"); dependent.source = "DEPENDENT"; dependent.target = "DEPENDENT_T";
                dependent.dependencies.add(MigrationDialect.qualified(config.sourceSchema, bad.source)); report.tables.add(dependent);
                TablePlan good = MigrationTransferTest.table("NUMBER"); good.source = "GOOD"; good.target = "GOOD_T"; good.sourceDdl = bad.sourceDdl; report.tables.add(good); return report;
            }
        };
        MigrationTransferTest.Database failed = new MigrationTransferTest.Database(); failed.failBatch = true;
        Connections routed = new Connections() {
            @Override public Connection open(DataSource info) {
                if (info.getId() == 1L) return connections.source.connection();
                Connection good = connections.target.connection(), bad = failed.connection();
                return MigrationTransferTest.proxy(Connection.class, (object, method, args) -> {
                    Connection selected = method.getName().equals("prepareStatement") && ((String) args[0]).contains("\"TARGET\"") ? bad : good;
                    try { return method.invoke(selected, args); } catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                });
            }
        };
        MigrationService service = new MigrationService(repository.mapper(), routed, metadata);
        try {
            Config config = MigrationDialectTest.config(); config.tables.get(0).target = "TARGET"; config.scope = "DATA";
            if (continueIndependent) config.errorPolicy = "CONTINUE_INDEPENDENT";
            Task task = service.save(null, 42L, config); service.precheck(task.id, 42L); service.start(task.id, 42L, request());
            assertTrue(repository.finished.await(3, TimeUnit.SECONDS)); Task result = service.get(task.id, 42L);
            assertEquals(result.message, continueIndependent ? "MIG_PARTIAL" : "MIG_FAILED", result.status);
            assertEquals(continueIndependent ? "FAILED" : "MIG_FAILED", result.objects.get(0).stage);
            assertEquals(continueIndependent ? "DEPENDENCY_BLOCKED" : "WAITING", result.objects.get(1).stage); assertEquals(continueIndependent ? "COMPLETED" : "WAITING", result.objects.get(2).stage);
            assertEquals(continueIndependent ? 1 : 0, result.committedRows); assertEquals(continueIndependent ? 1 : 0, connections.target.rows.size()); assertEquals(0, failed.rows.size());
        } finally { service.shutdown(); }
    }

    @Test public void singleObjectReviewCannotBeUsedAsWholeTaskOrAnotherObjectReview() {
        Repository repository = new Repository(); MigrationService service = new MigrationService(repository.mapper(), new Connections(), new Metadata());
        try {
            Task task = service.save(null, 42L, MigrationDialectTest.config()); task.status = "MIG_PARTIAL";
            task.resumeObject = "\"QA\".\"FAILED\""; task.resumeFingerprint = "one-object"; task.resumeCheckedAt = System.currentTimeMillis();
            repository.row.setTaskStatus(task.status); repository.row.setContent(JSON.toJSONBytes(task));
            StartRequest request = request(); request.fingerprint = task.resumeFingerprint; request.reviewConfirmed = true;
            try { service.resume(task.id, 42L, request); fail(); } catch (BusinessException expected) { }
            request.sourceObject = "\"QA\".\"OTHER\"";
            try { service.resume(task.id, 42L, request); fail(); } catch (BusinessException expected) { }
        } finally { service.shutdown(); }
    }

    @Test public void checkpointFailureIsNotAnIndependentObjectError() {
        MigrationTransfer.Control control = new MigrationTransfer.Control(new Task(), () -> { throw new IllegalStateException("store down"); });
        try { control.checkpoint(); fail(); } catch (IllegalStateException expected) { assertTrue(control.checkpointFailed); }
    }

    @Test public void certainDdlFailureContinuesOnlyIndependentObjects() throws Exception { ddlFailure("42000", false); }
    @Test public void uncertainDdlStopsDespiteContinuePolicy() throws Exception { ddlFailure("08006", false); }
    @Test public void lostDdlCheckpointStopsDespiteContinuePolicy() throws Exception { ddlFailure(null, true); }

    @Test public void noKeyAndUnsupportedStrategiesBlockSingleObjectRetryWithoutWrites() {
        Repository repository = new Repository(); Connections connections = new Connections();
        Metadata metadata = new Metadata() { @Override public void verifyCreated(Connection target, Config config, TablePlan table, String type) { } };
        MigrationService service = new MigrationService(repository.mapper(), connections, metadata);
        try {
            Config config = MigrationDialectTest.config(); config.scope = "DATA";
            Task task = service.save(null, 42L, config); task.status = "MIG_PARTIAL"; task.assessment = new Assessment();
            TablePlan table = MigrationTransferTest.table("NUMBER"); task.assessment.tables.add(table);
            ObjectResult failed = new ObjectResult(); failed.source = MigrationDialect.qualified(config.sourceSchema, table.source); failed.stage = "FAILED"; task.objects.add(failed);
            for (String strategy : new String[] { "STOP", "APPEND", "STRUCTURE" }) {
                task.config.conflict = "APPEND".equals(strategy) ? "APPEND" : "STOP"; task.config.scope = "STRUCTURE".equals(strategy) ? "STRUCTURE" : "DATA";
                repository.row.setTaskStatus(task.status); repository.row.setContent(JSON.toJSONBytes(task));
                Task review = service.reviewObject(task.id, 42L, failed.source); assertNull(review.resumeFingerprint); assertEquals(0, connections.target.batches); assertEquals(0, connections.target.commits);
            }
        } finally { service.shutdown(); }
    }

    private void ddlFailure(String sqlState, boolean loseCheckpoint) throws Exception {
        Repository repository = new Repository(); TaskMapper baseMapper = repository.mapper(); java.util.concurrent.atomic.AtomicBoolean lost = new java.util.concurrent.atomic.AtomicBoolean();
        TaskMapper mapper = MigrationTransferTest.proxy(TaskMapper.class, (object, method, args) -> {
            if (loseCheckpoint && method.getName().equals("updateById")) {
                Task patch = JSON.parseObject(((TaskDO) args[0]).getContent(), Task.class);
                if ("DDL_COMMITTING".equals(patch.stage) && patch.objects.get(0).created && !lost.getAndSet(true)) throw new IllegalStateException("test checkpoint lost");
            }
            try { return method.invoke(baseMapper, args); } catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
        });
        List<String> executed = new ArrayList<>(); Connections connections = new Connections() {
            @Override public Connection open(DataSource info) {
                Connection base = super.open(info); if (info.getId() == 1L) return base;
                return MigrationTransferTest.proxy(Connection.class, (object, method, args) -> {
                    if (method.getName().equals("createStatement")) return MigrationTransferTest.proxy(java.sql.Statement.class, (statement, call, params) -> {
                        if (call.getName().equals("execute")) { executed.add((String) params[0]); if (executed.size() == 1 && sqlState != null) throw new java.sql.SQLException("deliberate QA DDL failure", sqlState); return false; }
                        return MigrationTransferTest.unused(call);
                    });
                    try { return method.invoke(base, args); } catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                });
            }
        };
        Metadata metadata = new Metadata() {
            @Override public Assessment assess(Connection read, Connection write, Config config, String sourceType, String targetType) {
                Assessment report = super.assess(read, write, config, sourceType, targetType); report.tables.get(0).targetDdl = "CREATE TABLE BAD (V NUMBER)";
                TablePlan good = MigrationTransferTest.table("NUMBER"); good.source = "GOOD"; good.target = "GOOD"; good.targetDdl = "CREATE TABLE GOOD (V NUMBER)"; report.tables.add(good); return report;
            }
            @Override public void verifyCreated(Connection write, Config config, TablePlan table, String targetType) { }
        };
        MigrationService service = new MigrationService(mapper, connections, metadata);
        try {
            Config config = MigrationDialectTest.config(); config.scope = "STRUCTURE"; config.errorPolicy = "CONTINUE_INDEPENDENT";
            Task task = service.save(null, 42L, config); service.precheck(task.id, 42L); service.start(task.id, 42L, request());
            assertTrue(repository.finished.await(3, TimeUnit.SECONDS)); Task result = service.get(task.id, 42L);
            boolean certain = "42000".equals(sqlState);
            assertEquals(result.message, certain ? "MIG_PARTIAL" : "MIG_NEEDS_REVIEW", result.status); assertEquals(certain ? 2 : 1, executed.size());
            assertEquals(certain ? "COMPLETED" : "WAITING", result.objects.get(1).stage);
        } finally { service.shutdown(); }
    }
}
