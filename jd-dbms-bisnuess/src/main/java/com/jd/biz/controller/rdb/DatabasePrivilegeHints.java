package com.jd.biz.controller.rdb;

import com.jd.common.tools.base.excption.BusinessException;

import java.util.Locale;

/** Turns a missing system-view grant into an actionable database-account error. */
final class DatabasePrivilegeHints {
    private DatabasePrivilegeHints() {
    }

    static RuntimeException translate(RuntimeException error, String... systemViews) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            String upper = message.toUpperCase(Locale.ROOT);
            if (!upper.contains("NO SELECT PRIVILEGE") && !upper.contains("SELECT PRIVILEGE")
                    && !upper.contains("SELECT 权限")) {
                continue;
            }
            for (String view : systemViews) {
                if (upper.contains(view)) {
                    return new BusinessException("rdb.systemViewPermissionDenied", new Object[]{"SYS." + view}, error);
                }
            }
        }
        return error;
    }
}
