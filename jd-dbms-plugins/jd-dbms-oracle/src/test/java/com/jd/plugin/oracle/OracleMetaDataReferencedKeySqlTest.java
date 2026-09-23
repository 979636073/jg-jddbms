package com.jd.plugin.oracle;

import org.junit.Assert;
import org.junit.Test;

public class OracleMetaDataReferencedKeySqlTest {

    @Test
    public void shouldBuildSelectiveReferencedForeignKeyQuery() {
        String sql = OracleMetaData.buildReferencedForeignKeySql("SYSTEM", "PARENT_TABLE");

        Assert.assertTrue(sql.contains("/*+ MATERIALIZE */"));
        Assert.assertTrue(sql.contains("WHERE OWNER = 'SYSTEM' AND TABLE_NAME = 'PARENT_TABLE'"));
        Assert.assertTrue(sql.contains("fk.R_OWNER = pk.OWNER"));
        Assert.assertTrue(sql.contains("fk_col.OWNER = fk.OWNER"));
        Assert.assertTrue(sql.contains("pk_col.POSITION = fk_col.POSITION"));
    }

    @Test
    public void shouldEscapeReferencedForeignKeyIdentifiers() {
        String sql = OracleMetaData.buildReferencedForeignKeySql("SYS'TEM", "PARENT'TABLE");

        Assert.assertTrue(sql.contains("OWNER = 'SYS''TEM'"));
        Assert.assertTrue(sql.contains("TABLE_NAME = 'PARENT''TABLE'"));
    }
}
