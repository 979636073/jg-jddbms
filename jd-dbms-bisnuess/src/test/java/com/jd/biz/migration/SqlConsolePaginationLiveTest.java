package com.jd.biz.migration;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.common.tools.common.util.I18nUtils;
import com.jd.spi.model.Command;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.sql.SQLExecutor;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.context.support.StaticMessageSource;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;

import static org.junit.Assert.*;

/** Opt-in, read-only DM/Oracle checks of the console's session-backed executor. */
public class SqlConsolePaginationLiveTest {
    @Test public void dmConsole() throws Exception { verify("DM"); }
    @Test public void oracleConsole() throws Exception { verify("ORACLE"); }

    private void verify(String type) throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_PAGINATION_LIVE")));
        MigrationLiveTest helper = new MigrationLiveTest();
        DataSource source;
        if ("DM".equals(type)) {
            source = new DataSource(); source.setType(type); source.setDriverConfig(helper.driver(type));
            source.setUrl(MigrationLiveTest.hostUrl(System.getenv("JDDBMS_META_DB_URL")));
            source.setUserName(System.getenv("JDDBMS_META_DB_USERNAME")); source.setPassword(System.getenv("JDDBMS_META_DB_PASSWORD"));
            java.io.File root = new java.io.File(System.getProperty("user.dir"));
            if (root.getName().equals("jd-dbms-bisnuess")) System.setProperty("user.dir", root.getParent());
        } else if (System.getenv("JDDBMS_CONSOLE_QA_ORACLE_PASSWORD") != null) {
            java.io.File root = new java.io.File(System.getProperty("user.dir"));
            if (root.getName().equals("jd-dbms-bisnuess")) System.setProperty("user.dir", root.getParent());
            DataSource meta = new DataSource(); meta.setType("DM"); meta.setDriverConfig(helper.driver("DM"));
            meta.setUrl(MigrationLiveTest.hostUrl(System.getenv("JDDBMS_META_DB_URL")));
            meta.setUserName(System.getenv("JDDBMS_META_DB_USERNAME")); meta.setPassword(System.getenv("JDDBMS_META_DB_PASSWORD"));
            source = new DataSource(); source.setType(type); source.setDriverConfig(helper.driver(type));
            source.setUserName(System.getenv("JDDBMS_CONSOLE_QA_ORACLE_USERNAME"));
            source.setPassword(System.getenv("JDDBMS_CONSOLE_QA_ORACLE_PASSWORD"));
            try (Connection metaConnection = new MigrationConnections().open(meta);
                 java.sql.Statement statement = metaConnection.createStatement();
                 java.sql.ResultSet rows = statement.executeQuery("SELECT \"URL\" FROM \"DATA_SOURCE\" WHERE \"TYPE\"='ORACLE' AND \"USER_NAME\"='JQORO_260926' ORDER BY \"ID\" DESC")) {
                assertTrue("Existing Oracle QA address required", rows.next());
                source.setUrl(MigrationLiveTest.hostUrl(rows.getString(1)));
            }
        } else source = helper.environment(type);
        Field messages = I18nUtils.class.getDeclaredField("messageSourceStatic");
        messages.setAccessible(true);
        Object previous = messages.get(null);
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.setUseCodeAsDefaultMessage(true);
        messages.set(null, messageSource);
        String sessionId = null;
        try (Connection connection = new MigrationConnections().open(source)) {
            ConnectInfo context = new ConnectInfo();
            context.setDbType(type); context.setConnection(connection); context.setDriverConfig(source.getDriverConfig());
            context.setSchemaName(source.getUserName()); context.setDataSourceId(source.getId()); context.setConsoleId(987654L);
            Chat2DBContext.putContext(context);
            String sql = "SELECT LEVEL AS ID FROM DUAL CONNECT BY LEVEL <= 10001 ORDER BY ID";
            ExecuteResult first = query(sql, 1, 200, null);
            sessionId = first.getSessionId();
            assertNotNull(sessionId);
            assertPage(first, 1, 200, 200, 1, "10001", true);
            assertPage(query(sql, 2, 200, sessionId), 2, 200, 200, 201, "10001", true);
            assertPage(query(sql, 51, 200, sessionId), 51, 200, 1, 10001, "10001", false);
            assertPage(query(sql, 11, 1000, sessionId), 11, 1000, 1, 10001, "10001", false);
            assertPage(query(sql.replace("ORDER BY ID", "ORDER BY ID DESC"), 2, 200, sessionId),
                    2, 200, 200, 9801, "10001", true);
            assertPage(query("SELECT 1 AS ID FROM DUAL WHERE 1=0", 1, 200, sessionId),
                    1, 200, 0, 0, "0", false);
            assertEquals("2", query("WITH Q AS (SELECT 1 AS ID FROM DUAL UNION ALL SELECT 2 FROM DUAL) SELECT ID FROM Q", 1, 200, sessionId).getTotal());
            assertEquals("1", query("SELECT DISTINCT 1 AS ID FROM DUAL CONNECT BY LEVEL <= 10001", 1, 200, sessionId).getTotal());
            assertEquals("2", query("SELECT MOD(LEVEL,2) AS ID, COUNT(*) AS N FROM DUAL CONNECT BY LEVEL <= 10001 GROUP BY MOD(LEVEL,2)", 1, 200, sessionId).getTotal());
            assertEquals("10001", query("SELECT LEVEL AS ID, ROW_NUMBER() OVER (ORDER BY LEVEL) AS RN FROM DUAL CONNECT BY LEVEL <= 10001", 1, 200, sessionId).getTotal());
            assertEquals("10", query("SELECT LEVEL AS ID FROM DUAL CONNECT BY LEVEL <= 10 -- trailing comment", 1, 200, sessionId).getTotal());
            assertEquals("10", query("SELECT ID FROM (SELECT LEVEL AS ID FROM DUAL CONNECT BY LEVEL <= 10001) WHERE ROWNUM <= 10", 1, 200, sessionId).getTotal());
            ExecuteResult duplicateColumns = query("SELECT 1 AS ID, 2 AS ID FROM DUAL", 1, 200, sessionId);
            assertEquals("1", duplicateColumns.getTotal());
            assertEquals("1", duplicateColumns.getDataList().get(0).get(1));
            assertEquals("2", duplicateColumns.getDataList().get(0).get(2));
            assertEquals("1", query("SELECT COUNT(*) AS N FROM DUAL CONNECT BY LEVEL <= 10001", 1, 200, sessionId).getTotal());
            int majorVersion = connection.getMetaData().getDatabaseMajorVersion();
            String limited = "DM".equals(type)
                    ? "SELECT LEVEL AS ID FROM DUAL CONNECT BY LEVEL <= 10001 ORDER BY ID LIMIT 10"
                    : majorVersion >= 12
                    ? "SELECT LEVEL AS ID FROM DUAL CONNECT BY LEVEL <= 10001 ORDER BY ID FETCH FIRST 10 ROWS ONLY"
                    : "SELECT ID FROM (SELECT LEVEL AS ID FROM DUAL CONNECT BY LEVEL <= 10001 ORDER BY ID) WHERE ROWNUM <= 10";
            assertEquals("10", query(limited, 1, 200, sessionId).getTotal());
            System.out.println("SQL CONSOLE " + type + " major=" + majorVersion + ": exact 10001/zero totals, page 2/last page, 1000/page, descending, CTE/union/distinct/group/window/rownum/comment/duplicate columns/aggregate/native limit PASS");
        } finally {
            if (sessionId != null) {
                Command close = new Command(); close.setSessionId(sessionId);
                SQLExecutor.getInstance().delSession(close);
            }
            Chat2DBContext.detachContext();
            messages.set(null, previous);
        }
    }

    private ExecuteResult query(String sql, int page, int size, String sessionId) {
        Command command = new Command();
        command.setScript(sql); command.setPageNo(page); command.setPageSize(size);
        command.setSkipCount(false); command.setQueryTemplate(true); command.setSessionId(sessionId);
        command.setSign(false); command.setIsCommit(false); command.setQueryTimeoutSeconds(30);
        List<ExecuteResult> results = SQLExecutor.getInstance().execute(command);
        assertEquals(1, results.size());
        ExecuteResult result = results.get(0);
        assertTrue(result.getMessage(), result.getSuccess());
        assertEquals(Boolean.TRUE, result.getTotalExact());
        return result;
    }

    private void assertPage(ExecuteResult result, int page, int pageSize, int rows, int first,
                            String total, boolean next) {
        assertEquals(Integer.valueOf(page), result.getPageNo());
        assertEquals(Integer.valueOf(pageSize), result.getPageSize());
        assertEquals(rows, result.getDataList().size());
        if (rows > 0) assertEquals(Integer.toString(first), result.getDataList().get(0).get(1));
        assertEquals(total, result.getTotal()); assertEquals(total, result.getFuzzyTotal());
        assertEquals(next, result.getHasNextPage());
    }
}
