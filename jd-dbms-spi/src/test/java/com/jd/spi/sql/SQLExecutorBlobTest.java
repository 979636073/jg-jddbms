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

    @Before
    public void setUpContext() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType("oracle");
        connectInfo.setDriverConfig(new DriverConfig());
        Chat2DBContext.putContext(connectInfo);
    }

    @After
    public void clearContext() {
        Chat2DBContext.removeContext();
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
