package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.api.model.Dashboard;
import com.jd.biz.domain.api.param.dashboard.DashboardCreateParam;
import com.jd.biz.domain.api.param.dashboard.DashboardUpdateParam;
import com.jd.biz.domain.repository.entity.DashboardDO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * @author moji
 * @version ChartConverter.java, v 0.1 2023年06月09日 17:13 moji Exp $
 * @date 2023/06/09
 */
@Mapper(componentModel = "spring")
public abstract class DashboardConverter {

    /**
     * 参数转换
     *
     * @param param
     * @return
     */
    public abstract DashboardDO param2do(DashboardCreateParam param);

    /**
     * 参数转换
     *
     * @param param
     * @return
     */
    public abstract DashboardDO updateParam2do(DashboardUpdateParam param);

    /**
     * 模型转换
     *
     * @param chartDO
     * @return
     */
    public abstract Dashboard do2model(DashboardDO chartDO);

    /**
     * 模型转换
     *
     * @param chartDOS
     * @return
     */
    public abstract List<Dashboard> do2model(List<DashboardDO> chartDOS);
}
