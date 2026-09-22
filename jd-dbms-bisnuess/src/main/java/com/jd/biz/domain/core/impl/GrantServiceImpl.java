package com.jd.biz.domain.core.impl;

import cn.hutool.core.util.StrUtil;
import com.jd.biz.controller.rdb.request.GrantDetailRequest;
import com.jd.biz.controller.rdb.request.TableDetailQueryRequest;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.GrantService;
import com.jd.common.tools.base.excption.BusinessException;
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
        String sql = sqlBuilder.createGrantTemplate(request.getDatabaseName(),
                request.getSchemaName(), request.getTableName(),request.getToGrantUser(),
                request.getInsert(),request.getUpdate(),request.getDelete());
        if (StrUtil.isBlank(sql)) {
            throw new BusinessException("当前数据库类型不支持表级授权");
        }
        dlExecuteParam.setSql(sql);
        return dlTemplateService.JDBCExecute(dlExecuteParam);
    }

    @Override
    public DataResult<ExecuteResult> deleteGrant(GrantDetailRequest request) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        DlExecuteParam dlExecuteParam = new DlExecuteParam();
        BeanUtils.copyProperties(request, dlExecuteParam);
        String sql = sqlBuilder.deleteGrantTemplate(request.getDatabaseName(),
                request.getSchemaName(), request.getTableName(),request.getToGrantUser(),
                request.getInsert(),request.getUpdate(),request.getDelete());
        if (StrUtil.isBlank(sql)) {
            throw new BusinessException("当前数据库类型不支持表级撤权");
        }
        dlExecuteParam.setSql(sql);
        return dlTemplateService.JDBCExecute(dlExecuteParam);
    }
}
