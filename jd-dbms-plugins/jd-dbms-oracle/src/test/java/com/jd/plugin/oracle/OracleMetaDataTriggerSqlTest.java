package com.jd.plugin.oracle;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OracleMetaDataTriggerSqlTest {
    @Test
    public void shouldListAllSchemaTriggersWhenNoTableSelected() {
        String all = OracleMetaData.buildTriggerListSql("APP", null);
        assertTrue(all.contains("OWNER = 'APP'"));
        assertFalse(all.contains("TABLE_NAME ="));
        assertTrue(OracleMetaData.buildTriggerListSql("APP", "ORDERS")
                .contains("TABLE_NAME = 'ORDERS'"));
    }
}
