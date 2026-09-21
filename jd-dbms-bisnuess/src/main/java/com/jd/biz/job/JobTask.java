package com.jd.biz.job;

import com.jd.spi.sql.SQLExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 开启定时任务
 */
@EnableScheduling
@Component
@Slf4j
public class JobTask {


    /**
     * 定时任务清除无效会话 10分钟执行一次
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void cornDelSession() {
        try {
            log.info("-----------定时任务开始执行超时会话自动关闭-----------");
            SQLExecutor.getInstance().removeTimeoutConnection();
            log.info("-----------定时任务执行超时会话自动关闭结束-----------");
        } catch (Exception e) {
            log.error("定时任务执行超时会话自动关闭失败:" + e.getMessage());
        }
    }
}
