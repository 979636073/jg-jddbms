package com.jd.biz.domain.core.impl;

import com.jd.plugin.dm.builder.DMSqlBuilder;
import com.jd.plugin.oracle.builder.OracleSqlBuilder;
import org.junit.Assert;
import org.junit.Test;

public class FunctionTemplateSqlBuilderTest {

    @Test
    public void shouldBuildExecutableDmFunctionTemplate() {
        assertExecutableTemplate(new DMSqlBuilder().createFunctionTemplate("", "SYSDBA", "CODEX_FN_VERIFY"));
    }

    @Test
    public void shouldBuildExecutableOracleFunctionTemplate() {
        assertExecutableTemplate(new OracleSqlBuilder().createFunctionTemplate("orcl", "SYSTEM", "CODEX_FN_VERIFY"));
    }

    @Test
    public void shouldBuildExecutableProcedureTemplates() {
        for (String sql : new String[] {
                new DMSqlBuilder().createProcedureTemplate("", "SYSDBA", "CODEX_PROC_VERIFY"),
                new OracleSqlBuilder().createProcedureTemplate("orcl", "SYSTEM", "CODEX_PROC_VERIFY") }) {
            Assert.assertTrue(sql.contains("tmpVar NUMBER;"));
            Assert.assertTrue(sql.contains("NULL;"));
            Assert.assertTrue(sql.endsWith("END CODEX_PROC_VERIFY;"));
        }
    }

    @Test
    public void shouldBuildExecutableTriggerTemplates() {
        for (String sql : new String[] {
                new DMSqlBuilder().createTriggersTemplate("", "SYSDBA", "CODEX_TRG_VERIFY", "CODEX_TABLE", true, false),
                new OracleSqlBuilder().createTriggersTemplate("orcl", "SYSTEM", "CODEX_TRG_VERIFY", "CODEX_TABLE", true, false) }) {
            Assert.assertTrue(sql.contains("BEFORE INSERT OR UPDATE OR DELETE ON"));
            Assert.assertTrue(sql.contains("\"CODEX_TABLE\""));
            Assert.assertTrue(sql.contains("BEGIN NULL; END;"));
        }
    }

    private void assertExecutableTemplate(String sql) {
        Assert.assertTrue(sql.contains("tmpVar NUMBER;"));
        Assert.assertTrue(sql.contains("NULL;"));
        Assert.assertTrue(sql.endsWith("END CODEX_FN_VERIFY;"));
    }
}
