package com.jd.plugin.oracle;

import org.junit.Test;
import static org.junit.Assert.*;

public class OracleMetaDataColumnsSqlTest {
    @Test public void columnsIncludeCompositeKeyOrderWithoutLoadingAllIndexes() {
        String sql = OracleMetaData.buildColumnsSql("APP'ONE", "T'TWO");
        assertTrue(sql.contains("LEFT JOIN all_col_comments"));
        assertTrue(sql.contains("c.OWNER = cc.OWNER AND c.TABLE_NAME = cc.TABLE_NAME"));
        assertTrue(sql.contains("c.CONSTRAINT_TYPE = 'P'"));
        assertTrue(sql.contains("atc.OWNER = 'APP''ONE' AND atc.TABLE_NAME = 'T''TWO'"));
        assertTrue(sql.contains("pk.POSITION AS PK_POSITION"));
        assertFalse(sql.contains("all_indexes"));
    }
}
