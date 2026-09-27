package com.jd.plugin.dm;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class DMMetaDataColumnSqlTest {

    @Test
    public void shouldQueryIdentityColumnsThroughAccessibleCatalogSynonyms() {
        String sql = DMMetaData.buildAutoIncrementSql("APP", "TEST_TABLE");

        Assert.assertTrue(sql.contains("SYSCOLUMNS A"));
        Assert.assertTrue(sql.contains("SYSOBJECTS C"));
        Assert.assertFalse(sql.contains("SYS.SYSCOLUMNS"));
        Assert.assertFalse(sql.contains("SYS.SYSOBJECTS"));
        Assert.assertTrue(sql.contains("(A.INFO2 & 1)=1"));
    }

    @Test
    public void shouldEscapeIdentityColumnQueryLiterals() {
        String sql = DMMetaData.buildAutoIncrementSql("APP'OWNER", "TEST'TABLE");

        Assert.assertTrue(sql.contains("B.OWNER      = 'APP''OWNER'"));
        Assert.assertTrue(sql.contains("B.TABLE_NAME = 'TEST''TABLE'"));
    }

    @Test
    public void shouldBuildEscapedTableDdlFallbackQuery() {
        Assert.assertEquals(
                "SELECT DBMS_METADATA.GET_DDL('TABLE', 'TEST''TABLE', 'APP''OWNER') AS DDL FROM DUAL",
                DMMetaData.buildTableDdlSql("APP'OWNER", "TEST'TABLE"));
    }

    @Test
    public void shouldParseIdentityColumnsFromTableDdl() {
        String ddl = "CREATE TABLE \"APP\".\"TEST_TABLE\"\r\n(\r\n"
                + "\"ID\" INTEGER IDENTITY(1, 1) NOT NULL,\r\n"
                + "\"NAME\" VARCHAR(100),\r\n"
                + "\"SEQ\" BIGINT AUTO_INCREMENT\r\n)";

        Assert.assertEquals(Arrays.asList("ID", "SEQ"), DMMetaData.parseAutoIncrementColumns(ddl));
    }
}
