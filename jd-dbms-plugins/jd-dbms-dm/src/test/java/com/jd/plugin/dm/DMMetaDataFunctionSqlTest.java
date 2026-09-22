package com.jd.plugin.dm;

import org.junit.Assert;
import org.junit.Test;

public class DMMetaDataFunctionSqlTest {

    @Test
    public void shouldQueryStandaloneFunctionsForSchema() {
        Assert.assertEquals(
                "SELECT OWNER, OBJECT_NAME FROM ALL_OBJECTS WHERE OBJECT_TYPE = 'FUNCTION' AND OWNER = 'SYSDBA' ORDER BY OWNER, OBJECT_NAME",
                DMMetaData.buildFunctionsSql("SYSDBA"));
    }

    @Test
    public void shouldUseFunctionTypeForDetail() {
        Assert.assertEquals(
                "SELECT OWNER, NAME, TEXT FROM ALL_SOURCE WHERE TYPE = 'FUNCTION' AND OWNER = 'SYSDBA' AND NAME = 'TEST_FN' ORDER BY LINE",
                DMMetaData.buildFunctionDetailSql("SYSDBA", "TEST_FN"));
    }

    @Test
    public void shouldEscapeSchemaLiteral() {
        Assert.assertTrue(DMMetaData.buildFunctionsSql("SYS'DBA").contains("OWNER = 'SYS''DBA'"));
    }
}
