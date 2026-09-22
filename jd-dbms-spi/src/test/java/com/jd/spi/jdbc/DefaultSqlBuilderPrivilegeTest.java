package com.jd.spi.jdbc;

import com.jd.common.tools.base.excption.BusinessException;
import org.junit.Assert;
import org.junit.Test;

public class DefaultSqlBuilderPrivilegeTest {

    private final PrivilegeSqlBuilder sqlBuilder = new PrivilegeSqlBuilder();

    @Test
    public void shouldBuildGrantWithOptionalPrivileges() {
        String sql = sqlBuilder.build(true, null, "SYSTEM", "TEST_TABLE", "TEST_USER",
                true, true, true);

        Assert.assertEquals(
                "GRANT SELECT, INSERT, UPDATE, DELETE ON \"SYSTEM\".\"TEST_TABLE\" TO \"TEST_USER\"",
                sql);
    }

    @Test
    public void shouldBuildRevokeAndTreatNullFlagsAsFalse() {
        String sql = sqlBuilder.build(false, "SYSDBA", null, "TEST_TABLE", "TEST_USER",
                null, null, null);

        Assert.assertEquals(
                "REVOKE SELECT ON \"SYSDBA\".\"TEST_TABLE\" FROM \"TEST_USER\"",
                sql);
    }

    @Test
    public void shouldEscapeQuotedIdentifiers() {
        String sql = sqlBuilder.build(true, null, "OWNER", "A\"B", "TEST_USER",
                false, false, false);

        Assert.assertEquals("GRANT SELECT ON \"OWNER\".\"A\"\"B\" TO \"TEST_USER\"", sql);
    }

    @Test(expected = BusinessException.class)
    public void shouldRejectMissingOwner() {
        sqlBuilder.build(true, null, "", "TEST_TABLE", "TEST_USER",
                false, false, false);
    }

    private static class PrivilegeSqlBuilder extends DefaultSqlBuilder {
        private String build(boolean grant, String databaseName, String schemaName, String tableName,
                             String toGrantUser, Boolean insert, Boolean update, Boolean delete) {
            return buildTablePrivilegeSql(grant, databaseName, schemaName, tableName, toGrantUser,
                    insert, update, delete);
        }
    }
}
