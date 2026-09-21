package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.model.Environment;
import com.jd.biz.domain.api.param.EnvironmentPageQueryParam;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;

import java.util.List;

/**
 * environment
 *
 * @author Jiaju Zhuang
 */
public interface EnvironmentService {

    /**
     * List Query Data
     *
     * @param idList
     * @return
     */
    ListResult<Environment> listQuery(List<Long> idList);

    /**
     * List Query Data
     *
     * @param param
     * @return
     */
    ListResult<Environment> pageQuery(EnvironmentPageQueryParam param);

}
