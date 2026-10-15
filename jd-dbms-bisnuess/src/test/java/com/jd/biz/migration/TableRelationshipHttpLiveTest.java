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
import java.util.regex.*;
import static org.junit.Assert.*;

/** Only uniquely named objects in dedicated local QA schemas are changed. */
public class TableRelationshipHttpLiveTest {
    private String token;
    @Test public void dmRelationsRemainExactAfterCachingAndDDL() throws Exception { run("DM", 3, "JQDMO_260926", "JQDM4_260926"); }
    @Test public void oracleRelationsRemainExactAfterCachingAndDDL() throws Exception { run("ORACLE", 2, "JQORO_260926", "JQORO_260926"); }
    private void run(String type, int source, String schema, String childSchema) throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_RELATION_HTTP_LIVE")));
        String curl = Files.readString(Paths.get(System.getenv("JDDBMS_QA_CURL")), StandardCharsets.UTF_8);
        Matcher bearer = Pattern.compile("Bearer\\s+([A-Za-z0-9_.-]+)").matcher(curl);
        assertTrue(bearer.find()); token = bearer.group(1);
        String prefix = "JQA_REL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String parent = prefix + "P", child = prefix + "C", view = prefix + "V";
        String p = "\"" + schema + "\".\"" + parent + "\"", c = "\"" + childSchema + "\".\"" + child + "\"";
        String v = "\"" + schema + "\".\"" + view + "\"";
        List<String> created = new ArrayList<>();
        try {
            ddl(type, source, schema, "CREATE TABLE " + p + " (ID1 INT, ID2 INT, REF_ID INT, PRIMARY KEY(ID1, ID2), UNIQUE(ID1))");
            created.add("TABLE " + p);
            assertEquals(0, relations(source, schema, parent, false).size());
            assertEquals(0, relations(source, schema, parent, false).size());
            ddl(type, source, childSchema, "CREATE TABLE " + c + " (ID INT PRIMARY KEY, A INT, B INT, C INT, CONSTRAINT " + prefix + "F1 FOREIGN KEY(A,B) REFERENCES " + p + "(ID1,ID2), CONSTRAINT " + prefix + "F2 FOREIGN KEY(C) REFERENCES " + p + "(ID1))");
            created.add("TABLE " + c);
            JSONArray children = relations(source, schema, parent, false);
            assertEquals(2, children.size());
            JSONObject composite = children.stream().map(value -> (JSONObject)value).filter(value -> (prefix + "F1").equals(value.getString("constraintName"))).findFirst().orElseThrow();
            assertEquals("ID1, ID2", composite.getString("column")); assertEquals("A, B", composite.getString("foreignColumnName"));
            assertEquals(childSchema, composite.getString("schemaName"));
            assertEquals(2, relations(source, schema, parent, false).size());
            ddl(type, source, schema, "ALTER TABLE " + p + " ADD CONSTRAINT " + prefix + "SELF FOREIGN KEY(REF_ID) REFERENCES " + p + "(ID1)");
            assertEquals(3, relations(source, schema, parent, false).size());
            assertEquals(3, relations(source, schema, parent, true).size());
            ddl(type, source, schema, "CREATE VIEW " + v + " AS SELECT ID1 FROM " + p); created.add("VIEW " + v);
            assertEquals(1, viewRelations(source, schema, parent).size());
            assertEquals(1, viewRelations(source, schema, parent).size());
            ddl(type, source, schema, "DROP VIEW " + v); created.remove("VIEW " + v);
            assertEquals(0, viewRelations(source, schema, parent).size());
            ddl(type, source, childSchema, "DROP TABLE " + c); created.remove("TABLE " + c);
            assertEquals(1, relations(source, schema, parent, false).size());
            ddl(type, source, schema, "ALTER TABLE " + p + " DROP CONSTRAINT " + prefix + "SELF");
            assertEquals(0, relations(source, schema, parent, false).size());
            System.out.println("RELATION HTTP " + type + " PASS: empty cache, composite/two FKs, cross-schema, self-reference, manual refresh, CREATE/ALTER/DROP invalidation, view cache");
        } finally {
            Collections.reverse(created);
            for (String object : created) ddl(type, source, schema, "DROP " + object);
            System.out.println("RELATION HTTP cleaned owned " + type + " fixtures " + prefix);
        }
    }
    private JSONArray relations(int source, String schema, String table, boolean refresh) throws Exception {
        return children(http("/api/rdb/table/queryReferenced?dataSourceId=" + source + "&schemaName=" + schema + "&tableName=" + table + "&isRefreshCache=" + refresh, null));
    }
    private JSONArray viewRelations(int source, String schema, String table) throws Exception {
        return children(http("/api/rdb/table/queryViewReferenced?dataSourceId=" + source + "&schemaName=" + schema + "&tableName=" + table + "&check=0", null));
    }
    private JSONArray children(JSONObject result) {
        assertTrue(result.getString("errorMessage"), result.getBooleanValue("success"));
        return result.getJSONObject("data") == null ? new JSONArray() : result.getJSONObject("data").getJSONArray("children");
    }
    private void ddl(String type, int source, String schema, String sql) throws Exception {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("dataSourceId", source); body.put("databaseType", type); body.put("type", type);
        body.put("databaseName", ""); body.put("schemaName", schema); body.put("sql", sql);
        body.put("pageNo", 1); body.put("pageSize", 100); body.put("isColumn", false); body.put("isLog", false); body.put("confirmDangerousSql", true);
        JSONObject result = http("/api/rdb/dml/execute", body);
        assertTrue(result.getString("errorMessage"), result.getBooleanValue("success"));
    }
    private JSONObject http(String path, Map<String,Object> body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:38181" + path).openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(60000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(JSON.toJSONBytes(body)); }
        }
        try {
            assertEquals(200, connection.getResponseCode());
            try (java.io.InputStream input = connection.getInputStream()) { return JSON.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8)); }
        } finally { connection.disconnect(); }
    }
}
