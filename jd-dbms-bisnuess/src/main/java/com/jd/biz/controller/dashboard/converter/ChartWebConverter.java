package com.jd.biz.controller.dashboard.converter;

import com.jd.biz.domain.api.chart.ChartCreateParam;
import com.jd.biz.domain.api.chart.ChartUpdateParam;
import com.jd.biz.domain.api.model.Chart;
import com.jd.biz.controller.dashboard.request.ChartCreateRequest;
import com.jd.biz.controller.dashboard.request.ChartUpdateRequest;
import com.jd.biz.controller.dashboard.vo.ChartVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

/**
 * @author moji
 * @version ChartWebConverter.java, v 0.1 2023年06月09日 15:46 moji Exp $
 * @date 2023/06/09
 */
@Mapper(componentModel = "spring")
public abstract class ChartWebConverter {

    /**
     * 模型转换
     *
     * @param chart
     * @return
     */
    @Mappings({
        @Mapping(target = "connectable", expression = "java(chart.getDataSourceName() != null)"),
    })
    public abstract ChartVO model2vo(Chart chart);

    /**
     * 模型转换
     *
     * @param charts
     * @return
     */
    public abstract List<ChartVO> model2vo(List<Chart> charts);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract ChartCreateParam req2param(ChartCreateRequest request);

    /**
     * 参数转换
     *
     * @param request
     * @return
     */
    public abstract ChartUpdateParam req2updateParam(ChartUpdateRequest request);
}
