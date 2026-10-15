package com.jd.plugin.dm;

import org.junit.Test;
import static org.junit.Assert.*;

public class DMMetaDataColumnsSqlTest {
    @Test public void columnsJoinPrimaryKeysAndRetainColumnsWithoutComments() {
        String sql = DMMetaData.buildColumnsSql("APP'ONE", "T'TWO");
        assertTrue(sql.contains("LEFT JOIN ALL_COL_COMMENTS"));
        assertTrue(sql.contains("C.OWNER = CC.OWNER AND C.TABLE_NAME = CC.TABLE_NAME"));
        assertTrue(sql.contains("C.CONSTRAINT_TYPE = 'P'"));
        assertTrue(sql.contains("ATC.OWNER = 'APP''ONE' AND ATC.TABLE_NAME = 'T''TWO'"));
        assertTrue(sql.contains("PK.POSITION AS PK_POSITION"));
        assertFalse(sql.contains("ALL_INDEXES"));
    }
    @Test public void identityLookupIsRestrictedToTheRequestedSchema() {
        assertTrue(DMMetaData.buildAutoIncrementSql("APP", "T").contains(
                "C.SCHID = (SELECT ID FROM SYSOBJECTS WHERE TYPE$ = 'SCH' AND NAME = 'APP')"));
    }
}
