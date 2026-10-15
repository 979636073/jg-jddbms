package com.jd.biz.monitor;

import com.jd.biz.aspect.ConnectionInfoHandler;
import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.model.DataSourcePermissionPreview;
import com.jd.biz.domain.api.service.DataSourceAccessBusinessService;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.common.exception.PermissionDeniedBusinessException;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.spi.sql.Chat2DBContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DatabaseMonitorService {
    private final MonitorRepository repository;
    private final DatabaseSnapshotCollector collector;
    private final DataSourceService sources;
    private final DataSourceAccessBusinessService access;
    private final ConnectionInfoHandler connections;
    private final Object recordingLock = new Object();
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private int dispatchOffset;
    private final ExecutorService workers = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), task -> {
                Thread thread = new Thread(task, "database-monitor");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public DatabaseMonitorService(MonitorRepository repository, DatabaseSnapshotCollector collector,
            DataSourceService sources, DataSourceAccessBusinessService access, ConnectionInfoHandler connections) {
        this.repository = repository;
        this.collector = collector;
        this.sources = sources;
        this.access = access;
        this.connections = connections;
    }

    public Map<String, Object> state(long id) {
        DataSource source = source(id);
        access.checkActionPermission(source, DataSourceAction.MONITOR_VIEW);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("config", repository.config(id));
        state.put("history", repository.history(id));
        state.put("alerts", repository.alerts(id));
        state.put("canManage", canManage(source, ContextUtils.getLoginUser().getId()));
        return state;
    }

    public MonitorConfig configure(MonitorConfig config) {
        DataSource source = source(config.getDataSourceId());
        access.checkActionPermission(source, DataSourceAction.MONITOR_VIEW);
        access.checkActionPermission(source, DataSourceAction.DATASOURCE_MANAGE);
        if (!"DM".equalsIgnoreCase(source.getType()) && !"ORACLE".equalsIgnoreCase(source.getType())) {
            throw new BusinessException("监控采集仅支持达梦和 Oracle 数据源");
        }
        config.setUserId(ContextUtils.getLoginUser().getId());
        config.setRevision(UUID.randomUUID().toString());
        synchronized (recordingLock) { repository.save(config); }
        if (config.isEnabled()) submit(config);
        return config;
    }

    private DataSource source(long id) {
        DataSource source = sources.queryById(id).getData();
        if (source == null) throw new BusinessException("数据源已删除或不存在");
        return source;
    }

    private boolean canManage(DataSource source, Long userId) {
        DataSourcePermissionPreview preview = access.preview(source, userId);
        return preview.isActive() && allowed(preview, DataSourceAction.MONITOR_VIEW)
                && allowed(preview, DataSourceAction.DATASOURCE_MANAGE);
    }

    private boolean allowed(DataSourcePermissionPreview preview, DataSourceAction action) {
        return preview.getActions().stream().anyMatch(item -> action.name().equals(item.getCode()) && item.isAllowed());
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 30000)
    public void tick() {
        List<MonitorConfig> configs = repository.enabled();
        // Rotate the first source so a full queue cannot starve connections at the end of the list.
        if (!configs.isEmpty()) Collections.rotate(configs, -(dispatchOffset++ % configs.size()));
        for (MonitorConfig config : configs) submit(config);
        repository.prune(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7));
    }

    private void submit(MonitorConfig config) {
        if (!running.add(config.getDataSourceId())) return;
        try {
            workers.execute(() -> {
                try { sample(config); }
                catch (RuntimeException error) {
                    // Do not log JDBC exception messages, connection strings or credentials.
                    log.warn("Monitoring persistence failed for datasource {} ({})",
                            config.getDataSourceId(), error.getClass().getSimpleName());
                } finally { running.remove(config.getDataSourceId()); }
            });
        } catch (RejectedExecutionException busy) { running.remove(config.getDataSourceId()); }
    }

    void sample(MonitorConfig config) {
        MonitorConfig scheduled = repository.config(config.getDataSourceId());
        if (!scheduled.isEnabled() || !Objects.equals(scheduled.getRevision(), config.getRevision())) return;
        MonitorSample sample;
        try {
            DataSource source = source(config.getDataSourceId());
            // Re-read current platform permissions, account status and resource grants on every collection.
            if (!canManage(source, config.getUserId())) throw new PermissionDeniedBusinessException();
            Chat2DBContext.putContext(connections.toInfo(config.getDataSourceId(), null, false));
            sample = aggregate(collector.collect());
        } catch (RuntimeException error) {
            sample = new MonitorSample();
            sample.setCapturedAt(System.currentTimeMillis());
            sample.setError(error instanceof PermissionDeniedBusinessException
                    ? "采集已暂停：配置者账号或监控、连接管理权限已失效" : "采集失败：请检查数据源连接、数据库权限及配置者权限");
        } finally { Chat2DBContext.removeContext(); }
        synchronized (recordingLock) {
            MonitorConfig current = repository.config(config.getDataSourceId());
            // Disabling/changing configuration while a query is in flight must discard its old result.
            if (current.isEnabled() && Objects.equals(current.getRevision(), config.getRevision())) {
                repository.record(config, sample);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static MonitorSample aggregate(Map<String, Object> snapshot) {
        MonitorSample sample = new MonitorSample();
        sample.setCapturedAt(((Number) snapshot.get("capturedAt")).longValue());
        sample.setSessionCount(number(snapshot.get("sessionCount")));
        sample.setActiveCount(number(snapshot.get("activeCount")));
        sample.setBlockedCount(number(snapshot.get("blockedCount")));
        List<Map<String, Object>> files = (List<Map<String, Object>>) snapshot.get("tablespaceFiles");
        if (files != null) {
            files.stream().filter(file -> file.get("usagePercent") instanceof Number)
                    .mapToDouble(file -> ((Number) file.get("usagePercent")).doubleValue()).max()
                    .ifPresent(sample::setObservedSpacePercent);
        }
        if (files != null && !files.isEmpty() && files.stream().allMatch(file -> file.get("usagePercent") instanceof Number)) {
            sample.setSpacePercent(sample.getObservedSpacePercent());
        }
        Map<String, ?> errors = (Map<String, ?>) snapshot.get("errors");
        if (errors != null && !errors.isEmpty() || sample.getSessionCount() == null || sample.getActiveCount() == null
                || sample.getBlockedCount() == null || sample.getSpacePercent() == null) {
            sample.setError("部分指标无法读取，请检查动态视图和表空间查询权限");
        }
        return sample;
    }

    private static Long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : null; }

    @PreDestroy
    public void close() { workers.shutdownNow(); }
}
