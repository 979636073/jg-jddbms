package com.jd.biz.aspect;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.common.exception.ParamBusinessException;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

public class ConnectionInfoHandlerTest {

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
