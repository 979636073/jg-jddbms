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

    private void assertExecutableTemplate(String sql) {
        Assert.assertTrue(sql.contains("tmpVar NUMBER;"));
        Assert.assertTrue(sql.contains("NULL;"));
        Assert.assertTrue(sql.endsWith("END CODEX_FN_VERIFY;"));
    }
}
