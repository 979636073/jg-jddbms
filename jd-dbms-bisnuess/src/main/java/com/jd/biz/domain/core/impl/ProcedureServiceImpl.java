package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.ProcedureDetailRequest;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.ProcedureService;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.SqlBuilder;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Procedure;
import com.jd.spi.model.Sql;
import com.jd.spi.sql.Chat2DBContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProcedureServiceImpl implements ProcedureService {

    @Autowired
    private DlTemplateService dlTemplateService;

    @Override
    public ListResult<Procedure> procedures(String databaseName, String schemaName) {
        return ListResult.of(Chat2DBContext.getMetaData().procedures(Chat2DBContext.getConnection(),databaseName, schemaName));
    }

    @Override
    public DataResult<Procedure> detail(String databaseName, String schemaName, String procedureName) {
        return DataResult.of(Chat2DBContext.getMetaData().procedure(Chat2DBContext.getConnection(), databaseName, schemaName, procedureName));
    }

    @Override
    public ActionResult update(String databaseName, String schemaName, Procedure procedure) throws SQLException {
        Chat2DBContext.getDBManage().updateProcedure(Chat2DBContext.getConnection(), databaseName, schemaName, procedure);
        return ActionResult.isSuccess();
    }


    /**
     * 创建存储过程
     * @param queryParam
     * @return
     */
    @Override
    public DataResult<Sql> buildSqlCreateProcedure(ProcedureDetailRequest queryParam) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        return DataResult.of(Sql.builder().sql(sqlBuilder.createProcedureTemplate(queryParam.getDatabaseName(),
                queryParam.getSchemaName(), queryParam.getProcedureName())).build());
    }

    /**
     * 删除存储过程
     * @param queryParam
     * @return
     */
    @Override
    public DataResult<ExecuteResult> deleteProcedure(ProcedureDetailRequest queryParam) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        DlExecuteParam dlExecuteParam = new DlExecuteParam();
        dlExecuteParam.setSql(sqlBuilder.deleteProcedure(queryParam.getDatabaseName(),
                queryParam.getSchemaName(), queryParam.getProcedureName()));
        BeanUtils.copyProperties(queryParam, dlExecuteParam);
        return dlTemplateService.JDBCExecute(dlExecuteParam);
    }
}
