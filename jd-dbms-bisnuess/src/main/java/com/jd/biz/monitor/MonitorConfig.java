package com.jd.biz.monitor;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Getter
@Setter
public class MonitorConfig extends DataSourceBaseRequest {
    private boolean enabled;
    @Min(1) @Max(100000)
    private int activeThreshold = 30;
    @Min(1) @Max(100)
    private int spaceThreshold = 85;
    private Long userId;
    private String revision;
}
