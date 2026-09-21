package com.jd.biz.domain.core.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jd.biz.domain.api.enums.AccessObjectTypeEnum;
import com.jd.biz.domain.api.enums.RoleCodeEnum;
import com.jd.biz.domain.api.model.User;
import com.jd.biz.domain.api.param.user.UserCreateParam;
import com.jd.biz.domain.api.param.user.UserPageQueryParam;
import com.jd.biz.domain.api.param.user.UserSelector;
import com.jd.biz.domain.api.param.user.UserUpdateParam;
import com.jd.biz.domain.api.service.UserService;
import com.jd.biz.domain.core.converter.UserConverter;
import com.jd.biz.domain.repository.entity.DataSourceAccessDO;
import com.jd.biz.domain.repository.entity.DbhubUserDO;
import com.jd.biz.domain.repository.entity.TeamUserDO;
import com.jd.biz.domain.repository.mapper.DataSourceAccessMapper;
import com.jd.biz.domain.repository.mapper.DbhubUserMapper;
import com.jd.biz.domain.repository.mapper.TeamUserMapper;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.common.exception.DataAlreadyExistsBusinessException;
import com.jd.common.tools.common.exception.ParamBusinessException;
import com.jd.common.tools.common.model.EasyLambdaQueryWrapper;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * 用户服务
 *
 * @author 是仪
 */
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private DbhubUserMapper dbhubUserMapper;
    @Resource
    private UserConverter userConverter;
    @Resource
    private TeamUserMapper teamUserMapper;
    @Resource
    private DataSourceAccessMapper dataSourceAccessMapper;

    @Override
    public DataResult<User> query(Long id) {
        return DataResult.of(userConverter.do2dto(dbhubUserMapper.selectById(id)));
    }

    @Override
    public DataResult<User> query(String userName) {
        LambdaQueryWrapper<DbhubUserDO> query = new LambdaQueryWrapper<>();
        if (Objects.nonNull(userName)) {
            query.eq(DbhubUserDO::getUserName, userName);
        }
        DbhubUserDO dbhubUserDO = dbhubUserMapper.selectOne(query);
        return DataResult.of(userConverter.do2dto(dbhubUserDO));
    }

    @Override
    public ListResult<User> listQuery(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return ListResult.empty();
        }
        LambdaQueryWrapper<DbhubUserDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(DbhubUserDO::getId, idList);
        List<DbhubUserDO> dataList = dbhubUserMapper.selectList(queryWrapper);
        List<User> list = userConverter.do2dto(dataList);
        return ListResult.of(list);
    }

    @Override
    public PageResult<User> pageQuery(UserPageQueryParam param, UserSelector selector) {
        EasyLambdaQueryWrapper<DbhubUserDO> queryWrapper = new EasyLambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(DbhubUserDO::getUserName, "%" + param.getSearchKey() + "%")
                .or()
                .like(DbhubUserDO::getNickName, "%" + param.getSearchKey() + "%")
                .or()
                .like(DbhubUserDO::getEmail, "%" + param.getSearchKey() + "%"));
        }
        // Default not to query desktop accounts
        queryWrapper.ne(DbhubUserDO::getId, RoleCodeEnum.DESKTOP.getDefaultUserId());
        queryWrapper.orderBy(param.getOrderByList());
        Page<DbhubUserDO> page = new Page<>(param.getPageNo(), param.getPageSize());
        page.setSearchCount(param.getEnableReturnCount());
        IPage<DbhubUserDO> iPage = dbhubUserMapper.selectPage(page, queryWrapper);
        List<User> list = userConverter.do2dto(iPage.getRecords());

        fillData(list, selector);
        return PageResult.of(list, iPage.getTotal(), param);
    }

    @Override
    public DataResult<Long> update(UserUpdateParam param) {
        if (RoleCodeEnum.DESKTOP.getDefaultUserId().equals(param.getId())) {
            throw new BusinessException("user.canNotOperateSystemAccount");
        }
        if (RoleCodeEnum.DESKTOP.getCode().equals(param.getRoleCode())) {
            throw new ParamBusinessException("roleCode");
        }

        DbhubUserDO data = userConverter.param2do(param, ContextUtils.getUserId());
        if (Objects.nonNull(data.getPassword())) {
            String bcryptPassword = DigestUtil.bcrypt(data.getPassword());
            data.setPassword(bcryptPassword);
        }

        if (RoleCodeEnum.ADMIN.getDefaultUserId().equals(param.getId())) {
            data.setStatus(null);
            data.setEmail(null);
            data.setUserName(null);
            data.setRoleCode(null);
        }
        dbhubUserMapper.updateById(data);
        return DataResult.of(data.getId());
    }

    @Override
    public ActionResult delete(Long id) {
        if (RoleCodeEnum.DESKTOP.getDefaultUserId().equals(id) || RoleCodeEnum.ADMIN.getDefaultUserId().equals(id)) {
            throw new BusinessException("user.canNotOperateSystemAccount");
        }
        dbhubUserMapper.deleteById(id);

        LambdaQueryWrapper<TeamUserDO> teamUserQueryWrapper = new LambdaQueryWrapper<>();
        teamUserQueryWrapper.eq(TeamUserDO::getUserId, id);
        teamUserMapper.delete(teamUserQueryWrapper);

        LambdaQueryWrapper<DataSourceAccessDO>  dataSourceAccessQueryWrapper = new LambdaQueryWrapper<>();
        dataSourceAccessQueryWrapper.eq(DataSourceAccessDO::getAccessObjectId, id)
            .eq(DataSourceAccessDO::getAccessObjectType, AccessObjectTypeEnum.USER.getCode())
        ;
        dataSourceAccessMapper.delete(dataSourceAccessQueryWrapper);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<Long> create(UserCreateParam param) {
        LambdaQueryWrapper<DbhubUserDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.eq(DbhubUserDO::getUserName, param.getUserName())
            .or()
            .eq(DbhubUserDO::getEmail, param.getEmail()));
        Page<DbhubUserDO> page = new Page<>(1, 1);
        page.setSearchCount(false);
        IPage<DbhubUserDO> iPage = dbhubUserMapper.selectPage(page, queryWrapper);
        if (CollectionUtils.isNotEmpty(iPage.getRecords())) {
            throw new DataAlreadyExistsBusinessException("userName or email",
                param.getUserName() + " or " + param.getEmail());
        }
        if (RoleCodeEnum.DESKTOP.getCode().equals(param.getRoleCode())) {
            throw new ParamBusinessException("roleCode");
        }

        DbhubUserDO data = userConverter.param2do(param, ContextUtils.getUserId());
        String bcryptPassword = DigestUtil.bcrypt(data.getPassword());
        data.setPassword(bcryptPassword);
        dbhubUserMapper.insert(data);
        return DataResult.of(data.getId());
    }

    private void fillData(List<User> list, UserSelector selector) {
        if (CollectionUtils.isEmpty(list) || selector == null) {
            return;
        }
        fillUser(list, selector);
    }

    private void fillUser(List<User> list, UserSelector selector) {
        if (BooleanUtils.isNotTrue(selector.getModifiedUser())) {
            return;
        }
        userConverter.fillDetail(EasyCollectionUtils.toList(list, User::getModifiedUser));
    }

}
