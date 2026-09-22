package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.plugin.oracle.builder.OracleSqlBuilder;
import com.jd.spi.jdbc.DefaultSqlBuilder;
import com.jd.spi.model.TableObjectRoleData;
import com.jd.spi.model.TableRoleData;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TableUserServiceRoleChangeTest {

    private final TableUserServiceImpl service = new TableUserServiceImpl();

    @Test
    public void shouldOnlyPlanChangedUserRoles() {
        List<TableRoleData> oldRoles = Arrays.asList(
                role("RESOURCE", true, false),
                role("DBA", true, false));
        List<TableRoleData> newRoles = Arrays.asList(
                role("RESOURCE", true, false),
                role("DBA", true, true),
                role("CONNECT", true, false));

        List<TableUserServiceImpl.SqlChange> changes = service.buildUserRoleChanges(
                new DefaultSqlBuilder(), "JDDBMS_USER", newRoles, oldRoles);

        Assert.assertEquals(Arrays.asList(
                        "REVOKE \"DBA\" FROM \"JDDBMS_USER\"",
                        "GRANT \"DBA\" TO \"JDDBMS_USER\" WITH ADMIN OPTION",
                        "GRANT \"CONNECT\" TO \"JDDBMS_USER\""),
                sqlList(changes));
        Assert.assertEquals("GRANT \"DBA\" TO \"JDDBMS_USER\"",
                changes.get(0).getRollbackSql());
    }

    @Test
    public void shouldOnlyPlanChangedObjectPrivileges() {
        TableBriefQueryRequest request = new TableBriefQueryRequest();
        request.setSchemaName("SYSTEM");
        request.setTableName("TEST_TABLE");
        request.setUserName("JDDBMS_USER");
        request.setOldObjectRoleData(Arrays.asList(
                objectRole("SELECT", true, false),
                objectRole("INSERT", true, false)));
        request.setNewObjectRoleData(Arrays.asList(
                objectRole("SELECT", true, true),
                objectRole("INSERT", false, false),
                objectRole("UPDATE", true, false)));

        List<TableUserServiceImpl.SqlChange> changes = service.buildObjectRoleChanges(
                new OracleSqlBuilder(), request);

        Assert.assertEquals(Arrays.asList(
                        "REVOKE SELECT ON \"SYSTEM\".\"TEST_TABLE\" FROM \"JDDBMS_USER\"",
                        "REVOKE INSERT ON \"SYSTEM\".\"TEST_TABLE\" FROM \"JDDBMS_USER\"",
                        "grant SELECT on \"SYSTEM\".\"TEST_TABLE\" to \"JDDBMS_USER\" with grant option",
                        "grant UPDATE on \"SYSTEM\".\"TEST_TABLE\" to \"JDDBMS_USER\""),
                sqlList(changes));
    }

    private List<String> sqlList(List<TableUserServiceImpl.SqlChange> changes) {
        return changes.stream().map(TableUserServiceImpl.SqlChange::getSql).collect(Collectors.toList());
    }

    private TableRoleData role(String name, boolean granted, boolean admin) {
        TableRoleData role = new TableRoleData();
        role.setRole(name);
        role.setIsGranted(granted);
        role.setIsAdmin(admin);
        return role;
    }

    private TableObjectRoleData objectRole(String name, boolean granted, boolean grantOption) {
        TableObjectRoleData role = new TableObjectRoleData();
        role.setDesc(name);
        role.setRule(granted);
        role.setToRule(grantOption);
        return role;
    }
}
