package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.GrantDetailRequest;
import com.jd.biz.controller.rdb.request.TableDetailQueryRequest;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Sql;

public interface GrantService {
    /**
     * 授权sql
     * @param request
     * @return
     */
    DataResult<ExecuteResult> grantSql(GrantDetailRequest request);

    /**
     * 删除授权
     * @param request
     * @return
     */
    DataResult<ExecuteResult> deleteGrant(GrantDetailRequest request);
}
