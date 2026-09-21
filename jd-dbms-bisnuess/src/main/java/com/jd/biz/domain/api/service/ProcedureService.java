package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.ProcedureDetailRequest;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Procedure;
import com.jd.spi.model.Sql;

import javax.validation.constraints.NotEmpty;
import java.sql.SQLException;

public interface ProcedureService {

    /**
     * Querying all procedures under a schema.
     *
     * @param databaseName
     * @return
     */
    ListResult<Procedure> procedures(@NotEmpty String databaseName, String schemaName);

    /**
     * Querying procedure information.
     * @param databaseName
     * @param schemaName
     * @param procedureName
     * @return
     */
    DataResult<Procedure> detail(String databaseName, String schemaName, String procedureName);

    /**
     * @param databaseName
     * @param schemaName
     * @param procedure
     * @return
     */
    ActionResult update(String databaseName, String schemaName, Procedure procedure) throws SQLException;



    /**
     * 构建创建存储过程sql
     * @param queryParam
     * @return
     */
    DataResult<Sql> buildSqlCreateProcedure(ProcedureDetailRequest queryParam);

    /**
     * 构建删除存储过程sql
     * @param queryParam
     * @return
     */
    DataResult<ExecuteResult> deleteProcedure(ProcedureDetailRequest queryParam);
}
