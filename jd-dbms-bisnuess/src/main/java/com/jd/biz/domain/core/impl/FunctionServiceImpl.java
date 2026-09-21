package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.FunctionDetailRequest;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.FunctionService;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.SqlBuilder;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Function;
import com.jd.spi.model.Sql;
import com.jd.spi.sql.Chat2DBContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FunctionServiceImpl implements FunctionService {

    @Autowired
    private DlTemplateService dlTemplateService;
    @Override
    public ListResult<Function> functions(String databaseName, String schemaName) {
        return ListResult.of(Chat2DBContext.getMetaData().functions(Chat2DBContext.getConnection(),databaseName, schemaName));
    }

    @Override
    public DataResult<Function> detail(String databaseName, String schemaName, String functionName) {
        return DataResult.of(Chat2DBContext.getMetaData().function(Chat2DBContext.getConnection(), databaseName, schemaName, functionName));
    }


    /**
     * 创建函数
     * @param queryParam
     * @return
     */
    @Override
    public DataResult<Sql> buildSqlCreateFunction(FunctionDetailRequest queryParam) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        return DataResult.of(Sql.builder().sql(sqlBuilder.createFunctionTemplate(queryParam.getDatabaseName(),
                queryParam.getSchemaName(), queryParam.getFunctionName())).build());
    }

    /**
     * 删除函数
     * @param queryParam
     * @return
     */
    @Override
    public DataResult<ExecuteResult> deleteFunction(FunctionDetailRequest queryParam) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        DlExecuteParam dlExecuteParam = new DlExecuteParam();
        BeanUtils.copyProperties(queryParam, dlExecuteParam);
        dlExecuteParam.setSql(sqlBuilder.deleteFuction(queryParam.getDatabaseName(),
                queryParam.getSchemaName(), queryParam.getFunctionName()));
        return dlTemplateService.JDBCExecute(dlExecuteParam);
    }
}
