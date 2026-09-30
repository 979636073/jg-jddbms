package com.jd.biz.migration;

import org.junit.Test;
import com.jd.common.tools.base.excption.BusinessException;
import java.util.*;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;
import static org.junit.Assert.*;

public class MigrationPlanTest {
    @Test public void multiSchemaNamesStayDistinctButPhysicalOverlapIsRejected() {
        Config config = MigrationDialectTest.config(); Mapping second = new Mapping(); second.source = config.tables.get(0).source;
        second.target = config.tables.get(0).target; second.sourceSchema = "OTHER_SOURCE"; second.targetSchema = "OTHER_TARGET"; config.tables.add(second);
        validate(config, true);
        second.targetSchema = config.targetSchema;
        try { validate(config, true); fail(); } catch (BusinessException expected) { }
        second.target = config.tables.get(0).source; second.targetSchema = config.sourceSchema; config.targetId = config.sourceId;
        try { validate(config, true); fail(); } catch (BusinessException expected) { }
    }
    @Test public void fieldNamesAreMappedEverywhereWithoutChangingLiterals() {
        TablePlan plan = new TablePlan(); plan.target = "TARGET";
        Column column = new Column(); column.name = "ID"; column.targetType = "NUMBER(10,0)"; plan.columns.add(column); plan.primaryKey.add("ID");
        Mapping mapping = new Mapping(); FieldMapping field = new FieldMapping(); field.source = "ID"; field.target = "KEY_ID"; mapping.columns.add(field);
        MigrationMetadata.applyFields(mapping, plan);
        assertTrue(createTable("TARGET_SCHEMA", plan).contains("PRIMARY KEY (\"KEY_ID\")"));
        assertEquals("\"KEY_ID\" > 0 AND 'ID' <> 'x'", MigrationConstraints.checkExpression("\"ID\" > 0 AND 'ID' <> 'x'", plan));
        assertEquals("KEY_ID", targetColumns(plan).get(0).name);
    }
    @Test public void fieldMappingRejectsUnknownAndDuplicateTargets() {
        TablePlan plan = new TablePlan(); Column a = new Column(); a.name = "A"; Column b = new Column(); b.name = "B"; plan.columns.add(a); plan.columns.add(b);
        Mapping mapping = new Mapping(); FieldMapping field = new FieldMapping(); field.source = "MISSING"; field.target = "B"; mapping.columns.add(field);
        try { MigrationMetadata.applyFields(mapping, plan); fail(); } catch (BusinessException expected) { }
        field.source = "A";
        try { MigrationMetadata.applyFields(mapping, plan); fail(); } catch (BusinessException expected) { }
    }
    @Test public void unsupportedCheckFunctionsAndScriptSeparatorsAreBlocked() {
        TablePlan plan = new TablePlan(); Column column = new Column(); column.name = "ID"; plan.columns.add(column);
        for (String text : new String[] { "CUSTOM(ID)>0", "ID>0; DROP TABLE X", "ID>0 -- comment" }) {
            try { MigrationConstraints.checkExpression(text, plan); fail(text); } catch (BusinessException expected) { }
        }
    }
    @Test public void manualDdlCannotReplaceOtherObjectsOrRunBatchScripts() {
        TablePlan plan = new TablePlan(); plan.type = "VIEW"; plan.targetSchema = "QA"; plan.target = "V";
        assertEquals("CREATE VIEW \"QA\".\"V\" AS SELECT 1 AS X FROM DUAL", MigrationObjects.reviewedSql("CREATE VIEW \"QA\".\"V\" AS SELECT 1 AS X FROM DUAL;", plan));
        for (String sql : new String[] { "CREATE OR REPLACE VIEW \"QA\".\"V\" AS SELECT 1 FROM DUAL", "CREATE VIEW \"QA\".\"OTHER\" AS SELECT 1 FROM DUAL", "CREATE VIEW \"QA\".\"V\" AS SELECT 1 FROM DUAL; DROP TABLE X", "BEGIN NULL; END;" }) {
            try { MigrationObjects.reviewedSql(sql, plan); fail(sql); } catch (BusinessException expected) { }
        }
    }
    @Test public void proceduralSemicolonsAreNotSplitIntoBatchStatements() {
        TablePlan plan = new TablePlan(); plan.type = "PROCEDURE"; plan.targetSchema = "QA"; plan.target = "P";
        assertTrue(MigrationObjects.reviewedSql("CREATE PROCEDURE \"QA\".\"P\" AS BEGIN IF 1=1 THEN NULL; END IF; END;\n/", plan).endsWith("END;"));
    }
    @Test public void dependenciesOrderTablesBeforeViewsAndBlockCycles() {
        Assessment report = new Assessment(); TablePlan a = new TablePlan(), b = new TablePlan();
        a.sourceSchema = b.sourceSchema = "QA"; a.source = "A"; b.source = "B"; b.type = "VIEW"; b.dependencies.add(qualified("QA", "A"));
        report.tables.add(b); report.tables.add(a); MigrationObjects.order(report); assertSame(a, report.tables.get(0));
        a.dependencies.add(qualified("QA", "B")); MigrationObjects.order(report); assertFalse(report.isReady());
    }
    @Test public void dmAndOracleTriggerStatusMustPreserveEnabledAndDisabled() {
        assertTrue(MigrationObjects.triggerEnabled("Y")); assertTrue(MigrationObjects.triggerEnabled("ENABLED"));
        assertFalse(MigrationObjects.triggerEnabled("N")); assertFalse(MigrationObjects.triggerEnabled("DISABLED"));
        try { MigrationObjects.triggerEnabled("UNKNOWN"); fail(); } catch (BusinessException expected) { }
    }
    @Test public void unsupportedConstraintAndIndexStatesCannotBeSilentlyChanged() {
        MigrationConstraints.validateConstraint("ENABLED", "NOT DEFERRABLE", "VALIDATED");
        for (String[] state : new String[][] { { "DISABLED", "NOT DEFERRABLE", "VALIDATED" }, { "ENABLED", "DEFERRABLE", "VALIDATED" }, { "ENABLED", "NOT DEFERRABLE", "NOT VALIDATED" } }) {
            try { MigrationConstraints.validateConstraint(state[0], state[1], state[2]); fail(); } catch (BusinessException expected) { }
        }
        MigrationConstraints.validateIndex("VALID", "VISIBLE");
        for (String[] state : new String[][] { { "UNUSABLE", "VISIBLE" }, { "VALID", "INVISIBLE" } }) {
            try { MigrationConstraints.validateIndex(state[0], state[1]); fail(); } catch (BusinessException expected) { }
        }
    }
}
