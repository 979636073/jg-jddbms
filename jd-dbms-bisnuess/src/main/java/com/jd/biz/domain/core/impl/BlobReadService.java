package com.jd.biz.domain.core.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLAllColumnExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.jd.biz.controller.rdb.request.GetBlobRequest;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.util.JdbcUtils;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BlobReadService {
    public static final int PREVIEW_LIMIT = 8 * 1024 * 1024;
    private final Map<String, Reading> readings = new ConcurrentHashMap<>();

    @Data
    public static class Preview {
        private String base64;
        private String prefix;
        private boolean nullValue;
        private boolean tooLarge;
        private long byteLength = -1;
        private int previewLimit = PREVIEW_LIMIT;
    }

    public Preview preview(GetBlobRequest request) throws SQLException, IOException {
        Preview preview = new Preview();
        withBlob(request, (input, length, reading) -> {
            preview.setByteLength(length);
            if (input == null) { preview.setNullValue(true); return; }
            if (length > PREVIEW_LIMIT) {
                byte[] prefix = new byte[24];
                int count = input.read(prefix);
                reading.checkCancelled();
                if (count > 0) preview.setPrefix(Base64.getEncoder().encodeToString(Arrays.copyOf(prefix, count)));
                preview.setTooLarge(true); return;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer, 0, Math.min(buffer.length, PREVIEW_LIMIT + 1 - output.size()))) != -1) {
                reading.checkCancelled();
                if (output.size() == 0) preview.setPrefix(Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, Math.min(count, 24))));
                output.write(buffer, 0, count);
                if (output.size() > PREVIEW_LIMIT) { preview.setTooLarge(true); return; }
            }
            preview.setBase64(Base64.getEncoder().encodeToString(output.toByteArray()));
        });
        return preview;
    }

    public void download(GetBlobRequest request, javax.servlet.http.HttpServletResponse response) throws SQLException, IOException {
        withBlob(request, (input, length, reading) -> {
            if (input == null) { response.setStatus(204); return; }
            response.setContentType("application/octet-stream");
            String name = (StrUtil.blankToDefault(request.getTableName(), "query") + "_" + request.getBlobColumn())
                    .replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_") + ".bin";
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20"));
            response.setHeader("Cache-Control", "no-store");
            if (length >= 0) response.setContentLengthLong(length);
            byte[] buffer = new byte[8192];
            int count;
            OutputStream output = response.getOutputStream();
            while ((count = input.read(buffer)) != -1) {
                reading.checkCancelled();
                output.write(buffer, 0, count);
            }
            reading.checkCancelled();
            output.flush();
        });
    }

    public boolean cancel(GetBlobRequest request) throws SQLException {
        String key = key(request);
        Reading reading = readings.get(key);
        if (reading == null) return false;
        reading.cancelled = true;
        try {
            if (reading.statement != null) reading.statement.cancel();
        } finally {
            // A proxy may keep the response socket open after the browser aborts.
            // Release the dedicated database connection even while socket writes are blocked.
            try { if (reading.connection != null) reading.connection.close(); }
            finally { readings.remove(key, reading); }
        }
        return true;
    }

    private String key(GetBlobRequest request) {
        return Chat2DBContext.getConnectInfo().getLoginUser() + ":" + request.getDataSourceId() + ":" + request.getReadId();
    }

    private void withBlob(GetBlobRequest request, BlobConsumer consumer) throws SQLException, IOException {
        if (StrUtil.isBlank(request.getReadId())) request.setReadId(UUID.randomUUID().toString());
        String key = key(request);
        Reading reading = new Reading();
        if (readings.putIfAbsent(key, reading) != null) throw new BusinessException("该文件正在读取，请稍后重试");
        try {
            Connection connection = Chat2DBContext.getConnection();
            reading.connection = connection;
            reading.checkCancelled();
            Lookup lookup = lookup(request, connection);
            reading.checkCancelled();
            try (PreparedStatement statement = connection.prepareStatement(lookup.sql)) {
                reading.statement = statement;
                statement.setQueryTimeout(60);
                statement.setMaxRows(1);
                for (int i = 0; i < lookup.values.size(); i++) {
                    String value = lookup.values.get(i);
                    if (lookup.rowId && "DM".equalsIgnoreCase(Chat2DBContext.getConnectInfo().getDbType())) {
                        try { statement.setLong(i + 1, Long.parseLong(value)); continue; }
                        catch (NumberFormatException ignored) { /* Other databases and encoded ROWIDs use strings. */ }
                    }
                    statement.setString(i + 1, value);
                }
                reading.checkCancelled();
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new BusinessException("该行已不存在，请刷新查询结果");
                    Blob blob = result.getMetaData().getColumnType(1) == Types.BLOB ? result.getBlob(1) : null;
                    try (InputStream input = blob == null ? result.getBinaryStream(1) : blob.getBinaryStream()) {
                        consumer.accept(input, blob == null ? -1 : blob.length(), reading);
                    } finally {
                        if (blob != null) blob.free();
                    }
                }
            }
        } finally {
            readings.remove(key, reading);
        }
    }

    // Never rerun a result by its row number: ordering and membership may have changed.
    Lookup lookup(GetBlobRequest request, Connection connection) throws SQLException {
        if (StrUtil.isNotBlank(request.getRowId()) && StrUtil.isNotBlank(request.getTableName()) && StrUtil.isNotBlank(request.getSchemaName())) {
            return new Lookup("SELECT " + quote(request.getBlobColumn()) + " FROM " + quote(request.getSchemaName()) + "." +
                    quote(request.getTableName()) + " WHERE ROWID = ?", Collections.singletonList(request.getRowId()), true);
        }
        String hint = "当前结果缺少稳定的 BLOB 定位信息，请查询原表并包含完整主键，或同时查询 ROWID";
        if (request.getResultColumns() == null || request.getResultValues() == null ||
                request.getResultColumns().size() != request.getResultValues().size() ||
                new HashSet<>(request.getResultColumns()).size() != request.getResultColumns().size() ||
                StrUtil.isBlank(request.getSql())) throw new BusinessException(hint);
        SQLSelectQuery query;
        try {
            query = ((SQLSelectStatement) SQLUtils.parseSingleStatement(request.getSql(),
                    JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType()))).getSelect().getQuery();
        } catch (RuntimeException invalid) { throw new BusinessException(hint); }
        if (!(query instanceof SQLSelectQueryBlock)) throw new BusinessException(hint);
        SQLSelectQueryBlock block = (SQLSelectQueryBlock) query;
        if (!(block.getFrom() instanceof SQLExprTableSource) || block.isDistinct() || block.getGroupBy() != null) throw new BusinessException(hint);
        SQLExpr source = ((SQLExprTableSource) block.getFrom()).getExpr();
        String schema = request.getSchemaName(), table;
        if (source instanceof SQLIdentifierExpr) table = identifier(((SQLIdentifierExpr) source).getName());
        else if (source instanceof SQLPropertyExpr && ((SQLPropertyExpr) source).getOwner() instanceof SQLIdentifierExpr) {
            schema = identifier(((SQLPropertyExpr) source).getOwnerName()); table = identifier(((SQLPropertyExpr) source).getName());
        } else throw new BusinessException(hint);
        if (StrUtil.isBlank(schema)) schema = connection.getSchema();
        if (StrUtil.isBlank(schema)) throw new BusinessException(hint);
        Map<String, String> columns = new LinkedHashMap<>();
        for (SQLSelectItem item : block.getSelectList()) {
            SQLExpr expr = item.getExpr();
            if (expr instanceof SQLAllColumnExpr || expr instanceof SQLPropertyExpr && "*".equals(((SQLPropertyExpr) expr).getName())) {
                for (String name : request.getResultColumns()) columns.put(name, name);
            } else if (expr instanceof SQLIdentifierExpr || expr instanceof SQLPropertyExpr) {
                String name = identifier(expr instanceof SQLIdentifierExpr ? ((SQLIdentifierExpr) expr).getName() : ((SQLPropertyExpr) expr).getName());
                columns.put(item.getAlias() == null ? name : identifier(item.getAlias()), name);
            }
        }
        String column = columns.get(request.getBlobColumn());
        if (column == null) throw new BusinessException(hint);
        List<String> predicates = new ArrayList<>(), values = new ArrayList<>();
        // An explicitly selected ROWID is stable even for tables without primary keys.
        String rowIdLabel = columns.entrySet().stream().filter(x -> "ROWID".equals(x.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
        if (rowIdLabel != null) addKey("ROWID", rowIdLabel, request, predicates, values, hint);
        else try (ResultSet keys = connection.getMetaData().getPrimaryKeys(null, schema, table)) {
            while (keys.next()) {
                String primary = keys.getString("COLUMN_NAME");
                String label = columns.entrySet().stream().filter(x -> primary.equals(x.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
                addKey(primary, label, request, predicates, values, hint);
            }
        }
        if (predicates.isEmpty()) throw new BusinessException(hint);
        return new Lookup("SELECT " + quote(column) + " FROM " + quote(schema) + "." + quote(table) + " WHERE " + String.join(" AND ", predicates), values, rowIdLabel != null);
    }

    private void addKey(String column, String label, GetBlobRequest request, List<String> predicates, List<String> values, String hint) {
        int index = label == null ? -1 : request.getResultColumns().indexOf(label);
        if (index < 0 || request.getResultValues().get(index) == null) throw new BusinessException(hint);
        predicates.add(quote(column) + " = ?"); values.add(request.getResultValues().get(index));
    }

    private String identifier(String name) {
        return name.startsWith("\"") ? name.substring(1, name.length() - 1).replace("\"\"", "\"") : name.toUpperCase(Locale.ROOT);
    }

    private String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }

    static class Lookup {
        final String sql; final List<String> values; final boolean rowId;
        Lookup(String sql, List<String> values, boolean rowId) { this.sql = sql; this.values = values; this.rowId = rowId; }
    }
    private static class Reading {
        volatile boolean cancelled; volatile PreparedStatement statement; volatile Connection connection;
        void checkCancelled() { if (cancelled) throw new BusinessException("BLOB 读取已取消"); }
    }
    @FunctionalInterface
    private interface BlobConsumer { void accept(InputStream input, long length, Reading reading) throws IOException; }
}
