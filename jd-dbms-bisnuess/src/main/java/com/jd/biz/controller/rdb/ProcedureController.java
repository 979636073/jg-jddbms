package com.jd.biz.controller.rdb;

import com.jd.biz.controller.rdb.converter.ProcedureConverter;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.ProcedureUpdateRequest;
import com.jd.biz.controller.rdb.request.TableDetailQueryRequest;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.service.ProcedureService;
import com.jd.common.annotation.Log;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.request.ProcedureDetailRequest;
import com.jd.biz.controller.rdb.request.ProcedurePageRequest;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Procedure;
import javax.validation.Valid;

import com.jd.spi.model.Sql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;

@ConnectionInfoAspect
@RequestMapping("/api/rdb/procedure")
@RestController
public class ProcedureController {

    @Autowired
    private ProcedureService procedureService;
    @Autowired
    private RdbWebConverter rdbWebConverter;

    @Autowired
    private ProcedureConverter procedureConverter;
    @GetMapping("/list")
    public WebPageResult<Procedure> list(@Valid ProcedurePageRequest request) {
        ListResult<Procedure> procedureListResult = procedureService.procedures(request.getDatabaseName(),
                request.getSchemaName());
        return WebPageResult.of(procedureListResult.getData(), Long.valueOf(procedureListResult.getData().size()), 1,
                procedureListResult.getData().size());
    }

    @GetMapping("/detail")
    public DataResult<Procedure> detail(@Valid ProcedureDetailRequest request) {
        return procedureService.detail(request.getDatabaseName(), request.getSchemaName(), request.getProcedureName());
    }

    @PostMapping("/update")
    @Log(title = "更新存储过程", businessType = BusinessType.UPDATE)
    public ActionResult update(@Valid @RequestBody ProcedureUpdateRequest request) throws SQLException {
        Procedure procedure = procedureConverter.request2param(request);
        return procedureService.update(request.getDatabaseName(), request.getSchemaName(), procedure);
    }


    /**
     * 创建存储过程
     * @param request
     * @return
     */
    @PostMapping("/create_procedure")
    public DataResult<Sql> createProcedure(@Valid @RequestBody ProcedureDetailRequest request) {
        return procedureService.buildSqlCreateProcedure(request);
    }

    /**
     * 删除存储过程
     * @param request
     * @return
     */
    @PostMapping("/delete_procedure")
    @Log(title = "删除存储过程", businessType = BusinessType.DELETE)
    public DataResult<ExecuteResult> deleteProcedure(@Valid @RequestBody ProcedureDetailRequest request) {
        return procedureService.deleteProcedure(request);
    }

}
