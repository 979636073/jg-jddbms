package com.jd.plugin.oracle.builder;

import com.jd.plugin.oracle.OracleMetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.enums.DataTypeEnum;
import com.jd.spi.model.Header;
import com.jd.spi.model.QueryResult;
import com.jd.spi.model.ResultOperation;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OracleSqlBuilderDmlTest {

    private static final String TEST_DB_TYPE = "ORACLE_DML_TEST";
    private Plugin previousPlugin;

    @Before
    public void setUpContext() {
        OracleMetaData metaData = new OracleMetaData();
        Plugin plugin = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(), new Class[]{Plugin.class},
                (proxy, method, args) -> "getMetaData".equals(method.getName()) ? metaData : null);
        previousPlugin = Chat2DBContext.PLUGIN_MAP.put(TEST_DB_TYPE, plugin);
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType(TEST_DB_TYPE);
        connectInfo.setDriverConfig(new DriverConfig());
        Chat2DBContext.putContext(connectInfo);
    }

    @After
    public void clearContext() {
        Chat2DBContext.removeContext();
        if (previousPlugin == null) {
            Chat2DBContext.PLUGIN_MAP.remove(TEST_DB_TYPE);
        } else {
            Chat2DBContext.PLUGIN_MAP.put(TEST_DB_TYPE, previousPlugin);
        }
    }

    @Test
    public void shouldUpdateTimestampUsingCompositePrimaryKeyWithoutRowId() {
        QueryResult query = queryResult("UPDATE",
                Arrays.asList("1", "10", "A", "2026-09-23 12:34:56"),
                Arrays.asList("1", "10", "A", "2026-09-23 10:11:12.123456"));

        String sql = new OracleSqlBuilder().buildSqlByQuery(query);

        assertEquals("UPDATE TEST_TABLE set \"D_TS\" = TO_TIMESTAMP('2026-09-23 12:34:56', 'yyyy-mm-dd hh24:mi:ss')"
                + " where \"ID\" = 10 and \"CODE\" = 'A';\n", sql);
    }

    @Test
    public void shouldPreserveFractionalSecondsWhenInsertingTimestamp() {
        QueryResult query = queryResult("CREATE",
                Arrays.asList("2", "11", "B", "2026-09-23 10:11:12.123456"), null);

        String sql = new OracleSqlBuilder().buildSqlByQuery(query);

        assertTrue(sql.contains("TO_TIMESTAMP('2026-09-23 10:11:12.123456', 'yyyy-mm-dd hh24:mi:ss.ff')"));
    }

    private QueryResult queryResult(String type, java.util.List<String> data,
                                    java.util.List<String> oldData) {
        QueryResult query = new QueryResult();
        query.setTableName("TEST_TABLE");
        query.setHeaderList(Arrays.asList(
                header("行号", DataTypeEnum.CHAT2DB_ROW_NUMBER, false),
                header("ID", DataTypeEnum.NUMERIC, true),
                header("CODE", DataTypeEnum.STRING, true),
                header("D_TS", DataTypeEnum.DATETIME, false)));
        ResultOperation operation = new ResultOperation();
        operation.setType(type);
        operation.setDataList(data);
        operation.setOldDataList(oldData);
        query.setOperations(Collections.singletonList(operation));
        return query;
    }

    private Header header(String name, DataTypeEnum type, boolean primaryKey) {
        return Header.builder().name(name).dataType(type.getCode()).primaryKey(primaryKey)
                .autoIncrement(0).build();
    }
}
