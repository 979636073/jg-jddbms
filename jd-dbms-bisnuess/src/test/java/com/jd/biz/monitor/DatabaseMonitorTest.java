package com.jd.biz.monitor;

import com.jd.biz.aspect.ConnectionInfoHandler;
import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.model.DataSourcePermissionPreview;
import com.jd.biz.domain.api.service.DataSourceAccessBusinessService;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.spi.sql.ConnectInfo;
import org.junit.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class DatabaseMonitorTest {
    private JdbcTemplate jdbc;
    private MonitorRepository repository;

    @Before
    public void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        repository = new MonitorRepository(jdbc);
        repository.initialize();
    }

    @Test
    public void configurationRequiresSourceAndValidThresholds() {
        try (javax.validation.ValidatorFactory factory = javax.validation.Validation.buildDefaultValidatorFactory()) {
            MonitorConfig config = config();
            assertTrue(factory.getValidator().validate(config).isEmpty());
            config.setDataSourceId(null);
            config.setActiveThreshold(0);
            config.setSpaceThreshold(101);
            Set<String> fields = new HashSet<>();
            factory.getValidator().validate(config).forEach(error -> fields.add(error.getPropertyPath().toString()));
            assertEquals(new HashSet<>(Arrays.asList("dataSourceId", "activeThreshold", "spaceThreshold")), fields);
        }
    }

    @Test
    public void upgradeIsRepeatableAndConfigurationSurvivesNewRepository() {
        MonitorConfig config = config();
        repository.save(config);
        MonitorRepository restarted = new MonitorRepository(jdbc);
        restarted.initialize();
        assertTrue(restarted.config(42).isEnabled());
        assertEquals(config.getRevision(), restarted.config(42).getRevision());
        assertEquals(30, restarted.config(42).getActiveThreshold());
    }

    @Test
    public void repeatedHitsDeduplicateMissingValuesDoNotRecoverAndNewEpisodeIsRecorded() {
        MonitorConfig config = config();
        repository.record(config, sample(100, 31L, 1L, 90D));
        repository.record(config, sample(200, 32L, 1L, 91D));
        assertEquals(3, repository.alerts(42).size());
        assertEquals(200L, repository.alerts(42).get(0).get("lastSeen"));
        MonitorSample missing = sample(300, null, null, null);
        missing.setError("数据库不可用");
        repository.record(config, missing);
        assertEquals(4, repository.alerts(42).size());
        assertTrue(repository.alerts(42).stream().allMatch(row -> row.get("endedAt") == null));
        repository.record(config, sample(400, 1L, 0L, 10D));
        assertTrue(repository.alerts(42).stream().allMatch(row -> Long.valueOf(400).equals(row.get("endedAt"))));
        repository.record(config, sample(500, 31L, 0L, 10D));
        assertEquals(5, repository.alerts(42).size());
        assertNull(repository.alerts(42).get(0).get("endedAt"));
    }

    @Test
    public void historyIsBoundedIsolatedAndRetentionKeepsUnresolvedAlerts() {
        for (int i = 1; i <= 205; i++) repository.record(config(), sample(i, 35L, 0L, 10D));
        assertEquals(200, repository.history(42).size());
        assertEquals(205, repository.history(42).get(0).getCapturedAt());
        assertTrue(repository.history(43).isEmpty());
        assertTrue(repository.alerts(43).isEmpty());
        repository.prune(200);
        assertEquals(6, repository.history(42).size());
        assertEquals(1, repository.alerts(42).size());
        repository.record(config(), sample(300, 1L, 0L, 10D));
        repository.prune(301);
        assertTrue(repository.history(42).isEmpty());
        assertTrue(repository.alerts(42).isEmpty());
    }

    @Test
    public void partialTablespaceDataDoesNotFalselyRecoverCapacityAlarm() {
        Map<String, Object> snapshot = snapshot();
        Map<String, Object> unknown = new HashMap<>();
        unknown.put("usagePercent", null);
        snapshot.put("tablespaceFiles", Arrays.asList(Collections.singletonMap("usagePercent", 90D), unknown));
        MonitorSample sample = DatabaseMonitorService.aggregate(snapshot);
        assertNull(sample.getSpacePercent());
        assertNotNull(sample.getError());
        assertEquals(Long.valueOf(2), sample.getActiveCount());
        repository.record(config(), sample);
        assertTrue(repository.alerts(42).stream().anyMatch(row -> "space".equals(row.get("key")) && row.get("endedAt") == null));
    }

    @Test
    public void revokedOrDisabledUserNeverOpensDatabaseAndProducesCollectionFailure() {
        MonitorConfig config = config();
        repository.save(config);
        AtomicInteger opened = new AtomicInteger();
        DatabaseMonitorService service = service(false, opened, new DatabaseSnapshotCollector());
        try { service.sample(config); } finally { service.close(); }
        assertEquals(0, opened.get());
        assertTrue(repository.history(42).get(0).getError().contains("权限已失效"));
        assertEquals("collection", repository.alerts(42).get(0).get("key"));
    }

    @Test
    public void disablingConfigurationDuringCollectionDiscardsInFlightResult() {
        MonitorConfig config = config();
        repository.save(config);
        DatabaseMonitorService service = service(true, new AtomicInteger(), new DatabaseSnapshotCollector() {
            @Override public Map<String, Object> collect() {
                config.setEnabled(false);
                repository.save(config);
                return snapshot();
            }
        });
        try { service.sample(config); } finally { service.close(); }
        assertTrue(repository.history(42).isEmpty());
    }

    @Test
    public void schedulerCollectsWithoutBrowserOrRequestAndDoesNotOverlapSource() throws Exception {
        repository.save(config());
        CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
        AtomicInteger opened = new AtomicInteger();
        DatabaseMonitorService service = service(true, opened, new DatabaseSnapshotCollector() {
            @Override public Map<String, Object> collect() {
                started.countDown();
                try { assertTrue(finish.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { throw new RuntimeException(error); }
                return snapshot();
            }
        });
        try {
            service.tick();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            service.tick();
            assertEquals(1, opened.get());
            finish.countDown();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (repository.history(42).isEmpty() && System.nanoTime() < until) Thread.sleep(10);
            assertEquals(1, repository.history(42).size());
        } finally { finish.countDown(); service.close(); }
    }

    @SuppressWarnings("unchecked")
    private DatabaseMonitorService service(boolean allowed, AtomicInteger opened, DatabaseSnapshotCollector collector) {
        DataSource source = new DataSource();
        source.setId(42L);
        source.setType("DM");
        DataSourceService sources = (DataSourceService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{DataSourceService.class}, (proxy, method, args) -> DataResult.of(source));
        DataSourceAccessBusinessService access = (DataSourceAccessBusinessService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{DataSourceAccessBusinessService.class}, (proxy, method, args) -> {
                    DataSourcePermissionPreview preview = new DataSourcePermissionPreview();
                    preview.setActive(allowed);
                    for (DataSourceAction action : Arrays.asList(DataSourceAction.MONITOR_VIEW, DataSourceAction.DATASOURCE_MANAGE)) {
                        DataSourcePermissionPreview.ActionPermission item = new DataSourcePermissionPreview.ActionPermission();
                        item.setCode(action.name());
                        item.setAllowed(allowed);
                        preview.getActions().add(item);
                    }
                    return preview;
                });
        ConnectionInfoHandler connections = new ConnectionInfoHandler() {
            @Override public ConnectInfo toInfo(Long id, String database, Boolean ref) {
                opened.incrementAndGet();
                ConnectInfo info = new ConnectInfo();
                info.setDbType("DM");
                return info;
            }
        };
        return new DatabaseMonitorService(repository, collector, sources, access, connections);
    }

    private MonitorConfig config() {
        MonitorConfig config = new MonitorConfig();
        config.setDataSourceId(42L);
        config.setUserId(1L);
        config.setEnabled(true);
        config.setRevision(UUID.randomUUID().toString());
        return config;
    }

    private MonitorSample sample(long time, Long active, Long blocked, Double space) {
        MonitorSample sample = new MonitorSample();
        sample.setCapturedAt(time);
        sample.setSessionCount(active);
        sample.setActiveCount(active);
        sample.setBlockedCount(blocked);
        sample.setSpacePercent(space);
        return sample;
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> result = new HashMap<>();
        result.put("capturedAt", System.currentTimeMillis());
        result.put("sessionCount", 5);
        result.put("activeCount", 2);
        result.put("blockedCount", 0);
        result.put("tablespaceFiles", Collections.singletonList(Collections.singletonMap("usagePercent", 10D)));
        result.put("errors", Collections.emptyMap());
        return result;
    }
}
