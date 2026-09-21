package com.jd.biz.domain.core.impl;

import com.jd.spi.sql.SQLExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically closes expired database transaction sessions. */
@Component
public class SqlSessionCleanupTask {

    @Scheduled(fixedDelay = 300000L)
    public void closeExpiredSessions() {
        SQLExecutor.getInstance().removeTimeoutConnection();
    }
}
