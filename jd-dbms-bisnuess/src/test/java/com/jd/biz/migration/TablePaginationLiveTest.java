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

import java.io.File;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;

import static org.junit.Assert.*;

/** Read-only real-database checks through the production SQL executor, not an HTTP/browser test. */
public class TablePaginationLiveTest {
    @Test public void dmPagination() throws Exception { verify("DM"); }
    @Test public void oraclePagination() throws Exception { verify("ORACLE"); }

    private void verify(String type) throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_PAGINATION_LIVE")));
        File root = new File(System.getProperty("user.dir"));
        if (root.getName().equals("jd-dbms-bisnuess")) root = root.getParentFile();
        System.setProperty("user.dir", root.getAbsolutePath());
        MigrationLiveTest helper = new MigrationLiveTest();
        DataSource source;
        if ("ORACLE".equals(type)) source = helper.environment(type);
        else {
            source = new DataSource(); source.setType(type); source.setDriverConfig(helper.driver(type));
            source.setUrl(MigrationLiveTest.hostUrl(System.getenv("JDDBMS_META_DB_URL")));
            source.setUserName(System.getenv("JDDBMS_META_DB_USERNAME")); source.setPassword(System.getenv("JDDBMS_META_DB_PASSWORD"));
        }
        Field messages = I18nUtils.class.getDeclaredField("messageSourceStatic");
        messages.setAccessible(true);
        Object previousMessages = messages.get(null);
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.setUseCodeAsDefaultMessage(true);
        messages.set(null, messageSource);
        try (Connection connection = new MigrationConnections().open(source)) {
            ConnectInfo context = new ConnectInfo();
            context.setDbType(type); context.setConnection(connection); context.setDriverConfig(source.getDriverConfig());
            Chat2DBContext.putContext(context);
            String sql = "SELECT LEVEL AS ID FROM DUAL CONNECT BY LEVEL <= 10001";
            assertPage(sql, 1, true, true, 100, 1, "101", false);
            assertPage(sql, 2, true, true, 100, 101, "201", false);
            assertPage(sql, 101, true, true, 1, 10001, "10001", true);
            assertPage(sql, 2, false, true, 100, 101, "10001", true);
            assertPage(sql, 1, true, false, 100, 10001, "101", false);
            assertPage(sql, 102, true, true, 0, 0, "10001", true);
            ExecuteResult empty = query("SELECT 1 AS ID FROM DUAL WHERE 1=0", 1, true, true);
            assertTrue(empty.getDataList().isEmpty()); assertFalse(empty.getHasNextPage());
        } finally {
            Chat2DBContext.detachContext();
            messages.set(null, previousMessages);
        }
    }

    private void assertPage(String sql, int page, boolean skipCount, boolean asc, int size,
                            int firstId, String total, boolean exact) {
        ExecuteResult result = query(sql, page, skipCount, asc);
        assertEquals(size, result.getDataList().size());
        for (int i = 0; i < size; i++) {
            assertEquals(Integer.toString(firstId + (asc ? i : -i)), result.getDataList().get(i).get(1));
        }
        assertEquals(Integer.valueOf(page), result.getPageNo());
        assertEquals(total, result.getFuzzyTotal());
        assertEquals(exact, result.getTotalExact());
        assertEquals(page < 101, result.getHasNextPage());
    }

    private ExecuteResult query(String sql, int page, boolean skipCount, boolean asc) {
        Command command = new Command();
        command.setScript(sql); command.setPageNo(page); command.setPageSize(100);
        command.setSkipCount(skipCount); command.setOrderByColumn("ID"); command.setIsAsc(asc);
        List<ExecuteResult> results = SQLExecutor.getInstance().execute(command);
        assertEquals(1, results.size());
        ExecuteResult result = results.get(0);
        assertTrue(result.getMessage(), result.getSuccess());
        return result;
    }
}
