package com.jd.framework.web.service;

import com.jd.system.service.ISysConfigService;
import org.junit.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

public class SysLoginServiceTest {
    @Test
    public void acceptsUsernamesAllowedByUserManagement() throws Exception {
        SysLoginService service = new SysLoginService();
        ISysConfigService config = (ISysConfigService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{ISysConfigService.class}, (proxy, method, args) -> "");
        Field field = SysLoginService.class.getDeclaredField("configService");
        field.setAccessible(true);
        field.set(service, config);
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{HttpServletRequest.class}, (proxy, method, args) ->
                        "getRemoteAddr".equals(method.getName()) ? "127.0.0.1" : null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            for (int length : new int[]{21, 30}) {
                service.loginPreCheck(new String(new char[length]).replace('\0', 'a'), "test-password");
            }
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
