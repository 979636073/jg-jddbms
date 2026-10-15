package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.enums.AccessObjectTypeEnum;
import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.enums.DataSourceKindEnum;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.model.DataSourcePermissionPreview;
import com.jd.biz.domain.api.param.datasource.access.DataSourceAccessPageQueryParam;
import com.jd.biz.domain.api.service.DataSourceAccessBusinessService;
import com.jd.biz.domain.api.service.DataSourceAccessService;
import com.jd.biz.domain.repository.mapper.DataSourceAccessCustomMapper;
import com.jd.biz.domain.repository.entity.DataSourceAccessDO;
import com.jd.common.utils.SecurityUtil;
import com.jd.common.core.domain.entity.SysUser;
import com.jd.common.tools.common.exception.DataNotFoundException;
import com.jd.system.mapper.SysUserMapper;
import com.jd.system.service.ISysMenuService;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.common.exception.PermissionDeniedBusinessException;
import com.jd.common.tools.common.model.LoginUser;
import com.jd.common.tools.common.util.ContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.*;

/**
 * Data Source Access
 *
 * @author Jiaju Zhuang
 */
@Slf4j
@Service
public class DataSourceAccessBusinessServiceImpl implements DataSourceAccessBusinessService {

    @Resource
    private DataSourceAccessService dataSourceAccessService;
    @Resource
    private DataSourceAccessCustomMapper dataSourceAccessCustomMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private ISysMenuService menuService;
    @Override
    public ActionResult checkPermission(@NotNull DataSource dataSource) {
        LoginUser loginUser = ContextUtils.getLoginUser();
        // The owner retains access after changing a private connection to shared.
        if (loginUser.getAdmin() || loginUser.getId().equals(dataSource.getUserId())) {
            return ActionResult.isSuccess();
        }
        // private
        if (DataSourceKindEnum.PRIVATE.getCode().equals(dataSource.getKind())) {
            throw new PermissionDeniedBusinessException();
        }

        // Verify if user have permission
        DataSourceAccessPageQueryParam dataSourceAccessPageQueryParam = new DataSourceAccessPageQueryParam();
        dataSourceAccessPageQueryParam.setDataSourceId(dataSource.getId());
        dataSourceAccessPageQueryParam.setAccessObjectType(AccessObjectTypeEnum.USER.getCode());
        dataSourceAccessPageQueryParam.setAccessObjectId(loginUser.getId());
        dataSourceAccessPageQueryParam.queryOne();
        if (dataSourceAccessService.pageQuery(dataSourceAccessPageQueryParam, null).hasData()) {
            return ActionResult.isSuccess();
        }

        // Verify if the team has permission
        if (dataSourceAccessCustomMapper.checkTeamPermission(dataSource.getId(), loginUser.getId()) != null) {
            return ActionResult.isSuccess();

        }
        throw new PermissionDeniedBusinessException();
    }

    @Override
    public ActionResult checkActionPermission(@NotNull DataSource dataSource, @NotNull DataSourceAction action) {
        LoginUser user = ContextUtils.getLoginUser();
        if (Boolean.TRUE.equals(user.getAdmin())) return ActionResult.isSuccess();
        checkPlatformAction(action);
        if (user.getId().equals(dataSource.getUserId()) && (action == DataSourceAction.DATASOURCE_MANAGE
                || action == DataSourceAction.METADATA_READ)) return ActionResult.isSuccess();
        if (resourceSources(dataSource, user.getId()).containsKey(action.name())) return ActionResult.isSuccess();
        throw new PermissionDeniedBusinessException();
    }

    private Map<String, List<String>> resourceSources(DataSource dataSource, Long userId) {
        Map<String, List<String>> sources = new LinkedHashMap<>();
        boolean owner = userId.equals(dataSource.getUserId());
        if (owner) {
            sources.put(DataSourceAction.DATASOURCE_MANAGE.name(), new ArrayList<>(Collections.singletonList("OWNER")));
            sources.put(DataSourceAction.METADATA_READ.name(), new ArrayList<>(Collections.singletonList("OWNER")));
        }
        if (!owner && DataSourceKindEnum.PRIVATE.getCode().equals(dataSource.getKind())) return sources;
        List<DataSourceAccessDO> grants = dataSourceAccessCustomMapper.selectEffectiveGrants(
                dataSource.getId(), userId);
        for (DataSourceAccessDO grant : grants) {
            try {
                Set<String> codes = grant.getPermissionCodes() == null
                        ? DataSourceAction.DEFAULT_GRANT : DataSourceAction.parse(grant.getPermissionCodes());
                for (String code : codes) sources.computeIfAbsent(code, key -> new ArrayList<>())
                        .add(grant.getAccessObjectType() + ":" + grant.getAccessObjectId());
            } catch (IllegalArgumentException invalidGrant) {
                log.warn("Invalid action grant {} on data source {}", grant.getId(), dataSource.getId());
            }
        }
        return sources;
    }

    @Override
    public DataSourcePermissionPreview preview(DataSource dataSource, Long userId) {
        SysUser user = sysUserMapper.selectUserById(userId);
        if (user == null) throw new DataNotFoundException();
        DataSourcePermissionPreview result = new DataSourcePermissionPreview();
        result.setUserId(userId);
        result.setUserName(user.getUserName());
        result.setActive("0".equals(user.getStatus()) && "0".equals(user.getDelFlag()));
        boolean admin = user.isAdmin();
        Set<String> platform = admin ? Collections.emptySet() : menuService.selectMenuPermsByUserId(userId);
        Map<String, List<String>> resources = admin ? Collections.emptyMap() : resourceSources(dataSource, userId);
        for (DataSourceAction action : DataSourceAction.values()) {
            DataSourcePermissionPreview.ActionPermission item = new DataSourcePermissionPreview.ActionPermission();
            item.setCode(action.name());
            item.setPlatformAllowed(admin || platform.contains(action.platformPermission()));
            item.setResourceAllowed(admin || resources.containsKey(action.name()));
            item.setSources(admin ? Collections.singletonList("ADMIN")
                    : resources.getOrDefault(action.name(), Collections.emptyList()));
            item.setAllowed(result.isActive() && item.isPlatformAllowed() && item.isResourceAllowed());
            item.setReason(!result.isActive() ? "账号已停用或删除" : !item.isPlatformAllowed() ? "平台角色未允许此操作"
                    : !item.isResourceAllowed() ? "此连接未授予此操作" : admin ? "超级管理员" : "平台角色与连接授权均满足");
            result.getActions().add(item);
        }
        return result;
    }

    @Override
    public ActionResult checkPlatformAction(@NotNull DataSourceAction action) {
        LoginUser user = ContextUtils.getLoginUser();
        if (Boolean.TRUE.equals(user.getAdmin()) ||
                (SecurityUtil.getLoginUser().getPermissions() != null &&
                 SecurityUtil.getLoginUser().getPermissions().contains(action.platformPermission()))) {
            return ActionResult.isSuccess();
        }
        throw new PermissionDeniedBusinessException();
    }
}
