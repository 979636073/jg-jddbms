package com.jd.biz.controller.rdb;

import com.jd.common.tools.base.excption.BusinessException;
import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DatabasePrivilegeHintsTest {
    @Test
    public void explainsMissingSystemViewPrivilege() {
        RuntimeException error = new BusinessException("user.query.failed", new Object[]{"permission denied"},
                new RuntimeException(new SQLException("No select privilege on SYS.DBA_USERS")));

        RuntimeException translated = DatabasePrivilegeHints.translate(error, "DBA_USERS");

        assertTrue(translated instanceof BusinessException);
        assertEquals("rdb.systemViewPermissionDenied", ((BusinessException) translated).getCode());
        assertEquals("SYS.DBA_USERS", ((BusinessException) translated).getArgs()[0]);
    }

    @Test
    public void preservesUnrelatedDatabaseFailure() {
        RuntimeException error = new RuntimeException(new SQLException("Connection closed"));

        assertSame(error, DatabasePrivilegeHints.translate(error, "DBA_USERS"));
    }
}
