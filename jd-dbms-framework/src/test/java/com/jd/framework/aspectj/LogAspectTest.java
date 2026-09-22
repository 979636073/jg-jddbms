package com.jd.framework.aspectj;

import com.jd.common.core.domain.AjaxResult;
import com.jd.common.enums.BusinessStatus;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.system.domain.SysOperLog;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LogAspectTest {

    private final LogAspect logAspect = new LogAspect();

    @Test
    public void shouldMarkResultFailureInAuditLog() {
        SysOperLog operLog = successfulLog();

        logAspect.updateStatusFromResult(operLog,
                ActionResult.fail("user.drop.partialFailed", "one user failed", null));

        assertEquals(Integer.valueOf(BusinessStatus.FAIL.ordinal()), operLog.getStatus());
        assertEquals("one user failed", operLog.getErrorMsg());
    }

    @Test
    public void shouldMarkAjaxFailureInAuditLog() {
        SysOperLog operLog = successfulLog();

        logAspect.updateStatusFromResult(operLog, AjaxResult.error("operation failed"));

        assertEquals(Integer.valueOf(BusinessStatus.FAIL.ordinal()), operLog.getStatus());
        assertEquals("operation failed", operLog.getErrorMsg());
    }

    private SysOperLog successfulLog() {
        SysOperLog operLog = new SysOperLog();
        operLog.setStatus(BusinessStatus.SUCCESS.ordinal());
        return operLog;
    }
}
