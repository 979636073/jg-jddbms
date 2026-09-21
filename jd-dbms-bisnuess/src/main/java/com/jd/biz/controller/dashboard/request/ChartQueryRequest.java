package com.jd.biz.controller.dashboard.request;

import lombok.Data;

import java.util.List;

/**
 * @author moji
 * @version ChartQueryRequest.java, v 0.1 2023年06月09日 17:46 moji Exp $
 * @date 2023/06/09
 */
@Data
public class ChartQueryRequest {

    /**
     * 图表ID列表
     */
    private List<Long> ids;
}
