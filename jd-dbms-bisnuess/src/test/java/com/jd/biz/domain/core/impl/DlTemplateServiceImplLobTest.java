package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.param.UpdateSelectResultParam;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.spi.Plugin;
import com.jd.spi.SqlBuilder;
import com.jd.spi.MetaData;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.enums.DataTypeEnum;
import com.jd.spi.model.BlobSqlResult;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DlTemplateServiceImplLobTest {

    private static final String TEST_DB_TYPE = "oracle";
    private Plugin previousPlugin;

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
    public void shouldCommitAllLobStatementsTogether() {
        TransactionState state = new TransactionState(1, 1);
        prepareContext(state.connection(), lobResult());

        DataResult<Boolean> result = new DlTemplateServiceImpl()
                .executeDataBlobSql(new UpdateSelectResultParam());

        assertTrue(result.getSuccess());
        assertTrue(result.getData());
        assertEquals(1, state.commitCount);
        assertEquals(0, state.rollbackCount);
        assertEquals(1, state.closeCount);
    }

    @Test
    public void shouldRollbackAllLobStatementsWhenOneAffectsNoRows() {
        TransactionState state = new TransactionState(1, 0);
        prepareContext(state.connection(), lobResult());

        DataResult<Boolean> result = new DlTemplateServiceImpl()
                .executeDataBlobSql(new UpdateSelectResultParam());

        assertFalse(result.getSuccess());
        assertEquals(0, state.commitCount);
        assertEquals(1, state.rollbackCount);
        assertEquals(1, state.closeCount);
    }

    private void prepareContext(Connection connection, BlobSqlResult lobResult) {
        SqlBuilder sqlBuilder = (SqlBuilder) Proxy.newProxyInstance(SqlBuilder.class.getClassLoader(),
                new Class[]{SqlBuilder.class}, (proxy, method, args) ->
                        "buildBlobSql".equals(method.getName()) ? lobResult : defaultValue(method.getReturnType()));
        MetaData metaData = (MetaData) Proxy.newProxyInstance(MetaData.class.getClassLoader(),
                new Class[]{MetaData.class}, (proxy, method, args) ->
                        "getSqlBuilder".equals(method.getName()) ? sqlBuilder : defaultValue(method.getReturnType()));
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class[]{Plugin.class}, (proxy, method, args) ->
                        "getMetaData".equals(method.getName()) ? metaData : defaultValue(method.getReturnType()));
        previousPlugin = Chat2DBContext.PLUGIN_MAP.put(TEST_DB_TYPE, plugin);
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType(TEST_DB_TYPE);
        connectInfo.setDriverConfig(new DriverConfig());
        connectInfo.setConnection(connection);
        Chat2DBContext.putContext(connectInfo);
    }

    private BlobSqlResult lobResult() {
        BlobSqlResult result = new BlobSqlResult();
        result.setDataSql("UPDATE T SET PAYLOAD = ? WHERE ID = 1;DELETE FROM T WHERE ID = 2;");
        result.setBlobValues(Collections.singletonList("AQID"));
        result.setParameterTypes(Collections.singletonList(DataTypeEnum.BYTE.getCode()));
        return result;
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
        return null;
    }

    private class TransactionState {
        private final int[] affectedRows;
        private int statementIndex;
        private int commitCount;
        private int rollbackCount;
        private int closeCount;

        private TransactionState(int... affectedRows) {
            this.affectedRows = affectedRows;
        }

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class[]{Connection.class}, (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            int affected = affectedRows[statementIndex++];
                            return preparedStatement(affected);
                        }
                        if ("commit".equals(method.getName())) {
                            commitCount++;
                        } else if ("rollback".equals(method.getName())) {
                            rollbackCount++;
                        } else if ("close".equals(method.getName())) {
                            closeCount++;
                        } else if ("isClosed".equals(method.getName())) {
                            return false;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private PreparedStatement preparedStatement(int affectedRows) {
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class[]{PreparedStatement.class}, (proxy, method, args) -> {
                        if ("executeUpdate".equals(method.getName())) {
                            return affectedRows;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }
    }
}
