package com.jd.biz.domain.core.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jd.biz.domain.api.enums.AccessObjectTypeEnum;
import com.jd.biz.domain.api.enums.RoleCodeEnum;
import com.jd.biz.domain.api.model.Team;
import com.jd.biz.domain.api.param.team.TeamCreateParam;
import com.jd.biz.domain.api.param.team.TeamPageQueryParam;
import com.jd.biz.domain.api.param.team.TeamSelector;
import com.jd.biz.domain.api.param.team.TeamUpdateParam;
import com.jd.biz.domain.api.service.TeamService;
import com.jd.biz.domain.core.converter.TeamConverter;
import com.jd.biz.domain.core.converter.UserConverter;
import com.jd.biz.domain.repository.entity.DataSourceAccessDO;
import com.jd.biz.domain.repository.entity.TeamDO;
import com.jd.biz.domain.repository.entity.TeamUserDO;
import com.jd.biz.domain.repository.mapper.DataSourceAccessMapper;
import com.jd.biz.domain.repository.mapper.TeamMapper;
import com.jd.biz.domain.repository.mapper.TeamUserMapper;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.common.exception.DataAlreadyExistsBusinessException;
import com.jd.common.tools.common.exception.ParamBusinessException;
import com.jd.common.tools.common.model.EasyLambdaQueryWrapper;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * team
 *
 * @author Jiaju Zhuang
 */
@Slf4j
@Service
public class TeamServiceImpl implements TeamService {
    @Resource
    private TeamMapper teamMapper;
    @Resource
    private TeamUserMapper teamUserMapper;
    @Resource
    private DataSourceAccessMapper dataSourceAccessMapper;
    @Resource
    private TeamConverter teamConverter;
    @Resource
    private UserConverter userConverter;

    @Override
    public ListResult<Team> listQuery(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return ListResult.empty();
        }
        LambdaQueryWrapper<TeamDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(TeamDO::getId, idList);
        List<TeamDO> dataList = teamMapper.selectList(queryWrapper);
        List<Team> list = teamConverter.do2dto(dataList);
        return ListResult.of(list);
    }

    @Override
    public PageResult<Team> pageQuery(TeamPageQueryParam param, TeamSelector selector) {
        EasyLambdaQueryWrapper<TeamDO> queryWrapper = new EasyLambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(TeamDO::getCode, "%" + param.getSearchKey() + "%")
                .or()
                .like(TeamDO::getName, "%" + param.getSearchKey() + "%"));
        }
        Page<TeamDO> page = new Page<>(param.getPageNo(), param.getPageSize());
        page.setSearchCount(param.getEnableReturnCount());
        queryWrapper.orderBy(param.getOrderByList());
        IPage<TeamDO> iPage = teamMapper.selectPage(page, queryWrapper);
        List<Team> list = teamConverter.do2dto(iPage.getRecords());

        fillData(list, selector);

        return PageResult.of(list, iPage.getTotal(), param);
    }

    @Override
    public DataResult<Long> create(TeamCreateParam param) {
        LambdaQueryWrapper<TeamDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TeamDO::getCode, param.getCode());
        Page<TeamDO> page = new Page<>(1, 1);
        page.setSearchCount(false);
        IPage<TeamDO> iPage = teamMapper.selectPage(page, queryWrapper);
        if (CollectionUtils.isNotEmpty(iPage.getRecords())) {
            throw new DataAlreadyExistsBusinessException("code", param.getCode());
        }
        if (RoleCodeEnum.DESKTOP.getCode().equals(param.getRoleCode())) {
            throw new ParamBusinessException("roleCode");
        }

        TeamDO data = teamConverter.param2do(param, ContextUtils.getUserId());
        teamMapper.insert(data);
        return DataResult.of(data.getId());
    }

    @Override
    public DataResult<Long> update(TeamUpdateParam param) {
        TeamDO data = teamConverter.param2do(param, ContextUtils.getUserId());
        teamMapper.updateById(data);
        return DataResult.of(data.getId());
    }

    @Override
    public ActionResult delete(Long id) {
        teamMapper.deleteById(id);

        LambdaQueryWrapper<TeamUserDO> teamUserQueryWrapper = new LambdaQueryWrapper<>();
        teamUserQueryWrapper.eq(TeamUserDO::getTeamId, id);
        teamUserMapper.delete(teamUserQueryWrapper);

        LambdaQueryWrapper<DataSourceAccessDO>  dataSourceAccessQueryWrapper = new LambdaQueryWrapper<>();
        dataSourceAccessQueryWrapper.eq(DataSourceAccessDO::getAccessObjectId, id)
            .eq(DataSourceAccessDO::getAccessObjectType, AccessObjectTypeEnum.TEAM.getCode())
        ;
        dataSourceAccessMapper.delete(dataSourceAccessQueryWrapper);
        return ActionResult.isSuccess();
    }

    private void fillData(List<Team> list, TeamSelector selector) {
        if (CollectionUtils.isEmpty(list) || selector == null) {
            return;
        }
        fillUser(list, selector);
    }

    private void fillUser(List<Team> list, TeamSelector selector) {
        if (BooleanUtils.isNotTrue(selector.getModifiedUser())) {
            return;
        }
        userConverter.fillDetail(EasyCollectionUtils.toList(list, Team::getModifiedUser));
    }

}
