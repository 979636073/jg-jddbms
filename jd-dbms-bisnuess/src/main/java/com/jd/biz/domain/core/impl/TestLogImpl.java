package com.jd.biz.domain.core.impl;


import com.jd.biz.domain.api.model.OperationLog;
import com.jd.biz.domain.api.param.operation.OperationLogPageQueryParam;
import com.jd.common.tools.base.wrapper.result.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestLogImpl {

    @Autowired
    private OperationLogServiceImpl operationLogService;

    public PageResult<OperationLog> getLog(OperationLogPageQueryParam param) {
        return operationLogService.queryPage(param);
    }
}
