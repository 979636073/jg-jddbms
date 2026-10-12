package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.enums.AccessObjectTypeEnum;
import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.enums.DataSourceKindEnum;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.param.datasource.access.DataSourceAccessPageQueryParam;
import com.jd.biz.domain.api.service.DataSourceAccessBusinessService;
import com.jd.biz.domain.api.service.DataSourceAccessService;
import com.jd.biz.domain.repository.mapper.DataSourceAccessCustomMapper;
import com.jd.biz.domain.repository.entity.DataSourceAccessDO;
import com.jd.common.utils.SecurityUtil;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.common.exception.PermissionDeniedBusinessException;
import com.jd.common.tools.common.model.LoginUser;
import com.jd.common.tools.common.util.ContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

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
        boolean owner = user.getId().equals(dataSource.getUserId());
        if (owner && (action == DataSourceAction.DATASOURCE_MANAGE ||
                action == DataSourceAction.METADATA_READ)) return ActionResult.isSuccess();
        if (!owner && DataSourceKindEnum.PRIVATE.getCode().equals(dataSource.getKind())) {
            throw new PermissionDeniedBusinessException();
        }
        List<DataSourceAccessDO> grants = dataSourceAccessCustomMapper.selectEffectiveGrants(
                dataSource.getId(), user.getId());
        for (DataSourceAccessDO grant : grants) {
            try {
                Set<String> codes = grant.getPermissionCodes() == null
                        ? DataSourceAction.DEFAULT_GRANT : DataSourceAction.parse(grant.getPermissionCodes());
                if (codes.contains(action.name())) return ActionResult.isSuccess();
            } catch (IllegalArgumentException invalidGrant) {
                log.warn("Invalid action grant {} on data source {}", grant.getId(), dataSource.getId());
            }
        }
        throw new PermissionDeniedBusinessException();
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
