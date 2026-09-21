package com.jd.biz.domain.core.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jd.biz.domain.api.model.TeamUser;
import com.jd.biz.domain.api.param.team.user.TeamUserComprehensivePageQueryParam;
import com.jd.biz.domain.api.param.team.user.TeamUserCreatParam;
import com.jd.biz.domain.api.param.team.user.TeamUserPageQueryParam;
import com.jd.biz.domain.api.param.team.user.TeamUserSelector;
import com.jd.biz.domain.api.service.TeamUserService;
import com.jd.biz.domain.core.converter.TeamConverter;
import com.jd.biz.domain.core.converter.TeamUserConverter;
import com.jd.biz.domain.core.converter.UserConverter;
import com.jd.biz.domain.repository.entity.TeamUserDO;
import com.jd.biz.domain.repository.mapper.TeamUserCustomMapper;
import com.jd.biz.domain.repository.mapper.TeamUserMapper;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Team User
 *
 * @author Jiaju Zhuang
 */
@Slf4j
@Service
public class TeamUserServiceImpl implements TeamUserService {

    @Resource
    private TeamUserConverter teamUserConverter;
    @Resource
    private TeamUserCustomMapper teamUserCustomMapper;
    @Resource
    private TeamUserMapper teamUserMapper;
    @Resource
    private UserConverter userConverter;
    @Resource
    private TeamConverter teamConverter;

    @Override
    public PageResult<TeamUser> pageQuery(TeamUserPageQueryParam param, TeamUserSelector selector) {
        LambdaQueryWrapper<TeamUserDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TeamUserDO::getTeamId, param.getTeamId())
            .eq(TeamUserDO::getUserId, param.getUserId())
        ;

        Page<TeamUserDO> page = new Page<>(param.getPageNo(), param.getPageSize());
        page.setSearchCount(param.getEnableReturnCount());
        IPage<TeamUserDO> iPage = teamUserMapper.selectPage(page, queryWrapper);

        List<TeamUser> list = teamUserConverter.do2dto(iPage.getRecords());

        fillData(list, selector);

        return PageResult.of(list, iPage.getTotal(), param);
    }

    @Override
    public PageResult<TeamUser> comprehensivePageQuery(TeamUserComprehensivePageQueryParam param,
        TeamUserSelector selector) {
        Page<TeamUserDO> page = new Page<>(param.getPageNo(), param.getPageSize());
        page.setSearchCount(param.getEnableReturnCount());
        IPage<TeamUserDO> iPage = teamUserCustomMapper.comprehensivePageQuery(page, param.getTeamId(),
            param.getUserId(), param.getTeamSearchKey(), param.getUserSearchKey());

        List<TeamUser> list = teamUserConverter.do2dto(iPage.getRecords());

        fillData(list, selector);

        return PageResult.of(list, iPage.getTotal(), param);
    }

    @Override
    public DataResult<Long> create(TeamUserCreatParam param) {
        TeamUserDO data = teamUserConverter.param2do(param, ContextUtils.getUserId());

        teamUserMapper.insert(data);
        return DataResult.of(data.getId());
    }

    @Override
    public ActionResult delete(Long id) {
        teamUserMapper.deleteById(id);
        return ActionResult.isSuccess();
    }

    private void fillData(List<TeamUser> list, TeamUserSelector selector) {
        if (CollectionUtils.isEmpty(list) || selector == null) {
            return;
        }

        fillUser(list, selector);

        fillTeam(list, selector);
    }

    private void fillUser(List<TeamUser> list, TeamUserSelector selector) {
        if (BooleanUtils.isNotTrue(selector.getUser())) {
            return;
        }
        userConverter.fillDetail(EasyCollectionUtils.toList(list, TeamUser::getUser));
    }

    private void fillTeam(List<TeamUser> list, TeamUserSelector selector) {
        if (BooleanUtils.isNotTrue(selector.getTeam())) {
            return;
        }
        teamConverter.fillDetail(EasyCollectionUtils.toList(list, TeamUser::getTeam));
    }
}
