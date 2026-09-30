package com.jd.biz.migration;

import com.jd.common.tools.base.excption.BusinessException;
import org.junit.Test;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.*;
import static com.jd.biz.migration.MigrationModel.*;
import static org.junit.Assert.*;

public class MigrationFilterTest {
    static Filter filter(String column, String operator, String value) {
        Filter filter = new Filter(); filter.column = column; filter.operator = operator; filter.value = value; return filter;
    }
    private static void rejected(TablePlan table) {
        try { MigrationFilter.predicate(table); fail("Invalid filter must fail"); } catch (BusinessException expected) { }
    }
    @Test public void bindsValuesAndKeepsQuotedIdentifiersSeparateFromSql() throws Exception {
        TablePlan table = MigrationTransferTest.table("NUMBER", "TEXT", "TIMESTAMP", "BLOB");
        table.columns.get(1).name = "有\"引号"; table.columns.get(1).sourceType = "NVARCHAR2";
        table.filters.add(filter("C0", "GE", "-1.25"));
        table.filters.add(filter("有\"引号", "LIKE", "x'; DROP TABLE T;--%"));
        table.filters.add(filter("C2", "LT", "2026-09-29 12:30:45.123"));
        table.filters.add(filter("C3", "IS_NOT_NULL", null));
        assertEquals("\"C0\" >= ? AND \"有\"\"引号\" LIKE ? AND \"C2\" < TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS.FF6') AND \"C3\" IS NOT NULL", MigrationFilter.predicate(table));
        Map<Integer, Object> bindings = new TreeMap<>(); List<String> methods = new ArrayList<>();
        PreparedStatement query = MigrationTransferTest.proxy(PreparedStatement.class, (object, method, args) -> {
            bindings.put((Integer) args[0], args[1]); methods.add(method.getName()); return null;
        });
        MigrationFilter.bind(query, table, 3);
        assertEquals(new BigDecimal("-1.25"), bindings.get(3)); assertEquals("x'; DROP TABLE T;--%", bindings.get(4));
        assertEquals("2026-09-29 12:30:45.123000", bindings.get(5)); assertEquals(3, bindings.size()); assertEquals("setNString", methods.get(1));
    }
    @Test public void rejectsUnknownColumnsUnsupportedOperatorsAndLobComparisons() {
        TablePlan table = MigrationTransferTest.table("NUMBER", "BLOB");
        for (Filter filter : Arrays.asList(filter("C0 OR 1=1", "EQ", "1"), filter("C0", "IN", "1"), filter("C0", "LIKE", "1"), filter("C1", "EQ", "1"))) {
            table.filters = Collections.singletonList(filter); rejected(table);
        }
    }
    @Test public void rejectsInvalidNumbersAndCivilTimesWithoutEchoingValues() {
        for (String number : Arrays.asList("1 OR 1=1", "1e3", "NaN", "999999999999999999999999999999999999999", "")) {
            TablePlan table = MigrationTransferTest.table("NUMBER"); table.filters.add(filter("C0", "EQ", number)); rejected(table);
        }
        for (String time : Arrays.asList("2026-02-30 00:00:00", "2026-09-29T00:00:00Z", "0000-01-01 00:00:00", "2026-09-29 00:00:00.1234567")) {
            TablePlan table = MigrationTransferTest.table("TIMESTAMP"); table.filters.add(filter("C0", "EQ", time)); rejected(table);
        }
    }
    @Test public void emptyFiltersDoNotChangeQueriesAndNullTestsDoNotBind() throws Exception {
        TablePlan table = MigrationTransferTest.table("CLOB"); assertEquals("", MigrationFilter.where(table));
        table.filters.add(filter("C0", "IS_NULL", null)); assertEquals(" WHERE \"C0\" IS NULL", MigrationFilter.where(table));
        MigrationFilter.bind(MigrationTransferTest.proxy(PreparedStatement.class, (object, method, args) -> { fail("Null test must not bind"); return null; }), table, 1);
    }
    @Test public void perObjectOverridesInheritAndCannotBypassRiskConfirmation() {
        Config config = MigrationDialectTest.config(); Mapping mapping = config.tables.get(0);
        assertEquals("ALL", MigrationDialect.scope(config, mapping)); assertFalse(MigrationDialect.highRisk(config));
        mapping.scope = "DATA"; mapping.conflict = "CLEAR"; MigrationDialect.validate(config, true);
        assertTrue(MigrationDialect.highRisk(config));
        TablePlan plan = new TablePlan(); plan.scope = mapping.scope; plan.conflict = mapping.conflict; config.rowsPerSecond = 25;
        Config scoped = MigrationDialect.scoped(config, plan); assertEquals("DATA", scoped.scope); assertEquals("CLEAR", scoped.conflict); assertEquals(25, scoped.rowsPerSecond);
        mapping.conflict = "REBUILD"; try { MigrationDialect.validate(config, true); fail(); } catch (BusinessException expected) { }
        mapping.scope = "STRUCTURE"; mapping.conflict = "STOP"; mapping.filters.add(filter("ID", "EQ", "1"));
        try { MigrationDialect.validate(config, true); fail(); } catch (BusinessException expected) { }
    }
    @Test public void programObjectsCanOverrideDataDefaultsButNotUseDestructivePolicies() {
        Config config = MigrationDialectTest.config(); config.scope = "DATA"; Mapping mapping = config.tables.get(0); mapping.type = "VIEW";
        mapping.scope = "STRUCTURE"; MigrationDialect.validate(config, true);
        mapping.conflict = "REBUILD"; try { MigrationDialect.validate(config, true); fail(); } catch (BusinessException expected) { }
    }
    @Test public void throttlesWithoutClaimingUncommittedRows() throws Exception {
        MigrationTransferTest.Database source = new MigrationTransferTest.Database(), target = new MigrationTransferTest.Database();
        for (int i = 0; i < 3; i++) source.rows.add(new Object[] { BigDecimal.valueOf(i) });
        MigrationTransfer.Control control = MigrationTransferTest.control(); control.task.config.rowsPerSecond = 10;
        ObjectResult result = new ObjectResult(); long started = System.nanoTime();
        new MigrationTransfer().copy(source.connection(), target.connection(), control.task.config, MigrationTransferTest.table("NUMBER"), result, control);
        assertTrue("Three rows at 10/s require two intervals", System.nanoTime() - started >= 190000000L);
        assertEquals(3, result.committedRows); assertEquals("DATA", control.task.stage);
    }
    @Test public void throttleWaitCanBeCancelledWithoutWaitingForFullInterval() throws Exception {
        MigrationTransfer.Control control = MigrationTransferTest.control(); control.pace(1);
        java.util.concurrent.CountDownLatch waiting = new java.util.concurrent.CountDownLatch(1);
        MigrationTransfer.Control cancellable = new MigrationTransfer.Control(control.task, () -> { if ("THROTTLED".equals(control.task.stage)) waiting.countDown(); });
        cancellable.pace(1);
        Thread cancel = new Thread(() -> { try { waiting.await(); cancellable.cancelled.set(true); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        cancel.start(); long started = System.nanoTime();
        try { cancellable.pace(1); fail(); } catch (MigrationTransfer.Cancelled expected) { }
        finally { cancel.join(2000); }
        assertTrue("Cancellation must not wait for a one-second interval", System.nanoTime() - started < 800000000L);
        assertEquals(0, control.task.committedRows);
    }
}
