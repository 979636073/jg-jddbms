package com.jd.biz.aspect;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.common.exception.ParamBusinessException;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.biz.controller.rdb.vo.ExecuteResultVO;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.config.DriverConfig;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ConnectionInfoHandlerTest {
    @Test
    public void blobEndpointsRequireDataReadPermission() {
        try {
            for (String endpoint : new String[]{"preview", "download", "cancel"}) {
                org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest(
                        "POST", "/api/rdb/dml/blob/" + endpoint);
                org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                        new org.springframework.web.context.request.ServletRequestAttributes(request));
                Object action = org.springframework.test.util.ReflectionTestUtils.invokeMethod(new ConnectionInfoHandler(), "requestAction");
                assertEquals("DATA_READ", String.valueOf(action));
            }
        } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }
    }
    @Test
    public void retainedTransactionMustNotLeakIntoTheNextRequest() throws Throwable {
        for (boolean startsWithFailure : new boolean[]{false, true}) {
            AtomicInteger closes = new AtomicInteger();
            Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class[]{Connection.class}, (proxy, method, args) -> {
                        if ("isClosed".equals(method.getName())) return false;
                        if ("close".equals(method.getName())) closes.incrementAndGet();
                        return null;
                    });
            ConnectInfo info = new ConnectInfo();
            info.setDriverConfig(new DriverConfig());
            info.setConnection(connection);
            Chat2DBContext.putContext(info);
            ExecuteResultVO retained = new ExecuteResultVO();
            retained.setSessionId("retained-session");
            ListResult<ExecuteResultVO> result = ListResult.of(startsWithFailure
                    ? Arrays.asList(new ExecuteResultVO(), retained) : Arrays.asList(retained));
            ProceedingJoinPoint execution = (ProceedingJoinPoint) Proxy.newProxyInstance(
                    ProceedingJoinPoint.class.getClassLoader(), new Class[]{ProceedingJoinPoint.class},
                    (proxy, method, args) -> "getArgs".equals(method.getName()) ? new Object[0] : result);
            ConnectionInfoHandler handler = new ConnectionInfoHandler();
            try {
                handler.connectionInfoHandler(execution);
                assertNull("事务连接应保留，但请求线程必须解除绑定", Chat2DBContext.getConnectInfo());
                ProceedingJoinPoint nextRequest = (ProceedingJoinPoint) Proxy.newProxyInstance(
                        ProceedingJoinPoint.class.getClassLoader(), new Class[]{ProceedingJoinPoint.class},
                        (proxy, method, args) -> "getArgs".equals(method.getName()) ? new Object[0] : DataResult.of(null));
                handler.connectionInfoHandler(nextRequest);
                assertEquals("后续请求不能关闭前一窗口的事务连接", 0, closes.get());
            } finally {
                Chat2DBContext.removeContext();
            }
        }
    }


    @Test(expected = ParamBusinessException.class)
    public void shouldRejectMissingDataSourceBeforeBuildingConnectionContext() throws Exception {
        ConnectionInfoHandler handler = new ConnectionInfoHandler();
        DataSourceService dataSourceService = (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[]{DataSourceService.class},
                (proxy, method, args) -> DataResult.<DataSource>of(null));
        setField(handler, "dataSourceService", dataSourceService);

        handler.toInfo(999L, null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
