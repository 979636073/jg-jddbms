package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.enums.DataSourceKindEnum;
import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.model.DataSourceAccess;
import com.jd.biz.domain.api.model.DataSourcePermissionPreview;
import com.jd.system.mapper.SysUserMapper;
import com.jd.system.service.ISysMenuService;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

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

    @Test
    public void previewMatchesEnforcementAndRevocationWithoutAnotherLogin() throws Exception {
        authenticate(42L, "dbms_developer");
        List<DataSourceAccessDO> grants = new ArrayList<>();
        DataSourceAccessDO direct = new DataSourceAccessDO();
        direct.setAccessObjectType("USER"); direct.setAccessObjectId(42L);
        direct.setPermissionCodes("METADATA_READ,DATA_READ"); grants.add(direct);
        DataSourceAccessDO team = new DataSourceAccessDO();
        team.setAccessObjectType("TEAM"); team.setAccessObjectId(9L);
        team.setPermissionCodes("DATA_WRITE"); grants.add(team);
        DataSourceAccessBusinessServiceImpl service = withPreview(grants,
                DataSourceAction.METADATA_READ.platformPermission(), DataSourceAction.DATA_READ.platformPermission(),
                DataSourceAction.DATA_WRITE.platformPermission());
        DataSource source = dataSource(DataSourceKindEnum.SHARED, 7L);
        for (DataSourceAction action : DataSourceAction.values()) {
            boolean allowed = decision(service.preview(source, 42L), action).isAllowed();
            try {
                service.checkActionPermission(source, action);
                assertTrue(action.name(), allowed);
            } catch (PermissionDeniedBusinessException expected) { assertFalse(action.name(), allowed); }
        }
        assertEquals(Collections.singletonList("TEAM:9"), decision(service.preview(source, 42L), DataSourceAction.DATA_WRITE).getSources());
        grants.remove(team);
        assertFalse(decision(service.preview(source, 42L), DataSourceAction.DATA_WRITE).isAllowed());
        try { service.checkActionPermission(source, DataSourceAction.DATA_WRITE); fail(); }
        catch (PermissionDeniedBusinessException expected) { }
        grants.clear();
        assertFalse(decision(service.preview(source, 42L), DataSourceAction.DATA_READ).isAllowed());
    }

    @Test
    public void previewExplainsMissingRolePrivateConnectionAndOwnerLimits() throws Exception {
        DataSourceAccessDO grant = new DataSourceAccessDO();
        grant.setPermissionCodes("DATA_READ,SQL_EXECUTE");
        DataSourceAccessBusinessServiceImpl service = withPreview(Collections.singletonList(grant),
                DataSourceAction.METADATA_READ.platformPermission(), DataSourceAction.DATA_READ.platformPermission());
        assertEquals("平台角色未允许此操作", decision(service.preview(dataSource(DataSourceKindEnum.SHARED, 7L), 42L), DataSourceAction.SQL_EXECUTE).getReason());
        assertFalse(decision(service.preview(dataSource(DataSourceKindEnum.PRIVATE, 7L), 42L), DataSourceAction.DATA_READ).isAllowed());
        DataSourcePermissionPreview owner = service.preview(dataSource(DataSourceKindEnum.PRIVATE, 42L), 42L);
        assertEquals(Collections.singletonList("OWNER"), decision(owner, DataSourceAction.METADATA_READ).getSources());
        service = withPreview(Collections.emptyList(), DataSourceAction.DATA_READ.platformPermission());
        assertFalse(decision(service.preview(dataSource(DataSourceKindEnum.PRIVATE, 42L), 42L), DataSourceAction.DATA_READ).isAllowed());
    }

    @Test
    public void disabledUserHasNoEffectiveActionsEvenWithGrants() throws Exception {
        DataSourceAccessBusinessServiceImpl service = withPreview(Collections.emptyList(),
                DataSourceAction.METADATA_READ.platformPermission());
        SysUser user = new SysUser(); user.setUserId(42L); user.setStatus("1"); user.setDelFlag("0");
        setField(service, "sysUserMapper", proxy(SysUserMapper.class, user));
        DataSourcePermissionPreview preview = service.preview(dataSource(DataSourceKindEnum.PRIVATE, 42L), 42L);
        assertFalse(preview.isActive());
        for (DataSourcePermissionPreview.ActionPermission action : preview.getActions()) {
            assertFalse(action.isAllowed()); assertEquals("账号已停用或删除", action.getReason());
        }
    }

    private static DataSourceAccessBusinessServiceImpl withPreview(List<DataSourceAccessDO> grants, String... permissions) throws Exception {
        DataSourceAccessBusinessServiceImpl service = new DataSourceAccessBusinessServiceImpl();
        SysUser user = new SysUser(); user.setUserId(42L); user.setUserName("reader"); user.setStatus("0"); user.setDelFlag("0");
        setField(service, "sysUserMapper", proxy(SysUserMapper.class, user));
        setField(service, "menuService", proxy(ISysMenuService.class, new HashSet<>(Arrays.asList(permissions))));
        setField(service, "dataSourceAccessCustomMapper", proxy(DataSourceAccessCustomMapper.class, grants));
        return service;
    }

    private static DataSourcePermissionPreview.ActionPermission decision(DataSourcePermissionPreview preview, DataSourceAction action) {
        return preview.getActions().stream().filter(item -> action.name().equals(item.getCode())).findFirst().get();
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
