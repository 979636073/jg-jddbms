package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.model.TeamUser;
import com.jd.biz.domain.api.param.team.user.TeamUserComprehensivePageQueryParam;
import com.jd.biz.domain.api.param.team.user.TeamUserCreatParam;
import com.jd.biz.domain.api.param.team.user.TeamUserPageQueryParam;
import com.jd.biz.domain.api.param.team.user.TeamUserSelector;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.PageResult;

import javax.validation.constraints.NotNull;

/**
 * team user
 *
 * @author Jiaju Zhuang
 */
public interface TeamUserService {

    /**
     * Comprehensive Paging Query Data
     *
     * @param param
     * @param selector
     * @return
     */
    PageResult<TeamUser> pageQuery(TeamUserPageQueryParam param, TeamUserSelector selector);

    /**
     * Comprehensive Paging Query Data
     *
     * @param param
     * @param selector
     * @return
     */
    PageResult<TeamUser> comprehensivePageQuery(TeamUserComprehensivePageQueryParam param, TeamUserSelector selector);

    /**
     * Create
     *
     * @param param
     * @return
     */
    DataResult<Long> create(TeamUserCreatParam param);

    /**
     * delete
     *
     * @param id
     * @return
     */
    ActionResult delete(@NotNull Long id);
}
