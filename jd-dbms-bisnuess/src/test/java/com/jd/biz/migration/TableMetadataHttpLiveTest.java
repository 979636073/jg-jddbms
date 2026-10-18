package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.Assume;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** Opt-in HTTP acceptance; only uniquely named tables in dedicated QA schemas are changed. */
public class TableMetadataHttpLiveTest {
    @Test public void dmEditingMetadataAndPagination() throws Exception { run("DM", source("DM", 3L), schema("DM", "JQDMO_260926")); }
    @Test public void oracleEditingMetadataAndPagination() throws Exception { run("ORACLE", source("ORACLE", 2L), schema("ORACLE", "JQORO_260926")); }

    private long source(String type, long fallback) { return Long.parseLong(System.getenv().getOrDefault("JDDBMS_QA_" + type + "_DATASOURCE_ID", String.valueOf(fallback))); }
    private String schema(String type, String fallback) { return System.getenv().getOrDefault("JDDBMS_QA_" + type + "_SCHEMA", fallback); }

    @Test public void dmIdentityDoesNotLeakAcrossSchemasWithTheSameTableName() throws Exception {
        authenticate();
        String table = "JQA_META_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        List<String> created = new ArrayList<>();
        try {
            for (String schema : Arrays.asList(schema("DM", "JQDMO_260926"), System.getenv().getOrDefault("JDDBMS_QA_DM_SECOND_SCHEMA", "JQDM4_260926"))) {
                String qualified = "\"" + schema + "\".\"" + table + "\"";
                execute("DM", source("DM", 3L), schema, null, "CREATE TABLE " + qualified + " (ID INT" +
                        (schema.equals(schema("DM", "JQDMO_260926")) ? " IDENTITY(1,1)" : "") + ")", 1, 100, false);
                created.add(schema);
            }
            for (String schema : created) {
                JSONObject response = post("/api/rdb/dml/execute", params("DM", source("DM", 3L), schema, table,
                        "SELECT * FROM \"" + schema + "\".\"" + table + "\"", 1, 100, false));
                assertTrue(response.getString("errorMessage"), response.getBooleanValue("success"));
                JSONObject result = response.getJSONArray("data").getJSONObject(0);
                assertEquals(schema.equals(schema("DM", "JQDMO_260926")) ? 1 : 0, header(result, "ID").getIntValue("autoIncrement"));
                assertEquals("0", result.getString("fuzzyTotal")); assertTrue(result.getBooleanValue("totalExact"));
            }
            System.out.println("METADATA HTTP DM PASS: identity isolated across same-name tables in two schemas, exact empty total");
        } finally {
            for (String schema : created) execute("DM", source("DM", 3L), schema, null,
                    "DROP TABLE \"" + schema + "\".\"" + table + "\"", 1, 100, false);
            if (!created.isEmpty()) System.out.println("METADATA HTTP cleaned owned DM identity tables " + table);
        }
    }

    private String token;
    private void run(String type, long source, String schema) throws Exception {
        authenticate();
        String table = "JQA_META_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String qualified = "\"" + schema + "\".\"" + table + "\"";
        boolean created = false;
        try {
            StringBuilder columns = new StringBuilder("ID NUMBER(12) NOT NULL, SEQ NUMBER(12) NOT NULL, AMOUNT NUMBER(18,4) DEFAULT 12.5, ")
                    .append("NOTE ").append(type.equals("DM") ? "VARCHAR(128)" : "VARCHAR2(128)")
                    .append(", PAYLOAD BLOB, DETAIL CLOB, TS TIMESTAMP, PRIMARY KEY (ID, SEQ)");
            for (int i = 1; i <= 70; i++) columns.append(", C").append(i).append(" NUMBER(12)");
            execute(type, source, schema, null, "CREATE TABLE " + qualified + " (" + columns + ")", 1, 100, false);
            created = true;
            execute(type, source, schema, null, "COMMENT ON COLUMN " + qualified + ".NOTE IS 'QA 中文注释'", 1, 100, false);
            execute(type, source, schema, null, "INSERT INTO " + qualified + " (ID,SEQ,PAYLOAD,DETAIL) VALUES (1,1,EMPTY_BLOB(),TO_CLOB('QA中文'))", 1, 100, false);
            execute(type, source, schema, null, "INSERT INTO " + qualified + " (ID,SEQ) SELECT LEVEL+1,1 FROM DUAL CONNECT BY LEVEL<=10000", 1, 100, false);
            String select = "SELECT t.*" + (type.equals("ORACLE") ? ", t.ROWID AS \"ROWID\"" : "") + " FROM " + qualified + " t ORDER BY ID";
            JSONObject first = execute(type, source, schema, table, select, 1, 100, false);
            assertEditingHeaders(first);
            for (int i = 0; i < 3; i++) assertEditingHeaders(execute(type, source, schema, table, select, 1, 100, false));
            JSONObject second = execute(type, source, schema, table, select, 2, 100, false);
            assertEquals("101", second.getJSONArray("dataList").getJSONArray(0).getString(index(second, "ID")));
            JSONObject last = execute(type, source, schema, table, select, 101, 100, false);
            assertEquals(1, last.getJSONArray("dataList").size());
            assertEquals("10001", last.getJSONArray("dataList").getJSONArray(0).getString(index(last, "ID")));
            assertEquals(1000, execute(type, source, schema, table, select, 1, 1000, false).getJSONArray("dataList").size());
            JSONObject sorted = execute(type, source, schema, table, select.replace("ORDER BY ID", "ORDER BY ID DESC"), 1, 100, false);
            assertEquals("10001", sorted.getJSONArray("dataList").getJSONArray(0).getString(index(sorted, "ID")));
            assertEditingHeaders(execute(type, source, schema, table, select, 1, 100, true));

            // A console DDL batch can succeed partially: the first ALTER must still invalidate the cache.
            Map<String, Object> batch = params(type, source, schema, null,
                    "ALTER TABLE " + qualified + " ADD NEW_COL NUMBER(12) DEFAULT 9 NOT NULL;\nALTER TABLE " + qualified + " ADD ID NUMBER(12)", 1, 100, false);
            JSONObject partial = post("/api/rdb/dml/execute", batch);
            assertFalse(partial.getBooleanValue("success"));
            JSONObject changed = execute(type, source, schema, table, select, 1, 100, false);
            assertEquals(Integer.valueOf(1), header(changed, "NEW_COL").getInteger("nullable"));
            assertTrue(header(changed, "NEW_COL").getString("defaultValue").trim().startsWith("9"));

            // UPDATE exercises the retained primary key editing metadata; no existing business table is touched.
            execute(type, source, schema, null, "UPDATE " + qualified + " SET NOTE='edited' WHERE ID=1 AND SEQ=1", 1, 100, false);
            JSONObject edited = execute(type, source, schema, table, select, 1, 100, false);
            assertEquals("edited", edited.getJSONArray("dataList").getJSONArray(0).getString(index(edited, "NOTE")));
            System.out.println("METADATA HTTP " + type + " PASS: composite PK, default/null/precision/comment/LOB, exact 10001 rows, paging/last/size/sort, manual refresh, partial-DDL invalidation and UPDATE");
        } finally {
            if (created) {
                if (!table.matches("JQA_META_[A-F0-9]{8}")) throw new IllegalStateException("Not an owned fixture");
                execute(type, source, schema, null, "DROP TABLE " + qualified + (type.equals("ORACLE") ? " PURGE" : ""), 1, 100, false);
                System.out.println("METADATA HTTP cleaned owned " + type + " table " + table);
            }
        }
    }

