package com.jd.plugin.dm;

import org.junit.Assert;
import org.junit.Test;

public class DMMetaDataReferencedKeySqlTest {

    @Test
    public void shouldResolveOnlyTargetConstraintsBeforeBatchReadingColumns() {
        String sql = DMMetaData.buildReferencedForeignKeySql("SYSDBA", "PARENT_TABLE");

        Assert.assertTrue(sql.contains("WHERE pk.OWNER = 'SYSDBA' AND pk.TABLE_NAME = 'PARENT_TABLE'"));
        Assert.assertTrue(sql.contains("fk.R_OWNER = pk.OWNER"));
        Assert.assertFalse(sql.contains("ALL_CONS_COLUMNS"));
        Assert.assertTrue(sql.contains("pk.CONSTRAINT_TYPE IN ('P', 'U')"));
        Assert.assertTrue(sql.contains("ORDER BY fk.TABLE_NAME, fk.CONSTRAINT_NAME"));
    }

    @Test
    public void shouldEscapeReferencedForeignKeyIdentifiers() {
        String sql = DMMetaData.buildReferencedForeignKeySql("SYS'DBA", "PARENT'TABLE");

        Assert.assertTrue(sql.contains("OWNER = 'SYS''DBA'"));
        Assert.assertTrue(sql.contains("TABLE_NAME = 'PARENT''TABLE'"));
    }
}
