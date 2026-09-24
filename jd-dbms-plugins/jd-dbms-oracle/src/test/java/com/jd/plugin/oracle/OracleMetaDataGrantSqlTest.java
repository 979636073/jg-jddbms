package com.jd.plugin.oracle;

import org.junit.Assert;
import org.junit.Test;

public class OracleMetaDataGrantSqlTest {

    @Test
    public void shouldReadGrantsWithoutDbaPrivileges() {
        Assert.assertTrue(OracleMetaData.QUERY_ROLES_SQL.contains("FROM ALL_TAB_PRIVS"));
        Assert.assertTrue(OracleMetaData.QUERY_ROLES_SQL.contains("TABLE_SCHEMA AS OWNER"));
        Assert.assertTrue(OracleMetaData.QUERY_ROLES_SQL.contains("WHERE TABLE_SCHEMA = '%s'"));
    }
}
