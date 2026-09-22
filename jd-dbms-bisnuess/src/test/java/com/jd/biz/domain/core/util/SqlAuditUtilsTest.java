package com.jd.biz.domain.core.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SqlAuditUtilsTest {

    @Test
    public void shouldMaskCommonPasswordSyntax() {
        String sql = "alter user APP identified by Secret123; "
                + "set password = 'AnotherSecret'; password := 'PlSqlSecret'; "
                + "pwd => 'NamedArgumentSecret'; connect SYSDBA/Manager123";

        String sanitized = SqlAuditUtils.sanitizeSql(sql);

        assertFalse(sanitized.contains("Secret123"));
        assertFalse(sanitized.contains("AnotherSecret"));
        assertFalse(sanitized.contains("PlSqlSecret"));
        assertFalse(sanitized.contains("NamedArgumentSecret"));
        assertFalse(sanitized.contains("Manager123"));
        assertEquals("alter user APP identified by '******'; "
                + "set password = '******'; password := '******'; "
                + "pwd => '******'; connect SYSDBA/******", sanitized);
    }

    @Test
    public void shouldKeepOrdinaryPasswordColumnQueriesReadable() {
        String sql = "select password from app_user where id = 1";

        assertEquals(sql, SqlAuditUtils.sanitizeSql(sql));
    }

    @Test
    public void shouldResolveSqlTypeFromReportedTypeOrStatement() {
        assertEquals("SELECT", SqlAuditUtils.resolveSqlType("SELECT", "update demo set value = 1"));
        assertEquals("UPDATE", SqlAuditUtils.resolveSqlType("UNKNOWN", "-- audit\n update demo set value = 1"));
        assertEquals("SELECT", SqlAuditUtils.resolveSqlType(null, "/* report */ with data as (select 1) select * from data"));
    }
}
