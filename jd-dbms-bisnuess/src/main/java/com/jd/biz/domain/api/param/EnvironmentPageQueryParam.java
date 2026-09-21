package com.jd.biz.domain.api.param;

import com.jd.common.tools.base.wrapper.param.PageQueryParam;
import lombok.Data;

/**
 * environment
 *
 * @author Jiaju Zhuang
 */
@Data
public class EnvironmentPageQueryParam extends PageQueryParam {

    /**
     * 搜索关键词
     */
    private String searchKey;
}
