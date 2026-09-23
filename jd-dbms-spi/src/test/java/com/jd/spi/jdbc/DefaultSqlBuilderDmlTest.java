package com.jd.spi.jdbc;

import com.jd.spi.MetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.enums.DataTypeEnum;
import com.jd.spi.model.Header;
import com.jd.spi.model.BlobSqlResult;
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

public class DefaultSqlBuilderDmlTest {

    private static final String TEST_DB_TYPE = "DEFAULT_DML_TEST";
    private Plugin previousPlugin;

    @Before
    public void setUpContext() {
        MetaData metaData = (MetaData) Proxy.newProxyInstance(
                MetaData.class.getClassLoader(), new Class[]{MetaData.class},
                (proxy, method, args) -> {
                    if ("getMetaDataName".equals(method.getName())) {
                        String[] names = (String[]) args[0];
                        return "\"" + names[0] + "\"";
                    }
                    return null;
                });
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
    public void shouldUseCompositePrimaryKeyWhenRowIdIsMissing() {
        QueryResult query = queryResult(
                Arrays.asList(header("行号", DataTypeEnum.CHAT2DB_ROW_NUMBER, false),
                        header("ID", DataTypeEnum.NUMERIC, true),
                        header("CODE", DataTypeEnum.STRING, true),
                        header("NAME", DataTypeEnum.STRING, false)),
                operation("UPDATE",
                        Arrays.asList("1", "10", "A", "O'Brien"),
                        Arrays.asList("1", "10", "A", "old")));

        String sql = new DefaultSqlBuilder().buildSqlByQuery(query);

        assertEquals("UPDATE TEST_TABLE set \"NAME\" = 'O''Brien' where \"ID\" = 10 and \"CODE\" = 'A';\n", sql);
    }

    @Test
    public void shouldUseComparableOldValuesWhenTableHasNoPrimaryKey() {
        QueryResult query = queryResult(
                Arrays.asList(header("行号", DataTypeEnum.CHAT2DB_ROW_NUMBER, false),
                        header("NAME", DataTypeEnum.STRING, false),
                        header("NOTE", DataTypeEnum.CONTENT, false)),
                operation("DELETE", null, Arrays.asList("1", null, "large text")));

        String sql = new DefaultSqlBuilder().buildSqlByQuery(query);

        assertTrue(sql.contains("where \"NAME\" is null"));
        assertTrue(!sql.contains("\"NOTE\""));
    }

    @Test
    public void shouldSkipUpdateWhenValuesReturnToOriginalState() {
        ResultOperation operation = operation("UPDATE",
                Arrays.asList("1", "10"), Arrays.asList("1", "10"));
        QueryResult query = queryResult(
                Arrays.asList(header("行号", DataTypeEnum.CHAT2DB_ROW_NUMBER, false),
                        header("ID", DataTypeEnum.NUMERIC, true)), operation);

        assertEquals("", new DefaultSqlBuilder().buildSqlByQuery(query));
    }

    @Test
    public void shouldBuildMixedLobStatementsWithParameterTypesAndDelete() {
        java.util.List<Header> headers = Arrays.asList(
                header("行号", DataTypeEnum.CHAT2DB_ROW_NUMBER, false),
                header("ID", DataTypeEnum.NUMERIC, true),
                header("PAYLOAD", DataTypeEnum.BYTE, false),
                header("NOTE", DataTypeEnum.CONTENT, false));
        ResultOperation update = operation("UPDATE",
                Arrays.asList("1", "10", "AQID", "new text"),
                Arrays.asList("1", "10", "old", "old text"));
        ResultOperation delete = operation("DELETE", null,
                Arrays.asList("2", "11", null, "delete text"));
        QueryResult query = new QueryResult();
        query.setTableName("TEST_TABLE");
        query.setHeaderList(headers);
        query.setOperations(Arrays.asList(update, delete));

        BlobSqlResult result = new DefaultSqlBuilder().buildBlobSql(query);

        assertTrue(result.getDataSql().contains("UPDATE TEST_TABLE set \"PAYLOAD\" =  ? ,\"NOTE\" =  ?"));
        assertTrue(result.getDataSql().contains("DELETE FROM TEST_TABLE where \"ID\" = 11"));
        assertEquals(Arrays.asList("AQID", "new text"), result.getBlobValues());
        assertEquals(Arrays.asList(DataTypeEnum.BYTE.getCode(), DataTypeEnum.CONTENT.getCode()),
                result.getParameterTypes());
    }

    private QueryResult queryResult(java.util.List<Header> headers, ResultOperation operation) {
        QueryResult query = new QueryResult();
        query.setTableName("TEST_TABLE");
        query.setHeaderList(headers);
        query.setOperations(Collections.singletonList(operation));
        return query;
    }

    private ResultOperation operation(String type, java.util.List<String> data,
                                      java.util.List<String> oldData) {
        ResultOperation operation = new ResultOperation();
        operation.setType(type);
        operation.setDataList(data);
        operation.setOldDataList(oldData);
        return operation;
    }

    private Header header(String name, DataTypeEnum type, boolean primaryKey) {
        return Header.builder().name(name).dataType(type.getCode()).primaryKey(primaryKey)
                .autoIncrement(0).build();
    }
}
