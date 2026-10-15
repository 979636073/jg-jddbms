package com.jd.biz.monitor;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.util.*;

@Repository
public class MonitorRepository {
    private final JdbcTemplate jdbc;

    public MonitorRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Additive upgrade, also runs for existing H2 metadata databases. */
    @PostConstruct
    public void initialize() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            for (String table : Arrays.asList("DB_MONITOR_CONFIG", "DB_MONITOR_SAMPLE", "DB_MONITOR_ALERT")) {
                try (ResultSet tables = connection.getMetaData().getTables(
                        null, connection.getSchema(), table, new String[]{"TABLE"})) {
                    if (tables.next()) continue;
                }
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ClassPathResource("db/monitor/" + table + ".sql"), "UTF-8"));
            }
            return null;
        });
    }

    public MonitorConfig config(long id) {
        return configs(" WHERE DATA_SOURCE_ID = ?", id).stream().findFirst().orElseGet(() -> {
            MonitorConfig config = new MonitorConfig();
            config.setDataSourceId(id);
            return config;
        });
    }

    public List<MonitorConfig> enabled() { return configs(" WHERE ENABLED = 1"); }

    private List<MonitorConfig> configs(String where, Object... args) {
        return jdbc.query("SELECT DATA_SOURCE_ID, ENABLED, ACTIVE_THRESHOLD, SPACE_THRESHOLD, USER_ID, REVISION "
                + "FROM DB_MONITOR_CONFIG" + where, (rs, row) -> {
            MonitorConfig config = new MonitorConfig();
            config.setDataSourceId(rs.getLong(1));
            config.setEnabled(rs.getInt(2) == 1);
            config.setActiveThreshold(rs.getInt(3));
            config.setSpaceThreshold(rs.getInt(4));
            config.setUserId(rs.getLong(5));
            config.setRevision(rs.getString(6));
            return config;
        }, args);
    }

    @Transactional
    public void save(MonitorConfig config) {
        int updated = jdbc.update("UPDATE DB_MONITOR_CONFIG SET ENABLED=?, ACTIVE_THRESHOLD=?, SPACE_THRESHOLD=?, "
                        + "USER_ID=?, REVISION=? WHERE DATA_SOURCE_ID=?", config.isEnabled() ? 1 : 0,
                config.getActiveThreshold(), config.getSpaceThreshold(), config.getUserId(),
                config.getRevision(), config.getDataSourceId());
        if (updated == 0) jdbc.update("INSERT INTO DB_MONITOR_CONFIG "
                        + "(DATA_SOURCE_ID, ENABLED, ACTIVE_THRESHOLD, SPACE_THRESHOLD, USER_ID, REVISION) VALUES(?,?,?,?,?,?)",
                config.getDataSourceId(), config.isEnabled() ? 1 : 0, config.getActiveThreshold(),
                config.getSpaceThreshold(), config.getUserId(), config.getRevision());
    }

    public List<MonitorSample> history(long id) {
        return jdbc.query(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement("SELECT CAPTURED_AT, SESSION_COUNT, "
                    + "ACTIVE_COUNT, BLOCKED_COUNT, SPACE_PERCENT, ERROR_TEXT FROM DB_MONITOR_SAMPLE "
                    + "WHERE DATA_SOURCE_ID=? ORDER BY CAPTURED_AT DESC");
            statement.setLong(1, id);
            statement.setMaxRows(200);
            return statement;
        }, (rs, row) -> {
            MonitorSample sample = new MonitorSample();
            sample.setCapturedAt(rs.getLong(1));
            sample.setSessionCount(nullableLong(rs, 2));
            sample.setActiveCount(nullableLong(rs, 3));
            sample.setBlockedCount(nullableLong(rs, 4));
            sample.setSpacePercent(rs.getObject(5) == null ? null : rs.getDouble(5));
            sample.setError(rs.getString(6));
            return sample;
        });
    }

    private Long nullableLong(ResultSet rs, int column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public List<Map<String, Object>> alerts(long id) {
        return jdbc.query(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement("SELECT ALERT_KEY, STARTED_AT, "
                    + "LAST_SEEN, ENDED_AT, MESSAGE FROM DB_MONITOR_ALERT WHERE DATA_SOURCE_ID=? "
                    + "ORDER BY CASE WHEN ENDED_AT IS NULL THEN 0 ELSE 1 END, STARTED_AT DESC");
            statement.setLong(1, id);
            statement.setMaxRows(100);
            return statement;
        }, (rs, row) -> {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("key", rs.getString(1));
            alert.put("startedAt", rs.getLong(2));
            alert.put("lastSeen", rs.getLong(3));
            alert.put("endedAt", nullableLong(rs, 4));
            alert.put("message", rs.getString(5));
            return alert;
        });
    }

    @Transactional
    public void record(MonitorConfig config, MonitorSample sample) {
        long id = config.getDataSourceId(), now = sample.getCapturedAt();
        jdbc.update("INSERT INTO DB_MONITOR_SAMPLE (DATA_SOURCE_ID,CAPTURED_AT,SESSION_COUNT,ACTIVE_COUNT,"
                        + "BLOCKED_COUNT,SPACE_PERCENT,ERROR_TEXT) VALUES(?,?,?,?,?,?,?)", id, now,
                sample.getSessionCount(), sample.getActiveCount(), sample.getBlockedCount(),
                sample.getSpacePercent(), sample.getError());
        observe(id, "collection", sample.getError() != null, sample.getError(), now);
        if (sample.getActiveCount() != null) observe(id, "active",
                sample.getActiveCount() >= config.getActiveThreshold(), "活动会话达到 " + sample.getActiveCount()
                        + "，阈值 " + config.getActiveThreshold(), now);
        if (sample.getBlockedCount() != null) observe(id, "blocked", sample.getBlockedCount() > 0,
                "存在 " + sample.getBlockedCount() + " 个阻塞等待", now);
        Double space = sample.getSpacePercent() != null ? sample.getSpacePercent() : sample.getObservedSpacePercent();
        // A known full file can trigger an alarm even when other files are unreadable;
        // only a complete observation may recover the capacity alarm.
        if (space != null && (sample.getSpacePercent() != null || space >= config.getSpaceThreshold())) {
            observe(id, "space", space >= config.getSpaceThreshold(), "表空间文件使用率 "
                    + space + "%，阈值 " + config.getSpaceThreshold() + "%", now);
        }
    }

    private void observe(long id, String key, boolean triggered, String message, long now) {
        if (!triggered) {
            jdbc.update("UPDATE DB_MONITOR_ALERT SET ENDED_AT=? WHERE DATA_SOURCE_ID=? AND ALERT_KEY=? AND ENDED_AT IS NULL",
                    now, id, key);
            return;
        }
        int updated = jdbc.update("UPDATE DB_MONITOR_ALERT SET LAST_SEEN=?, MESSAGE=? "
                + "WHERE DATA_SOURCE_ID=? AND ALERT_KEY=? AND ENDED_AT IS NULL", now, message, id, key);
        if (updated == 0) jdbc.update("INSERT INTO DB_MONITOR_ALERT "
                        + "(ID,DATA_SOURCE_ID,ALERT_KEY,STARTED_AT,LAST_SEEN,MESSAGE) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(), id, key, now, now, message);
    }

    @Transactional
    public void prune(long cutoff) {
        jdbc.update("DELETE FROM DB_MONITOR_SAMPLE WHERE CAPTURED_AT < ?", cutoff);
        jdbc.update("DELETE FROM DB_MONITOR_ALERT WHERE ENDED_AT < ?", cutoff);
    }
}
