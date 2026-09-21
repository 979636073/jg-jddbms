package com.jd.biz.controller.rdb;

import com.jd.biz.domain.api.param.SchemaOperationParam;
import com.jd.biz.domain.api.param.SchemaQueryParam;
import com.jd.biz.domain.api.service.DatabaseService;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.TableService;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.SchemaCreateRequest;
import com.jd.biz.controller.rdb.request.UpdateSchemaRequest;
import com.jd.biz.controller.rdb.vo.SchemaVO;
import com.jd.spi.model.Schema;
import com.jd.spi.model.Sql;

import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * shema controller
 */
@ConnectionInfoAspect
@RequestMapping("/api/rdb/schema")
@RestController
public class SchemaController {

    @Autowired
    private TableService tableService;

    @Autowired
    private DlTemplateService dlTemplateService;

    @Resource
    private RdbWebConverter rdbWebConverter;

    @Autowired
    private DatabaseService databaseService;

    /**
     * 查询数据库里包含的schema_list
     *
     * @param request
     * @return
     */
    @GetMapping("/list")
    public ListResult<SchemaVO> list(@Valid DataSourceBaseRequest request) {
        SchemaQueryParam queryParam = SchemaQueryParam.builder().dataSourceId(request.getDataSourceId()).dataBaseName(
                request.getDatabaseName()).refresh(request.getRefresh()).build();
        ListResult<Schema> tableColumns = databaseService.querySchema(queryParam);
        List<SchemaVO> tableVOS = rdbWebConverter.schemaDto2vo(tableColumns.getData());
        return ListResult.of(tableVOS);
    }

    /**
     * 删除schema
     *
     * @param request
     * @return
     */
    @PostMapping("/delete_schema")
    public ActionResult deleteSchema(@Valid @RequestBody DataSourceBaseRequest request) {
        SchemaOperationParam param = SchemaOperationParam.builder().databaseName(request.getDatabaseName())
                .schemaName(request.getSchemaName()).build();
        return databaseService.deleteSchema(param);
    }

    /**
     * 创建schema
     *
     * @param request
     * @return
     */
    @PostMapping("/create_schema_sql")
    public DataResult<Sql> createSchema(@Valid @RequestBody SchemaCreateRequest request) {
        Schema schema = Schema.builder().databaseName(request.getDatabaseName())
                .name(request.getSchemaName())
                .owner(request.getOwner())
                .comment(request.getComment())
                .build();
        return databaseService.createSchema(schema);
    }

    /**
     * 创建database
     *
     * @param request
     * @return
     */
    @PostMapping("/modify_schema")
    public ActionResult modifySchema(@Valid @RequestBody UpdateSchemaRequest request) {
        SchemaOperationParam param = SchemaOperationParam.builder().databaseName(request.getDatabaseName())
                .schemaName(request.getSchemaName()).newSchemaName(request.getNewSchemaName()).build();
        return databaseService.modifySchema(param);
    }
}
