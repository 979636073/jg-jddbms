package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.model.OperationLog;
import com.jd.biz.domain.api.param.operation.OperationLogCreateParam;
import com.jd.biz.domain.api.param.operation.OperationLogPageQueryParam;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.PageResult;

/**
 * 用户执行ddl
 *
 * @author moji
 * @version UserExecutedDdlCoreService.java, v 0.1 2022年09月23日 17:35 moji Exp $
 * @date 2022/09/23
 */
public interface OperationLogService {

    /**
     * 创建用户执行的ddl记录
     *
     * @param param
     * @return
     */
    DataResult<Long> create(OperationLogCreateParam param);

    /**
     * 查询用户执行的ddl记录
     *
     * @param param
     * @return
     */
    PageResult<OperationLog> queryPage(OperationLogPageQueryParam param);
}
