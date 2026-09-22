package com.jd.spi.jdbc;

import com.jd.common.tools.base.excption.BusinessException;
import org.junit.Assert;
import org.junit.Test;

public class DefaultSqlBuilderUserTest {

    private final DefaultSqlBuilder sqlBuilder = new DefaultSqlBuilder();

    @Test
    public void shouldBuildUserWithOptionalTableSpaces() {
        Assert.assertEquals(
                "CREATE USER \"JDDBMS_USER\" IDENTIFIED BY \"Pass123\"",
                sqlBuilder.createUser("JDDBMS_USER", "Pass123", null, ""));
        Assert.assertEquals(
                "CREATE USER \"JDDBMS_USER\" IDENTIFIED BY \"Pass123\" DEFAULT TABLESPACE \"USERS\" TEMPORARY TABLESPACE \"TEMP\"",
                sqlBuilder.createUser("JDDBMS_USER", "Pass123", "USERS", "TEMP"));
    }

    @Test
    public void shouldBuildRoleDifferenceSqlWithEscapedIdentifiers() {
        Assert.assertEquals(
                "GRANT \"APP\"\"ROLE\" TO \"TEST\"\"USER\" WITH ADMIN OPTION",
                sqlBuilder.addUserRole("TEST\"USER", "APP\"ROLE", "APP\"ROLE"));
        Assert.assertEquals(
                "REVOKE \"APP\"\"ROLE\" FROM \"TEST\"\"USER\"",
                sqlBuilder.delUserRole("TEST\"USER", "APP\"ROLE"));
    }

    @Test
    public void shouldBuildAtomicUserTableSpaceChange() {
        Assert.assertEquals(
                "ALTER USER \"JDDBMS_USER\" DEFAULT TABLESPACE \"USERS\" TEMPORARY TABLESPACE \"TEMP\"",
                sqlBuilder.modifyUserTableSpaces("JDDBMS_USER", "USERS", "TEMP"));
        Assert.assertEquals(
                "ALTER USER \"JDDBMS_USER\" DEFAULT TABLESPACE \"USERS\"",
                sqlBuilder.modifyUserTableSpaces("JDDBMS_USER", "USERS", null));
        Assert.assertEquals(
                "ALTER USER \"JDDBMS_USER\" TEMPORARY TABLESPACE \"TEMP\"",
                sqlBuilder.modifyUserTableSpaces("JDDBMS_USER", null, "TEMP"));
    }

    @Test(expected = BusinessException.class)
    public void shouldRejectUserTableSpaceChangeWithoutAnyTableSpace() {
        sqlBuilder.modifyUserTableSpaces("JDDBMS_USER", null, "");
    }

    @Test(expected = BusinessException.class)
    public void shouldRejectMissingPassword() {
        sqlBuilder.createUser("JDDBMS_USER", "", null, null);
    }
}
