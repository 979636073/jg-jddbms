package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.common.tools.base.wrapper.result.ActionResult;

import javax.validation.constraints.NotNull;

/**
 * Data Source Access
 *
 * @author Jiaju Zhuang
 */
public interface DataSourceAccessBusinessService {
    /**
     * delete
     *
     * @param dataSource
     * @return
     */
    ActionResult checkPermission(@NotNull DataSource dataSource);
}
