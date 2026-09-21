package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.TableDetailQueryRequest;
import com.jd.biz.controller.rdb.request.TriggerDetailRequest;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Sql;
import com.jd.spi.model.Trigger;

import javax.validation.constraints.NotEmpty;

public interface TriggerService {

    /**
     * Querying all triggers under a schema.
     *
     * @param databaseName
     * @return
     */
    ListResult<Trigger> triggers(@NotEmpty String databaseName, String schemaName, String tableName);

    /**
     * Querying trigger information.
     * @param databaseName
     * @param schemaName
     * @param triggerName
     * @return
     */
    DataResult<Trigger> detail(String databaseName, String schemaName, String triggerName);


    /**
     * 创建触发器SQL
     * @param queryParam
     * @return
     */
    DataResult<Sql> buildSqlCreateTriggers(TriggerDetailRequest queryParam);

    /**
     * 删除触发器SQL
     * @param queryParam
     * @return
     */
    DataResult<ExecuteResult> deleteTriggers(TriggerDetailRequest queryParam);

     ActionResult createTriggersWH(TriggerDetailRequest request);

}