    private void assertEditingHeaders(JSONObject result) {
        assertTrue(header(result, "ID").getBooleanValue("primaryKey"));
        assertTrue(header(result, "SEQ").getBooleanValue("primaryKey"));
        assertFalse(header(result, "AMOUNT").getBooleanValue("primaryKey"));
        assertEquals(Integer.valueOf(1), header(result, "ID").getInteger("nullable"));
        assertEquals(Integer.valueOf(0), header(result, "AMOUNT").getInteger("nullable"));
        assertEquals(Integer.valueOf(18), header(result, "AMOUNT").getInteger("columnSize"));
        assertEquals(Integer.valueOf(4), header(result, "AMOUNT").getInteger("decimalDigits"));
        assertTrue(header(result, "AMOUNT").getString("defaultValue").trim().startsWith("12.5"));
        assertEquals("QA 中文注释", header(result, "NOTE").getString("comment"));
        assertNotNull(header(result, "PAYLOAD").getString("dataType"));
        assertNotNull(header(result, "DETAIL").getString("dataType"));
    }
    private void authenticate() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_METADATA_HTTP_LIVE")));
        String curl = Files.readString(Paths.get(System.getenv("JDDBMS_QA_CURL")), StandardCharsets.UTF_8);
        Matcher bearer = Pattern.compile("Bearer\\s+([A-Za-z0-9_.-]+)").matcher(curl);
        assertTrue("Authenticated local QA request is required", bearer.find()); token = bearer.group(1);
    }
    private JSONObject header(JSONObject result, String name) {
        return result.getJSONArray("headerList").getJSONObject(index(result, name));
    }
    private int index(JSONObject result, String name) {
        JSONArray headers = result.getJSONArray("headerList");
        for (int i = 0; i < headers.size(); i++) if (name.equals(headers.getJSONObject(i).getString("name"))) return i;
        throw new AssertionError("Missing metadata column " + name);
    }
    private JSONObject execute(String type, long source, String schema, String table, String sql, int page, int size, boolean refresh) throws Exception {
        JSONObject response = post("/api/rdb/dml/execute", params(type, source, schema, table, sql, page, size, refresh));
        assertTrue(response.getString("errorMessage"), response.getBooleanValue("success"));
        JSONObject result = response.getJSONArray("data").getJSONObject(0);
        assertTrue(result.getString("message"), result.getBooleanValue("success"));
        if (table != null) {
            assertTrue(result.getBooleanValue("totalExact")); assertEquals("10001", result.getString("fuzzyTotal"));
            assertEquals(page, result.getIntValue("pageNo")); assertEquals(size, result.getIntValue("pageSize"));
        }
        return result;
    }
    private Map<String, Object> params(String type, long source, String schema, String table, String sql, int page, int size, boolean refresh) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dataSourceId", source); body.put("databaseType", type); body.put("type", type);
        body.put("databaseName", ""); body.put("schemaName", schema); body.put("tableName", table); body.put("sql", sql);
        body.put("pageNo", page); body.put("pageSize", size); body.put("skipCount", false);
        body.put("isColumn", true); body.put("isLog", false); body.put("refresh", refresh); body.put("confirmDangerousSql", true);
        return body;
    }
    private JSONObject post(String path, Map<String, Object> body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(System.getenv().getOrDefault("JDDBMS_QA_HTTP_URL", "http://127.0.0.1:38181") + path).openConnection();
        connection.setRequestMethod("POST"); connection.setConnectTimeout(10000); connection.setReadTimeout(60000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8"); connection.setDoOutput(true);
        try {
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(JSON.toJSONBytes(body)); }
            assertEquals("Local QA HTTP status", 200, connection.getResponseCode());
            try (java.io.InputStream input = connection.getInputStream()) { return JSON.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8)); }
        } finally { connection.disconnect(); }
    }
}
