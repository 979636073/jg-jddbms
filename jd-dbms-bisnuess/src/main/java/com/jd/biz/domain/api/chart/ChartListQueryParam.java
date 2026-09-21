package com.jd.biz.domain.api.chart;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;

/**
 * query
 *
 * @author Jiaju Zhuang
 */
@Data
@NoArgsConstructor
public class ChartListQueryParam {

    /**
     * 主键
     */
    @NonNull
    private List<Long> idList;

    /**
     * 用户id
     */
    @NonNull
    private Long userId;

}
