package com.jd.biz.controller.dashboard.converter;

import com.jd.biz.domain.api.model.Dashboard;
import com.jd.biz.domain.api.param.dashboard.DashboardCreateParam;
import com.jd.biz.domain.api.param.dashboard.DashboardUpdateParam;
import com.jd.biz.controller.dashboard.request.DashboardCreateRequest;
import com.jd.biz.controller.dashboard.request.DashboardUpdateRequest;
import com.jd.biz.controller.dashboard.vo.DashboardVO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * @author moji
 * @version DashboardWebConverter.java, v 0.1 2023年06月09日 15:45 moji Exp $
 * @date 2023/06/09
 */
@Mapper(componentModel = "spring")
public abstract class DashboardWebConverter {

    /**
     * 模型转换
     *
     * @param dashboard
     * @return
     */
    public abstract DashboardVO model2vo(Dashboard dashboard);

    /**
     * 模型转换
     *
     * @param dashboards
     * @return
     */
    public abstract List<DashboardVO> model2vo(List<Dashboard> dashboards);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract DashboardCreateParam req2param(DashboardCreateRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract DashboardUpdateParam req2updateParam(DashboardUpdateRequest request);
}
