package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.enums.DataSourceKindEnum;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.model.DataSourceAccess;
import com.jd.biz.domain.api.service.DataSourceAccessService;
import com.jd.biz.domain.repository.mapper.DataSourceAccessCustomMapper;
import com.jd.common.core.domain.entity.SysUser;
import com.jd.common.core.domain.model.LoginUser;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.common.exception.PermissionDeniedBusinessException;
import org.junit.After;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;

public class DataSourceAccessBusinessServiceImplTest {

    @After
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void shouldAllowPrivateDataSourceOwner() {
        authenticate(42L);
        DataSource dataSource = dataSource(DataSourceKindEnum.PRIVATE, 42L);

        new DataSourceAccessBusinessServiceImpl().checkPermission(dataSource);
    }

    @Test(expected = PermissionDeniedBusinessException.class)
    public void shouldRejectOtherUsersPrivateDataSource() {
        authenticate(42L);
        DataSource dataSource = dataSource(DataSourceKindEnum.PRIVATE, 7L);

        new DataSourceAccessBusinessServiceImpl().checkPermission(dataSource);
    }

    @Test
    public void shouldAllowExplicitSharedDataSourceAccess() throws Exception {
        authenticate(42L);
        DataSourceAccessBusinessServiceImpl service = new DataSourceAccessBusinessServiceImpl();
        DataSourceAccess access = new DataSourceAccess();
        DataSourceAccessService accessService = proxy(DataSourceAccessService.class,
                PageResult.of(Collections.singletonList(access), 1L, 1, 10));
        setField(service, "dataSourceAccessService", accessService);

        service.checkPermission(dataSource(DataSourceKindEnum.SHARED, 7L));
    }

    @Test(expected = PermissionDeniedBusinessException.class)
    public void shouldRejectSharedDataSourceWithoutUserOrTeamAccess() throws Exception {
        authenticate(42L);
        DataSourceAccessBusinessServiceImpl service = new DataSourceAccessBusinessServiceImpl();
        setField(service, "dataSourceAccessService",
                proxy(DataSourceAccessService.class, PageResult.empty(1, 10)));
        setField(service, "dataSourceAccessCustomMapper",
                proxy(DataSourceAccessCustomMapper.class, null));

        service.checkPermission(dataSource(DataSourceKindEnum.SHARED, 7L));
    }

    private static DataSource dataSource(DataSourceKindEnum kind, Long ownerId) {
        DataSource dataSource = new DataSource();
        dataSource.setId(100L);
        dataSource.setKind(kind.getCode());
        dataSource.setUserId(ownerId);
        return dataSource;
    }

    private static void authenticate(Long userId) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Object returnValue) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> returnValue);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
