package com.jd.biz.domain.core.util;

import com.alibaba.druid.DbType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SqlSafetyCheckerTest {

    @Test
    public void shouldRequireConfirmationForDestructiveStatements() {
        List<String> risks = SqlSafetyChecker.findRisks(
                "drop table demo; truncate table audit_log", DbType.oracle);

        assertEquals(2, risks.size());
        assertTrue(risks.get(0).contains("DROP"));
        assertTrue(risks.get(1).contains("TRUNCATE"));
    }

    @Test
    public void shouldRequireConfirmationForUpdateAndDeleteWithoutWhere() {
        List<String> risks = SqlSafetyChecker.findRisks(
                "update demo set status = 1; delete from audit_log", DbType.dm);

        assertEquals(2, risks.size());
        assertTrue(risks.get(0).contains("UPDATE"));
        assertTrue(risks.get(1).contains("DELETE"));
    }

    @Test
    public void shouldAllowReadAndConditionedWriteStatements() {
        List<String> risks = SqlSafetyChecker.findRisks(
                "select * from demo; update demo set status = 1 where id = 1; "
                        + "delete from audit_log where created_at < sysdate - 30",
                DbType.oracle);

        assertTrue(risks.isEmpty());
    }

    @Test
    public void shouldNotTreatKeywordsInsideTextAsStatements() {
        List<String> risks = SqlSafetyChecker.findRisks(
                "select 'drop table demo' as text_value from dual", DbType.oracle);

        assertTrue(risks.isEmpty());
    }
}
