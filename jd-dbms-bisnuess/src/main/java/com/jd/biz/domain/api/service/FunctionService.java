package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.FunctionDetailRequest;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Function;
import com.jd.spi.model.Sql;

import javax.validation.constraints.NotEmpty;

/**
 * author jipengfei
 * date 2021/9/23 15:22
 */
public interface FunctionService {

    /**
     * Querying all functions under a schema.
     *
     * @param databaseName
     * @return
     */
    ListResult<Function> functions(@NotEmpty String databaseName, String schemaName);

    /**
     * Querying function information.
     * @param databaseName
     * @param schemaName
     * @param functionName
     * @return
     */
    DataResult<Function> detail(String databaseName, String schemaName, String functionName);


    /**
     * 创建函数SQL
     * @param queryParam
     * @return
     */
    DataResult<Sql> buildSqlCreateFunction(FunctionDetailRequest queryParam);

    /**
     * 删除函数SQL
     * @param queryParam
     * @return
     */
    DataResult<ExecuteResult> deleteFunction(FunctionDetailRequest queryParam);
}
