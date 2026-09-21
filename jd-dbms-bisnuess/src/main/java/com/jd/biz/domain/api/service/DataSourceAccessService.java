package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.model.DataSourceAccess;
import com.jd.biz.domain.api.param.datasource.access.DataSourceAccessComprehensivePageQueryParam;
import com.jd.biz.domain.api.param.datasource.access.DataSourceAccessCreatParam;
import com.jd.biz.domain.api.param.datasource.access.DataSourceAccessPageQueryParam;
import com.jd.biz.domain.api.param.datasource.access.DataSourceAccessSelector;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.PageResult;

import javax.validation.constraints.NotNull;

/**
 * Data Source Access
 *
 * @author Jiaju Zhuang
 */
public interface DataSourceAccessService {

    /**
     * Comprehensive Paging Query Data
     *
     * @param param
     * @param selector
     * @return
     */
    PageResult<DataSourceAccess> pageQuery(DataSourceAccessPageQueryParam param, DataSourceAccessSelector selector);

    /**
     * Paging Query Data
     *
     * @param param
     * @param selector
     * @return
     */
    PageResult<DataSourceAccess> comprehensivePageQuery(DataSourceAccessComprehensivePageQueryParam param,
        DataSourceAccessSelector selector);


    /**
     * Batch Create
     *
     * @param param
     * @return
     */
    DataResult<Long> create(DataSourceAccessCreatParam param);
    /**
     * delete
     *
     * @param id
     * @return
     */
    ActionResult delete(@NotNull Long id);
}
