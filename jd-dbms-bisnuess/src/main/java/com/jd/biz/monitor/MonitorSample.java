package com.jd.biz.monitor;

import lombok.Data;

/** Only aggregate metrics are persisted; session users, SQL and client addresses stay transient. */
@Data
public class MonitorSample {
    private long capturedAt;
    private Long sessionCount;
    private Long activeCount;
    private Long blockedCount;
    private Double spacePercent;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Double observedSpacePercent;
    private String error;
}
