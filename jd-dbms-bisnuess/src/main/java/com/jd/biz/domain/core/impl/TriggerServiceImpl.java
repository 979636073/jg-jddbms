package com.jd.biz.domain.core.impl;

import com.jd.biz.controller.rdb.request.TriggerDetailRequest;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.TriggerService;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.SqlBuilder;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Sql;
import com.jd.spi.model.Trigger;
import com.jd.spi.sql.Chat2DBContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Service
public class TriggerServiceImpl implements TriggerService {
    @Autowired
    private DlTemplateService dlTemplateService;
    @Override
    public ListResult<Trigger> triggers(String databaseName, String schemaName, String tableName) {
        List<Trigger> triggers = Chat2DBContext.getMetaData().triggers(Chat2DBContext.getConnection(), databaseName, schemaName, tableName);
        for (Trigger trigger : triggers) {
            trigger.setQuerySql(trigger.getTriggerBody());
        }
        return ListResult.of(triggers);
    }

    @Override
    public DataResult<Trigger> detail(String databaseName, String schemaName, String triggerName) {
        return DataResult.of(Chat2DBContext.getMetaData().trigger(Chat2DBContext.getConnection(), databaseName, schemaName, triggerName));
    }

    /**
     * 创建触发器
     * @param queryParam
     * @return
     */
    @Override
    public DataResult<Sql> buildSqlCreateTriggers(TriggerDetailRequest queryParam) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        return DataResult.of(Sql.builder().sql(sqlBuilder.createTriggersTemplate(queryParam.getDatabaseName(),
                queryParam.getSchemaName(), queryParam.getTriggerName(),queryParam.getTableName(),
                queryParam.getBefore(),queryParam.getAfter())).build());
    }


    /**
     * 创建触发器
     * @param queryParam
     * @return
     */
    @Override
    public ActionResult createTriggersWH(TriggerDetailRequest queryParam) {
        try {
            if (StringUtils.isBlank(queryParam.getSql())) {
                throw new BusinessException("参数缺失");
            }
            if (Boolean.TRUE.equals(queryParam.getIsUpdate())) {
                if (StringUtils.isBlank(queryParam.getTriggerName())) {
                    throw new BusinessException("参数缺失");
                }
            }
            String replace = queryParam.getSql().replace("\n", " ");
            replace = replace.replace("\r", " ");
            try (Statement statement = Chat2DBContext.getConnection().createStatement()) {
                statement.execute(replace);
            }
        } catch (SQLException e) {
            throw new BusinessException(e.getMessage());
        }
        return ActionResult.isSuccess();
    }

    /**
     * 删除触发器
     * @param queryParam
     * @return
     */
    @Override
    public DataResult<ExecuteResult> deleteTriggers(TriggerDetailRequest queryParam) {
        if (StringUtils.isBlank(queryParam.getSchemaName()) || StringUtils.isBlank(queryParam.getTriggerName())) {
            throw new BusinessException("参数缺失");
        }
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        DlExecuteParam dlExecuteParam = new DlExecuteParam();
        BeanUtils.copyProperties(queryParam, dlExecuteParam);
        dlExecuteParam.setSql(sqlBuilder.deleteTriggers(queryParam.getDatabaseName(),
                queryParam.getSchemaName(), queryParam.getTriggerName()));
        try {
            return dlTemplateService.JDBCExecute(dlExecuteParam);
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
    }
}
