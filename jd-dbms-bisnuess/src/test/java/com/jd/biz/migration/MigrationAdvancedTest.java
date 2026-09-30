package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.jd.common.tools.base.excption.BusinessException;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;

public class MigrationAdvancedTest {
    @Test public void concurrentCommitsCannotHideOutstandingCommitOrLoseCounts() throws Exception {
        Task task = new Task(); task.config = MigrationDialectTest.config();
        List<Integer> persisted = new ArrayList<>(); MigrationTransfer.Control root = new MigrationTransfer.Control(task, () -> persisted.add(task.inFlightCommits));
        MigrationTransfer.Control first = root.worker(), second = root.worker();
        first.beginCommit("COMMITTING"); second.beginCommit("DDL_COMMITTING"); first.endCommit("DATA");
        assertEquals(1, task.inFlightCommits); assertEquals("COMMITTING", task.stage); assertTrue(second.committing);
        second.endCommit("DATA"); assertEquals(0, task.inFlightCommits); assertEquals(Arrays.asList(1, 2, 1, 0), persisted);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> writes = new ArrayList<>();
            for (int i = 0; i < 4; i++) writes.add(pool.submit(() -> {
                MigrationTransfer.Control worker = root.worker(); MigrationTransferTest.Database database = new MigrationTransferTest.Database(); ObjectResult result = new ObjectResult();
                try { for (int j = 0; j < 100; j++) { worker.bytes(3); MigrationTransfer.commit(database.connection(), 1, result, worker); } }
                catch (Exception error) { throw new IllegalStateException(error); }
            }));
            for (Future<?> write : writes) write.get(5, TimeUnit.SECONDS);
            assertEquals(400, task.committedRows); assertEquals(1200, task.streamedBytes); assertEquals(0, task.inFlightCommits);
        } finally { pool.shutdownNow(); }
    }

    @Test public void workersShareRateAndCancelWhileWaiting() throws Exception {
        MigrationTransfer.Control root = MigrationTransferTest.control(); ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            long start = System.nanoTime(); List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) futures.add(pool.submit(() -> { try { root.worker().pace(10); } catch (InterruptedException error) { throw new IllegalStateException(error); } }));
            for (Future<?> future : futures) future.get(2, TimeUnit.SECONDS);
            assertTrue("Rate must be shared, not multiplied by workers", System.nanoTime() - start >= 180000000L);
            root.cancelled.set(true); try { root.worker().pace(1); fail(); } catch (MigrationTransfer.Cancelled expected) { }
        } finally { pool.shutdownNow(); }
    }

    @Test public void explicitCastAndAttributeProjectionHaveNoArbitrarySql() {
        Column column = new Column(); column.name = "PAYLOAD"; column.sourceType = "BUSINESS_OBJECT";
        FieldMapping field = new FieldMapping(); field.source = column.name; field.targetType = "NUMBER(20,4)"; field.sourceAttribute = "AMOUNT";
        try { MigrationConversion.apply(column, field, "ORACLE", "DM"); fail(); } catch (BusinessException expected) { }
        field.conversionConfirmed = true; MigrationConversion.apply(column, field, "ORACLE", "DM");
        assertEquals("NUMBER(20,4)", column.targetType); assertEquals("CAST(JD_SRC.\"PAYLOAD\".\"AMOUNT\" AS NUMBER(20,4))", column.sourceExpression);
        for (String invalid : Arrays.asList("VARCHAR(10); DROP TABLE X", "NUMBER(999999999999,2)", "NUMBER(4,-2)", "VARCHAR(0)", "TIMESTAMP(9)")) {
            field.targetType = invalid; try { MigrationConversion.apply(column, field, "ORACLE", "DM"); fail(invalid); } catch (BusinessException expected) { }
        }
        field.targetType = "VARCHAR(40)"; field.sourceAttribute = "A);F()";
        try { MigrationConversion.apply(column, field, "ORACLE", "DM"); fail(); } catch (BusinessException expected) { }
        field.targetType = ""; field.sourceAttribute = "AMOUNT";
        try { MigrationConversion.apply(column, field, "ORACLE", "DM"); fail("An attribute without explicit destination type must be rejected"); } catch (BusinessException expected) { }
    }

    @Test public void tablespaceIsQuotedAndIncludedInDdlAndParallelismIsBounded() {
        TablePlan table = MigrationTransferTest.table("NUMBER"); table.columns.get(0).targetType = "NUMBER"; table.targetTablespace = "QA\"SPACE";
        assertTrue(MigrationDialect.createTable("QA", table).endsWith(" TABLESPACE \"QA\"\"SPACE\""));
        Config config = MigrationDialectTest.config();
        for (int invalid : new int[] { 0, -1, 5 }) { config.parallelism = invalid; try { MigrationDialect.validate(config, false); fail(); } catch (BusinessException expected) { } }
    }

    @Test public void noKeyOrBaselineFailedObjectGetsOwnedNewTargetDraftNotBlindResume() {
        MigrationServiceTest.Repository repository = new MigrationServiceTest.Repository(); MigrationService service = new MigrationService(repository.mapper(), new MigrationServiceTest.Connections(), new MigrationServiceTest.Metadata());
        try {
            Config config = MigrationDialectTest.config(); Mapping mapping = config.tables.get(0); mapping.sourceSchema = config.sourceSchema; mapping.targetSchema = config.targetSchema; mapping.target = "订单明细测试数据表";
            Task previous = service.save(null, 42L, config); previous.status = "MIG_FAILED"; previous.assessment = new Assessment();
            TablePlan plan = new TablePlan(); plan.source = mapping.source; plan.target = mapping.target; plan.sourceSchema = config.sourceSchema; plan.targetSchema = config.targetSchema;
            previous.assessment.tables.add(plan); ObjectResult result = new ObjectResult(); result.source = MigrationDialect.qualified(plan.sourceSchema, plan.source); result.stage = "FAILED"; previous.objects.add(result);
            repository.row.setTaskStatus(previous.status); repository.row.setContent(JSON.toJSONBytes(previous));
            try { service.retryDraft(previous.id, 43L, result.source); fail(); } catch (BusinessException expected) { }
            Task retry = service.retryDraft(previous.id, 42L, result.source);
            assertEquals(previous.id, retry.parentTaskId); assertEquals("MIG_DRAFT", retry.status); assertNull(retry.assessment); assertNotEquals(mapping.target, retry.config.tables.get(0).target);
            assertTrue("New Oracle-compatible names must be bounded in bytes, not Chinese characters", retry.config.tables.get(0).target.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 29);
            assertEquals("STOP", retry.config.tables.get(0).conflict); assertEquals("ALL", retry.config.tables.get(0).scope);
            Task saved = service.save(retry.id, 42L, retry.config); assertEquals(previous.id, saved.parentTaskId);
        } finally { service.shutdown(); }
    }

    @Test public void failedProgramDraftIncludesDependencyAndRequiresNewDdlReview() {
        MigrationServiceTest.Repository repository = new MigrationServiceTest.Repository(); MigrationService service = new MigrationService(repository.mapper(), new MigrationServiceTest.Connections(), new MigrationServiceTest.Metadata());
        try {
            Config config = MigrationDialectTest.config(); Mapping table = config.tables.get(0);
            Mapping view = new Mapping(); view.source = "V"; view.target = "V_OLD"; view.type = "VIEW"; view.manualDdl = "CREATE VIEW OLD AS SELECT * FROM OLD_TABLE"; view.ddlConfirmed = true; config.tables.add(view);
            Task previous = service.save(null, 42L, config); previous.status = "MIG_PARTIAL"; previous.assessment = new Assessment();
            for (Mapping mapping : config.tables) {
                TablePlan plan = new TablePlan(); plan.source = mapping.source; plan.target = mapping.target; plan.sourceSchema = config.sourceSchema; plan.targetSchema = config.targetSchema; plan.type = mapping.type; previous.assessment.tables.add(plan);
                ObjectResult result = new ObjectResult(); result.source = MigrationDialect.qualified(plan.sourceSchema, plan.source); result.stage = mapping == table ? "COMPLETED" : "FAILED"; previous.objects.add(result);
            }
            previous.assessment.tables.get(1).dependencies.add(previous.objects.get(0).source);
            repository.row.setTaskStatus(previous.status); repository.row.setContent(JSON.toJSONBytes(previous));
            Task retry = service.retryDraft(previous.id, 42L, previous.objects.get(1).source);
            assertEquals(2, retry.config.tables.size()); assertEquals("ALL", retry.config.tables.get(0).scope); assertEquals("STRUCTURE", retry.config.tables.get(1).scope);
            assertNotEquals(table.target, retry.config.tables.get(0).target); assertNull(retry.config.tables.get(1).manualDdl); assertFalse(retry.config.tables.get(1).ddlConfirmed);
            assertEquals("CREATE VIEW OLD AS SELECT * FROM OLD_TABLE", view.manualDdl); assertTrue(view.ddlConfirmed);
            assertEquals("COMPLETED", previous.objects.get(0).stage);
        } finally { service.shutdown(); }
    }
}
