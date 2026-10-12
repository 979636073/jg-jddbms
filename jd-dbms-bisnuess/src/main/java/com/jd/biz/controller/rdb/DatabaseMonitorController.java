package com.jd.biz.controller.rdb;

import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.spi.model.TableDetails;
import com.jd.spi.sql.Chat2DBContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only monitoring snapshot. Every section reports its own privilege/query failure. */
@ConnectionInfoAspect
@RestController
@RequestMapping("/api/rdb/monitor")
public class DatabaseMonitorController {
    @GetMapping("/snapshot")
    public DataResult<Map<String, Object>> snapshot(@Valid TableBriefQueryRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        String dbType = Chat2DBContext.getConnectInfo().getDbType();
        Connection connection = Chat2DBContext.getConnection();
        snapshot.put("dbType", dbType);
        snapshot.put("capturedAt", new Date().getTime());
        boolean oracle = "ORACLE".equalsIgnoreCase(dbType);
        try {
            snapshot.put("sessionCount", count(connection, oracle
                    ? "SELECT COUNT(*) FROM V$SESSION WHERE TYPE='USER'"
                    : "SELECT COUNT(*) FROM V$SESSIONS"));
            snapshot.put("activeCount", count(connection, oracle
                    ? "SELECT COUNT(*) FROM V$SESSION WHERE TYPE='USER' AND STATUS='ACTIVE'"
                    : "SELECT COUNT(*) FROM V$SESSIONS WHERE STATE='ACTIVE'"));
            snapshot.put("sessions", sessions(connection, oracle));
        } catch (SQLException error) {
            errors.put("sessions", error.getMessage());
        }
        try {
            snapshot.put("blockedCount", count(connection, oracle
                    ? "SELECT COUNT(*) FROM V$SESSION WHERE BLOCKING_SESSION IS NOT NULL"
                    : "SELECT COUNT(*) FROM V$TRXWAIT"));
        } catch (SQLException error) {
            errors.put("locks", error.getMessage());
        }
        try {
            List<Map<String, Object>> spaces = new ArrayList<>();
            // The existing DM/Oracle metadata adapters calculate file usage consistently.
            for (TableDetails file : Chat2DBContext.getMetaData().tableSpacesFile(connection, 1, 1000)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", file.getName());
                row.put("path", file.getPath());
                row.put("usagePercent", percent(file.getUsageRate()));
                row.put("status", file.getStatus());
                spaces.add(row);
            }
            // File-list metadata contains names and paths only. Read usage from the
            // data dictionary so the capacity alert is based on measured values.
            populateFileUsage(connection, spaces);
            snapshot.put("tablespaceFiles", spaces);
        } catch (SQLException | RuntimeException error) {
            errors.put("tablespaces", error.getMessage());
        }
        snapshot.put("errors", errors);
        return DataResult.of(snapshot);
    }

    private long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (ResultSet rows = statement.executeQuery(sql)) {
                return rows.next() ? rows.getLong(1) : 0;
            }
        }
    }

    private List<Map<String, Object>> sessions(Connection connection, boolean oracle) throws SQLException {
        String sql = oracle
                ? "SELECT SID, USERNAME, STATUS, MACHINE FROM V$SESSION WHERE TYPE='USER'"
                : "SELECT SESS_ID, USER_NAME, STATE, CLNT_IP FROM V$SESSIONS";
        List<Map<String, Object>> result = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            statement.setMaxRows(50);
            try (ResultSet rows = statement.executeQuery(sql)) {
                while (rows.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rows.getString(1));
                    row.put("user", rows.getString(2));
                    row.put("state", rows.getString(3));
                    row.put("client", rows.getString(4));
                    result.add(row);
                }
            }
        }
        return result;
    }

    private Double percent(String value) {
        try { return value == null ? null : Double.valueOf(value.replace("%", "").trim()); }
        catch (NumberFormatException error) { return null; }
    }

    private void populateFileUsage(Connection connection, List<Map<String, Object>> files) throws SQLException {
        String sql = "SELECT f.FILE_NAME, f.BYTES, COALESCE(SUM(s.BYTES), 0) "
                + "FROM DBA_DATA_FILES f LEFT JOIN DBA_FREE_SPACE s "
                + "ON s.TABLESPACE_NAME = f.TABLESPACE_NAME AND s.FILE_ID = f.FILE_ID "
                + "GROUP BY f.FILE_NAME, f.BYTES";
        Map<String, Double> usageByPath = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (ResultSet rows = statement.executeQuery(sql)) {
                while (rows.next()) {
                    double total = rows.getDouble(2);
                    double free = rows.getDouble(3);
                    if (total > 0) usageByPath.put(rows.getString(1),
                            Math.round((1 - free / total) * 10000.0) / 100.0);
                }
            }
        }
        for (Map<String, Object> file : files) {
            file.put("usagePercent", usageByPath.get(file.get("path")));
        }
    }
}
