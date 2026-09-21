package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.chart.ChartCreateParam;
import com.jd.biz.domain.api.chart.ChartListQueryParam;
import com.jd.biz.domain.api.chart.ChartQueryParam;
import com.jd.biz.domain.api.chart.ChartUpdateParam;
import com.jd.biz.domain.api.model.Chart;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author moji
 * @version ChartService.java, v 0.1 2023年06月09日 15:28 moji Exp $
 * @date 2023/06/09
 */
public interface ChartService {
    /**
     * 保存报表
     *
     * @param param
     * @return
     */
    DataResult<Long> createWithPermission(ChartCreateParam param);

    /**
     * 更新报表
     *
     * @param param
     * @return
     */
    ActionResult updateWithPermission(ChartUpdateParam param);

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    DataResult<Chart> find(@NotNull Long id);

    /**
     * 查询一条数据
     *
     * @param param
     * @return
     */
    DataResult<Chart> queryExistent(@NotNull ChartQueryParam param);

    /**
     * 查询一条数据
     *
     * @param id
     * @return
     */
    DataResult<Chart> queryExistent(@NotNull Long id);

    /**
     * 查询多条数据
     *
     * @param param
     * @return
     */
    ListResult<Chart> listQuery(@NotNull ChartListQueryParam param);

    /**
     * 通过ID查询图表列表
     *
     * @param ids
     * @return
     */
    ListResult<Chart> queryByIds(@NotEmpty List<Long> ids);

    /**
     * 删除
     *
     * @param id
     * @return
     */
    ActionResult deleteWithPermission(@NotNull Long id);

}
