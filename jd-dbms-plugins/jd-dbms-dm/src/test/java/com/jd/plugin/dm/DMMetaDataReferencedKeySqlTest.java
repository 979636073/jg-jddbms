package com.jd.plugin.dm;

import org.junit.Assert;
import org.junit.Test;

public class DMMetaDataReferencedKeySqlTest {

    @Test
    public void shouldMatchCompositeForeignKeyColumnsByPosition() {
        String sql = DMMetaData.buildReferencedForeignKeySql("SYSDBA", "PARENT_TABLE");

        Assert.assertTrue(sql.contains("WHERE OWNER = 'SYSDBA' AND TABLE_NAME = 'PARENT_TABLE'"));
        Assert.assertTrue(sql.contains("fk.R_OWNER = pk.OWNER"));
        Assert.assertTrue(sql.contains("fk_col.OWNER = fk.OWNER"));
        Assert.assertTrue(sql.contains("pk_col.POSITION = fk_col.POSITION"));
        Assert.assertTrue(sql.contains("ORDER BY fk.TABLE_NAME, fk.CONSTRAINT_NAME, fk_col.POSITION"));
    }

    @Test
    public void shouldEscapeReferencedForeignKeyIdentifiers() {
        String sql = DMMetaData.buildReferencedForeignKeySql("SYS'DBA", "PARENT'TABLE");

        Assert.assertTrue(sql.contains("OWNER = 'SYS''DBA'"));
        Assert.assertTrue(sql.contains("TABLE_NAME = 'PARENT''TABLE'"));
    }
}
