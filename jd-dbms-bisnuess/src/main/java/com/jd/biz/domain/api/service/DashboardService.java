package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.model.Dashboard;
import com.jd.biz.domain.api.param.dashboard.DashboardCreateParam;
import com.jd.biz.domain.api.param.dashboard.DashboardPageQueryParam;
import com.jd.biz.domain.api.param.dashboard.DashboardQueryParam;
import com.jd.biz.domain.api.param.dashboard.DashboardUpdateParam;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.PageResult;

import javax.validation.constraints.NotNull;

/**
 * @author moji
 * @version DashboardService.java, v 0.1 2023年06月09日 15:28 moji Exp $
 * @date 2023/06/09
 */
public interface DashboardService {

    /**
     * 保存报表
     *
     * @param param
     * @return
     */
    DataResult<Long> createWithPermission(DashboardCreateParam param);

    /**
     * 更新报表
     *
     * @param param
     * @return
     */
    ActionResult updateWithPermission(DashboardUpdateParam param);

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    DataResult<Dashboard> find(@NotNull Long id);

    /**
     * 查询一条数据
     *
     * @param param
     * @return
     */
    DataResult<Dashboard> queryExistent(@NotNull DashboardQueryParam param);

    /**
     * 查询一条数据
     *
     * @param id
     * @return
     */
    DataResult<Dashboard> queryExistent(@NotNull Long id);

    /**
     * 删除
     *
     * @param id
     * @return
     */
    ActionResult deleteWithPermission(@NotNull Long id);

    /**
     * 查询报表列表
     *
     * @param param
     * @return
     */
    PageResult<Dashboard> queryPage(DashboardPageQueryParam param);
}
