package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.model.Team;
import com.jd.biz.domain.api.param.team.TeamCreateParam;
import com.jd.biz.domain.api.param.team.TeamPageQueryParam;
import com.jd.biz.domain.api.param.team.TeamSelector;
import com.jd.biz.domain.api.param.team.TeamUpdateParam;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * team
 *
 * @author Jiaju Zhuang
 */
public interface TeamService {

    /**
     * Pagination query
     *
     * @param param
     * @param selector
     * @return
     */
    PageResult<Team> pageQuery(TeamPageQueryParam param, TeamSelector selector);

    /**
     * List Query Data
     *
     * @param idList
     * @return
     */
    ListResult<Team> listQuery(List<Long> idList);

    /**
     * Create
     *
     * @param param
     * @return
     */
    DataResult<Long> create(TeamCreateParam param);

    /**
     * update
     *
     * @param param
     * @return
     */
    DataResult<Long> update(TeamUpdateParam param);

    /**
     * delete
     *
     * @param id
     * @return
     */
    ActionResult delete(@NotNull Long id);

}
