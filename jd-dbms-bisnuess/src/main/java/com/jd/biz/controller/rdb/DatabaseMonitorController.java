package com.jd.biz.controller.rdb;

import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.monitor.DatabaseSnapshotCollector;
import com.jd.biz.monitor.DatabaseMonitorService;
import com.jd.biz.monitor.MonitorConfig;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.annotation.Log;
import com.jd.common.enums.BusinessType;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.Map;

@ConnectionInfoAspect
@RestController
@RequestMapping("/api/rdb/monitor")
public class DatabaseMonitorController {
    private final DatabaseSnapshotCollector collector;
    private final DatabaseMonitorService monitor;

    public DatabaseMonitorController(DatabaseSnapshotCollector collector, DatabaseMonitorService monitor) {
        this.collector = collector;
        this.monitor = monitor;
    }

    @GetMapping("/snapshot")
    public DataResult<Map<String, Object>> snapshot(@Valid TableBriefQueryRequest request) {
        return DataResult.of(collector.collect());
    }

    @GetMapping("/state")
    public DataResult<Map<String, Object>> state(@Valid TableBriefQueryRequest request) {
        return DataResult.of(monitor.state(request.getDataSourceId()));
    }

    @PutMapping("/config")
    @Log(title = "数据库监控配置", businessType = BusinessType.UPDATE)
    public DataResult<MonitorConfig> configure(@RequestBody @Valid MonitorConfig config) {
        return DataResult.of(monitor.configure(config));
    }
}
