package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.GrantDetailRequest;
import com.jd.biz.controller.rdb.request.TableDetailQueryRequest;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.GrantService;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.SqlBuilder;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Sql;
import com.jd.spi.sql.Chat2DBContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GrantServiceImpl implements GrantService {
    @Autowired
    private DlTemplateService dlTemplateService;
    @Override
    public DataResult<ExecuteResult> grantSql(GrantDetailRequest request) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        DlExecuteParam dlExecuteParam = new DlExecuteParam();
        BeanUtils.copyProperties(request, dlExecuteParam);
        dlExecuteParam.setSql(sqlBuilder.createGrantTemplate(request.getDatabaseName(),
                request.getSchemaName(), request.getTableName(),request.getToGrantUser(),
                request.getInsert(),request.getUpdate(),request.getDelete()));
        return dlTemplateService.JDBCExecute(dlExecuteParam);
    }

    @Override
    public DataResult<ExecuteResult> deleteGrant(GrantDetailRequest request) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        DlExecuteParam dlExecuteParam = new DlExecuteParam();
        BeanUtils.copyProperties(request, dlExecuteParam);
        dlExecuteParam.setSql(sqlBuilder.deleteGrantTemplate(request.getDatabaseName(),
                request.getSchemaName(), request.getTableName(),request.getToGrantUser(),
                request.getInsert(),request.getUpdate(),request.getDelete()));
        return dlTemplateService.JDBCExecute(dlExecuteParam);
    }
}
