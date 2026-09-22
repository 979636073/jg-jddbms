package com.jd.plugin.oracle;

import org.junit.Assert;
import org.junit.Test;

public class OracleMetaDataFunctionSqlTest {

    @Test
    public void shouldQueryStandaloneFunctionsForSchema() {
        Assert.assertEquals(
                "SELECT OWNER, OBJECT_NAME FROM ALL_OBJECTS WHERE OBJECT_TYPE = 'FUNCTION' AND OWNER = 'SYSTEM' ORDER BY OWNER, OBJECT_NAME",
                OracleMetaData.buildFunctionsSql("SYSTEM"));
    }

    @Test
    public void shouldEscapeSchemaLiteral() {
        Assert.assertTrue(OracleMetaData.buildFunctionsSql("SYS'TEM").contains("OWNER = 'SYS''TEM'"));
    }
}
