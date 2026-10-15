package com.jd.spi.sql;

import com.jd.spi.enums.DataTypeEnum;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.ExecuteResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SQLExecutorBlobTest {

    private Object previousMessageSource;

    @Before
    public void setUpContext() throws Exception {
        java.lang.reflect.Field messages = com.jd.common.tools.common.util.I18nUtils.class
                .getDeclaredField("messageSourceStatic");
        messages.setAccessible(true);
        previousMessageSource = messages.get(null);
        messages.set(null, Proxy.newProxyInstance(org.springframework.context.MessageSource.class.getClassLoader(),
                new Class[]{org.springframework.context.MessageSource.class}, (proxy, method, args) -> args[0]));
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType("oracle");
        connectInfo.setDriverConfig(new DriverConfig());
        Chat2DBContext.putContext(connectInfo);
    }

    @After
    public void clearContext() throws Exception {
        Chat2DBContext.removeContext();
        java.lang.reflect.Field messages = com.jd.common.tools.common.util.I18nUtils.class
                .getDeclaredField("messageSourceStatic");
        messages.setAccessible(true);
        messages.set(null, previousMessageSource);
    }

    @Test
    public void shouldExecuteMixedLobStatementsAndBindByType() {
        List<String> preparedSql = new ArrayList<>();
        List<String> bindings = new ArrayList<>();
        Connection connection = connection(preparedSql, bindings, 1, 1, 1);

        ExecuteResult result = SQLExecutor.getInstance().executeBlob(connection,
                "UPDATE T SET PAYLOAD = ? WHERE ID = 1;"
                        + "DELETE FROM T WHERE ID = 2;"
                        + "INSERT INTO T (NOTE) VALUES (?);",
                Arrays.asList("AQID", "long text"),
                Arrays.asList(DataTypeEnum.BYTE.getCode(), DataTypeEnum.CONTENT.getCode()));

        assertTrue(result.getSuccess());
        assertEquals(3, preparedSql.size());
        assertEquals(Arrays.asList("bytes:1:AQID", "string:1:long text"), bindings);
    }

    @Test
    public void shouldFailWhenOneStatementDoesNotAffectExactlyOneRow() {
        Connection connection = connection(new ArrayList<>(), new ArrayList<>(), 0);

        ExecuteResult result = SQLExecutor.getInstance().executeBlob(connection,
                "DELETE FROM T WHERE ID = 1;", Collections.emptyList(), Collections.emptyList());

        assertFalse(result.getSuccess());
        assertTrue(result.getMessage().contains("预期为 1 行"));
    }

    @Test
    public void shouldIgnoreQuestionMarkInsideStringLiteral() {
        List<String> bindings = new ArrayList<>();
        Connection connection = connection(new ArrayList<>(), bindings, 1);

        ExecuteResult result = SQLExecutor.getInstance().executeBlob(connection,
                "UPDATE T SET NOTE = 'why?' WHERE PAYLOAD = ?;",
                Collections.singletonList("AQID"),
                Collections.singletonList(DataTypeEnum.BYTE.getCode()));

        assertTrue(result.getSuccess());
        assertEquals(Collections.singletonList("bytes:1:AQID"), bindings);
    }

    @Test
    public void shouldKeepEmptyBlobDistinctFromNull() {
        List<String> bindings = new ArrayList<>();
        Connection connection = connection(new ArrayList<>(), bindings, 1, 1);

        ExecuteResult result = SQLExecutor.getInstance().executeBlob(connection,
                "UPDATE T SET PAYLOAD = ? WHERE ID = 1; UPDATE T SET PAYLOAD = ? WHERE ID = 2;",
                Arrays.asList("", null),
                Arrays.asList(DataTypeEnum.BYTE.getCode(), DataTypeEnum.BYTE.getCode()));

        assertTrue(result.getSuccess());
        assertEquals(Arrays.asList("bytes:1:", "bytes:1:null"), bindings);
    }

    @Test
    public void listQueryMustNotReadBlobContent() throws Exception {
        java.sql.Blob blob = (java.sql.Blob) Proxy.newProxyInstance(java.sql.Blob.class.getClassLoader(),
                new Class[]{java.sql.Blob.class}, (proxy, method, args) -> {
                    throw new AssertionError("列表不能读取 BLOB: " + method.getName());
                });
        java.sql.ResultSetMetaData metadata = (java.sql.ResultSetMetaData) Proxy.newProxyInstance(
                java.sql.ResultSetMetaData.class.getClassLoader(), new Class[]{java.sql.ResultSetMetaData.class},
                (proxy, method, args) -> {
                    if ("getColumnCount".equals(method.getName())) return 2;
                    if ("getColumnType".equals(method.getName()))
                        return (int) args[0] == 1 ? java.sql.Types.INTEGER : java.sql.Types.BLOB;
                    if ("getColumnTypeName".equals(method.getName())) return (int) args[0] == 1 ? "INTEGER" : "BLOB";
                    if ("getColumnName".equals(method.getName()) || "getColumnLabel".equals(method.getName()))
                        return (int) args[0] == 1 ? "ID" : "PAYLOAD";
                    return defaultValue(method.getReturnType());
                });
        int[] row = {0};
        java.sql.ResultSet rs = (java.sql.ResultSet) Proxy.newProxyInstance(java.sql.ResultSet.class.getClassLoader(),
                new Class[]{java.sql.ResultSet.class}, (proxy, method, args) -> {
                    if ("getMetaData".equals(method.getName())) return metadata;
                    if ("next".equals(method.getName())) return ++row[0] <= 3;
                    if ("getBlob".equals(method.getName())) return row[0] == 2 ? null : blob;
                    if ("getObject".equals(method.getName()) || "getBytes".equals(method.getName())
                            || "getBinaryStream".equals(method.getName()))
                        throw new AssertionError("不能提前读取二进制列");
                    return defaultValue(method.getReturnType());
                });
        java.sql.Statement stmt = (java.sql.Statement) Proxy.newProxyInstance(java.sql.Statement.class.getClassLoader(),
                new Class[]{java.sql.Statement.class}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) return true;
                    if ("getResultSet".equals(method.getName())) return rs;
                    return defaultValue(method.getReturnType());
                });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class[]{Connection.class}, (proxy, method, args) ->
                        "createStatement".equals(method.getName()) ? stmt : defaultValue(method.getReturnType()));

        ExecuteResult result = SQLExecutor.getInstance().execute("SELECT ID, PAYLOAD FROM T", connection,
                true, 0, 10, (resultSet, index, limit) -> {
                    assertEquals(1, index);
                    return Integer.toString(row[0]);
                });

        assertEquals(Arrays.asList(Arrays.asList("1", "A"), Arrays.asList("2", null), Arrays.asList("3", "A")),
                result.getDataList());
    }

    private Connection connection(List<String> preparedSql, List<String> bindings, int... affectedRows) {
        int[] statementIndex = {0};
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class[]{Connection.class}, (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        preparedSql.add((String) args[0]);
                        int affected = affectedRows[statementIndex[0]++];
                        return preparedStatement(bindings, affected);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private PreparedStatement preparedStatement(List<String> bindings, int affectedRows) {
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class}, (proxy, method, args) -> {
                    if ("setBytes".equals(method.getName())) {
                        byte[] value = (byte[]) args[1];
                        bindings.add("bytes:" + args[0] + ":" + (value == null ? "null"
                                : java.util.Base64.getEncoder().encodeToString(value)));
                    } else if ("setString".equals(method.getName())) {
                        bindings.add("string:" + args[0] + ":" + args[1]);
                    } else if ("executeUpdate".equals(method.getName())) {
                        return affectedRows;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }
}
