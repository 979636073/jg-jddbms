package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.enums.DataSourceKindEnum;
import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.model.DataSourceAccess;
import com.jd.biz.domain.api.service.DataSourceAccessService;
import com.jd.biz.domain.repository.mapper.DataSourceAccessCustomMapper;
import com.jd.biz.domain.repository.entity.DataSourceAccessDO;
import com.jd.common.core.domain.entity.SysRole;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

    @Test
    public void readerGrantAllowsRead() throws Exception {
        authenticate(42L, "dbms_reader");
        DataSourceAccessBusinessServiceImpl service = withGrant("METADATA_READ,DATA_READ");
        service.checkActionPermission(dataSource(DataSourceKindEnum.SHARED, 7L), DataSourceAction.DATA_READ);
    }

    @Test(expected = PermissionDeniedBusinessException.class)
    public void readerCannotExecuteSqlEvenAsOwner() {
        authenticate(42L, "dbms_reader");
        new DataSourceAccessBusinessServiceImpl().checkActionPermission(
                dataSource(DataSourceKindEnum.PRIVATE, 42L), DataSourceAction.SQL_EXECUTE);
    }

    @Test(expected = PermissionDeniedBusinessException.class)
    public void developerNeedsExplicitSqlGrantOnSharedConnection() throws Exception {
        authenticate(42L, "dbms_developer");
        withGrant("METADATA_READ,DATA_READ").checkActionPermission(
                dataSource(DataSourceKindEnum.SHARED, 7L), DataSourceAction.SQL_EXECUTE);
    }

    @Test(expected = PermissionDeniedBusinessException.class)
    public void developerOwnerCannotReadDataWithoutGrant() throws Exception {
        authenticate(42L, "dbms_developer");
        withGrant("METADATA_READ").checkActionPermission(
                dataSource(DataSourceKindEnum.PRIVATE, 42L), DataSourceAction.DATA_READ);
    }

    @Test
    public void developerOwnerCanExecuteSqlWithExplicitGrant() throws Exception {
        authenticate(42L, "dbms_developer");
        withGrant("METADATA_READ,SQL_EXECUTE").checkActionPermission(
                dataSource(DataSourceKindEnum.PRIVATE, 42L), DataSourceAction.SQL_EXECUTE);
    }

    @Test
    public void customRoleCanUseConfiguredDatabaseAction() throws Exception {
        authenticate(42L, "company_analyst", DataSourceAction.DATA_READ.platformPermission());
        withGrant("METADATA_READ,DATA_READ").checkActionPermission(
                dataSource(DataSourceKindEnum.SHARED, 7L), DataSourceAction.DATA_READ);
    }

    private static DataSourceAccessBusinessServiceImpl withGrant(String codes) throws Exception {
        DataSourceAccessDO grant = new DataSourceAccessDO();
        grant.setPermissionCodes(codes);
        DataSourceAccessBusinessServiceImpl service = new DataSourceAccessBusinessServiceImpl();
        setField(service, "dataSourceAccessCustomMapper",
                proxy(DataSourceAccessCustomMapper.class, Collections.singletonList(grant)));
        return service;
    }

    private static DataSource dataSource(DataSourceKindEnum kind, Long ownerId) {
        DataSource dataSource = new DataSource();
        dataSource.setId(100L);
        dataSource.setKind(kind.getCode());
        dataSource.setUserId(ownerId);
        return dataSource;
    }

    private static void authenticate(Long userId) {
        authenticate(userId, null);
    }

    private static void authenticate(Long userId, String roleKey) {
        String[] permissions = "dbms_developer".equals(roleKey)
                ? Arrays.stream(DataSourceAction.values()).map(DataSourceAction::platformPermission).toArray(String[]::new)
                : "dbms_reader".equals(roleKey)
                    ? new String[]{DataSourceAction.METADATA_READ.platformPermission(), DataSourceAction.DATA_READ.platformPermission()}
                    : new String[0];
        authenticate(userId, roleKey, permissions);
    }

    private static void authenticate(Long userId, String roleKey, String... permissions) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        if (roleKey != null) {
            SysRole role = new SysRole();
            role.setRoleKey(roleKey);
            role.setStatus("0");
            user.setRoles(Arrays.asList(role));
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUser(user);
        Set<String> permissionSet = new HashSet<>(Arrays.asList(permissions));
        loginUser.setPermissions(permissionSet);
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
