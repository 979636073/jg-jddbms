package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.core.converter.CommandConverter;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.spi.CommandExecutor;
import com.jd.spi.MetaData;
import com.jd.spi.Plugin;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.model.Command;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DlTemplateServiceImplTest {

    private static final String TEST_DB_TYPE = "JDBC_EXECUTE_RESULT_TEST";
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
    public void shouldPropagateJdbcExecutionFailureToOuterResult() throws Exception {
        ExecuteResult execution = ExecuteResult.builder()
                .success(Boolean.FALSE)
                .message("drop failed")
                .build();

        DataResult<ExecuteResult> result = serviceReturning(execution).JDBCExecute(new DlExecuteParam());

        assertFalse(result.getSuccess());
        assertEquals("execute error", result.getErrorCode());
        assertEquals("drop failed", result.getErrorMessage());
        assertSame(execution, result.getData());
    }

    @Test
    public void shouldTreatMissingJdbcExecutionResultAsFailure() throws Exception {
        DataResult<ExecuteResult> result = serviceReturning(null).JDBCExecute(new DlExecuteParam());

        assertFalse(result.getSuccess());
        assertEquals("未返回执行结果", result.getErrorMessage());
        assertNull(result.getData());
    }

    @Test
    public void shouldKeepSuccessfulJdbcExecutionResult() throws Exception {
        ExecuteResult execution = ExecuteResult.builder().success(Boolean.TRUE).build();

        DataResult<ExecuteResult> result = serviceReturning(execution).JDBCExecute(new DlExecuteParam());

        assertTrue(result.getSuccess());
        assertSame(execution, result.getData());
    }

    private DlTemplateServiceImpl serviceReturning(ExecuteResult execution) throws Exception {
        CommandExecutor executor = proxy(CommandExecutor.class, (methodName) ->
                "JDBCExecute".equals(methodName) ? execution : defaultValue(methodName));
        MetaData metaData = proxy(MetaData.class, (methodName) ->
                "getCommandExecutor".equals(methodName) ? executor : defaultValue(methodName));
        Plugin plugin = proxy(Plugin.class, (methodName) ->
                "getMetaData".equals(methodName) ? metaData : defaultValue(methodName));

        previousPlugin = Chat2DBContext.PLUGIN_MAP.put(TEST_DB_TYPE, plugin);
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setDbType(TEST_DB_TYPE);
        connectInfo.setDriverConfig(new DriverConfig());
        Chat2DBContext.putContext(connectInfo);

        DlTemplateServiceImpl service = new DlTemplateServiceImpl();
        Field field = DlTemplateServiceImpl.class.getDeclaredField("commandConverter");
        field.setAccessible(true);
        field.set(service, new CommandConverter() {
            @Override
            public Command param2model(DlExecuteParam param) {
                return new Command();
            }
        });
        return service;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, ReturnValue returnValue) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> returnValue.forMethod(method.getName()));
    }

    private Object defaultValue(String methodName) {
        return null;
    }

    private interface ReturnValue {
        Object forMethod(String methodName);
    }
}
