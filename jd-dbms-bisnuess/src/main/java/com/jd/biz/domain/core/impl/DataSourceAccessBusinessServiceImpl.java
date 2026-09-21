package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.enums.AccessObjectTypeEnum;
import com.jd.biz.domain.api.enums.DataSourceKindEnum;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.param.datasource.access.DataSourceAccessPageQueryParam;
import com.jd.biz.domain.api.service.DataSourceAccessBusinessService;
import com.jd.biz.domain.api.service.DataSourceAccessService;
import com.jd.biz.domain.repository.mapper.DataSourceAccessCustomMapper;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.common.exception.PermissionDeniedBusinessException;
import com.jd.common.tools.common.model.LoginUser;
import com.jd.common.tools.common.util.ContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;

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
        // private
        if (DataSourceKindEnum.PRIVATE.getCode().equals(dataSource.getKind())) {
            if (loginUser.getAdmin() || loginUser.getId().equals(dataSource.getUserId())) {
                return ActionResult.isSuccess();
            } else {
                throw new PermissionDeniedBusinessException();
            }
        }

        // Administrators can edit anything
        if (loginUser.getAdmin()) {
            return ActionResult.isSuccess();
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
}
