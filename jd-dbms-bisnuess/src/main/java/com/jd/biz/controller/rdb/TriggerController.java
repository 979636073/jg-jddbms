package com.jd.biz.controller.rdb;

import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.TableDetailQueryRequest;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.service.TriggerService;
import com.jd.common.annotation.Log;
import com.jd.common.enums.BusinessType;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.request.TriggerDetailRequest;
import com.jd.biz.controller.rdb.request.TriggerPageRequest;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Sql;
import com.jd.spi.model.Trigger;

import javax.annotation.Resource;
import javax.validation.Valid;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@ConnectionInfoAspect
@RequestMapping("/api/rdb/trigger")
@RestController
public class TriggerController {

    @Autowired
    private TriggerService triggerService;

    @Resource
    private RdbWebConverter rdbWebConverter;

    @GetMapping("/list")
    public WebPageResult<Trigger> list(@Valid TriggerPageRequest request) {
        ListResult<Trigger> listResult = triggerService.triggers(request.getDatabaseName(), request.getSchemaName(), request.getTableName());
        Long total = CollectionUtils.isNotEmpty(listResult.getData()) ? Long.valueOf(listResult.getData().size()) : 0L;
        Integer pageSize = listResult.getData() != null ? listResult.getData().size() : 0;
        return WebPageResult.of(listResult.getData(), total, 1, pageSize);
    }

    @GetMapping("/detail")
    public DataResult<Trigger> detail(@Valid TriggerDetailRequest request) {
        return triggerService.detail(request.getDatabaseName(), request.getSchemaName(), request.getTriggerName());
    }

    /**
     * 创建触发器
     * @param request
     * @return
     */
    @PostMapping("/create_triggers")
    public DataResult<Sql> createTriggers(@Valid @RequestBody TriggerDetailRequest request) {
        return triggerService.buildSqlCreateTriggers(request);
    }


    /**
     * 创建触发器
     * @param request
     * @return
     */
    @PostMapping("/createTriggersWH")
    @Log(title = "创建或更新数据库触发器", businessType = BusinessType.INSERT)
    public ActionResult createTriggersWH(@Valid @RequestBody TriggerDetailRequest request) {
        return triggerService.createTriggersWH(request);
    }


    /**
     * 删除触发器
     * @param request
     * @return
     */
    @PostMapping("/deleteTriggers")
    @Log(title = "删除数据库触发器", businessType = BusinessType.DELETE)
    public DataResult<ExecuteResult> deleteTriggers(@Valid @RequestBody TriggerDetailRequest request) {
        return triggerService.deleteTriggers(request);
    }
}
