package com.jd.biz.controller.rdb;

import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.TableDetailQueryRequest;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.service.FunctionService;
import com.jd.common.annotation.Log;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.request.FunctionDetailRequest;
import com.jd.biz.controller.rdb.request.FunctionPageRequest;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Function;

import javax.annotation.Resource;
import javax.validation.Valid;

import com.jd.spi.model.Sql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@ConnectionInfoAspect
@RequestMapping("/api/rdb/function")
@RestController
public class FunctionController {

    @Autowired
    private FunctionService functionService;

    @Resource
    private RdbWebConverter rdbWebConverter;
    @GetMapping("/list")
    public WebPageResult<Function> list(@Valid FunctionPageRequest request) {
        ListResult<Function> functionListResult = functionService.functions(request.getDatabaseName(),
            request.getSchemaName());
        return WebPageResult.of(functionListResult.getData(), Long.valueOf(functionListResult.getData().size()), 1,
            functionListResult.getData().size());
    }

    @GetMapping("/detail")
    public DataResult<Function> detail(@Valid FunctionDetailRequest request) {
        return functionService.detail(request.getDatabaseName(), request.getSchemaName(), request.getFunctionName());
    }


    /**
     * 创建函数
     * @param request
     * @return
     */
    @PostMapping("/create_function")
    public DataResult<Sql> createFunction(@Valid @RequestBody FunctionDetailRequest request) {
        return functionService.buildSqlCreateFunction(request);
    }

    /**
     * 删除函数SQL
     * @param request
     * @return
     */
    @PostMapping("/delete_function")
    @Log(title = "删除数据库函数", businessType = BusinessType.DELETE)
    public DataResult<ExecuteResult> deleteFunction(@Valid @RequestBody FunctionDetailRequest request) {
        return functionService.deleteFunction(request);
    }
}
