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
    public void shouldUseActualProcedureTypeForDetail() {
        Assert.assertEquals(
                "SELECT OWNER, NAME, TEXT FROM ALL_SOURCE WHERE TYPE = 'PROCEDURE' AND OWNER = 'SYSDBA' AND NAME = 'TEST_PROC' ORDER BY LINE",
                DMMetaData.buildProcedureDetailSql("SYSDBA", "TEST_PROC"));
    }

    @Test
    public void shouldExcludeFunctionsFromProcedureList() {
        Assert.assertEquals(
                "SELECT OWNER, OBJECT_NAME FROM ALL_OBJECTS WHERE OBJECT_TYPE = 'PROCEDURE' AND OWNER = 'SYSDBA' ORDER BY OWNER, OBJECT_NAME",
                DMMetaData.buildProcedureListSql("SYSDBA"));
    }

    @Test
    public void shouldListAllSchemaTriggersWithoutTableFilter() {
        String sql = DMMetaData.buildTriggerListSql("SYSDBA", null);
        Assert.assertTrue(sql.contains("OWNER = 'SYSDBA'"));
        Assert.assertFalse(sql.contains("TABLE_NAME ="));
        Assert.assertTrue(DMMetaData.buildTriggerListSql("SYSDBA", "MY_TABLE")
                .contains("TABLE_NAME = 'MY_TABLE'"));
    }

    @Test
    public void shouldReadExecutableTriggerDefinition() {
        Assert.assertEquals(
                "SELECT DBMS_METADATA.GET_DDL('TRIGGER', 'TEST_TR', 'SYSDBA') AS DDL FROM DUAL",
                DMMetaData.buildTriggerDdlSql("SYSDBA", "TEST_TR"));
    }

    @Test
    public void shouldEscapeSchemaLiteral() {
        Assert.assertTrue(DMMetaData.buildFunctionsSql("SYS'DBA").contains("OWNER = 'SYS''DBA'"));
    }

    @Test
    public void shouldQueryStandaloneIndexesWithoutConstraintOrClusterIndexes() {
        String sql = DMMetaData.buildStandaloneIndexDdlSql("JD'DBMS", "MY'TABLE");
        Assert.assertTrue(sql.contains("TABLE_OWNER = 'JD''DBMS'"));
        Assert.assertTrue(sql.contains("TABLE_NAME = 'MY''TABLE'"));
        Assert.assertTrue(sql.contains("INDEX_TYPE NOT IN ('CLUSTER', 'VIRTUAL')"));
        Assert.assertTrue(sql.contains("CONSTRAINT_TYPE IN ('P', 'U')"));
    }
}
