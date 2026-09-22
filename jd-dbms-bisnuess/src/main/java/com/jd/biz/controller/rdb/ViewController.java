package com.jd.biz.controller.rdb;

import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.converter.SQLErrorConverter;
import com.jd.biz.controller.rdb.request.*;
import com.jd.biz.controller.rdb.vo.TableVO;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.service.TableService;
import com.jd.biz.domain.api.service.ViewService;
import com.jd.common.annotation.Log;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.common.utils.StringUtils;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Sql;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@ConnectionInfoAspect
@RequestMapping("/api/rdb/view")
@RestController
public class ViewController {
    @Autowired
    private ViewService viewService;

    @Autowired
    private TableService tableService;

    @Resource
    private RdbWebConverter rdbWebConverter;

    /**
     * 获取表列表
     *
     * @param request 包含查询条件的请求对象
     * @return 返回包含表信息的 WebPageResult 对象
     */
    @GetMapping("/list")
    public WebPageResult<TableVO> list(@Valid TableBriefQueryRequest request) {
        ListResult<Table> tableDTOPageResult = viewService.views(request.getDatabaseName(), request.getSchemaName(), request.getRequestType());
        List<TableVO> tableVOS = rdbWebConverter.tableDto2vo(tableDTOPageResult.getData());
        Integer pageSize = tableDTOPageResult.getData() != null ? tableDTOPageResult.getData().size() : 0;
        return WebPageResult.of(tableVOS, Long.valueOf(tableVOS.size()), 1, pageSize);
    }


    /**
     * 修改视图名
     * @param request 包含查询条件的请求对象
     * @return 返回包含表信息的 WebPageResult 对象
     */
    @PostMapping("/updateViewTableName")
    @Log(title = "修改视图名称", businessType = BusinessType.UPDATE)
    public DataResult<Boolean> updateViewTableName(@RequestBody  @Valid ViewQueryRequest request) {
        Boolean status = viewService.updateViewTableName(request);
        return Boolean.TRUE.equals(status)
                ? DataResult.of(Boolean.TRUE)
                : DataResult.error("view.rename.failed", "修改视图名称失败");
    }


    /**
     * 修改视图列明列名
     * @param request 包含查询条件的请求对象
     * @return 返回包含表信息的 WebPageResult 对象
     */
    @PostMapping("/updateViewColumnName")
    @Log(title = "修改视图列名", businessType = BusinessType.UPDATE)
    public DataResult<Boolean> updateViewColumnName(@RequestBody @Valid ViewQueryRequest request) {
        Boolean status =  viewService.updateViewColumnName(request);
        return Boolean.TRUE.equals(status)
                ? DataResult.of(Boolean.TRUE)
                : DataResult.error("view.column.rename.failed", "修改视图列名失败");
    }


    /**
     * 获取表列信息列表
     *
     * @param request 包含查询条件的请求对象
     * @return 包含表列信息的 ListResult 对象
     */
    @PostMapping("/column_list")
    public ListResult<TableColumn> columnList(@Valid @RequestBody TableDetailQueryRequest request) {
        TableQueryParam queryParam = rdbWebConverter.tableRequest2param(request);
        return viewService.queryColumns(queryParam);
    }


    /**
     * 获取表详情
     *
     * @param request 包含查询条件的请求对象
     * @return 包含表详情的 DataResult 对象
     */
    @GetMapping("/detail")
    public DataResult<Table> detail(@Valid TableDetailQueryRequest request) {
        return viewService.detail(request.getDatabaseName(), request.getSchemaName(), request.getTableName());
    }

    /**
     * 删除表
     *
     * @param request 包含删除表信息的请求对象
     * @return ActionResult 包含删除结果的响应对象
     */
    @PostMapping("/delete")
    @Log(title = "删除数据库视图", businessType = BusinessType.DELETE)
    public ActionResult delete(@Valid @RequestBody ViewNewRequest request) {
        try {
            if (request.getViewNames() != null && !request.getViewNames().isEmpty()) {
                viewService.dropSelect(request);
                return ActionResult.isSuccess();
            }
            if (StringUtils.isEmpty(request.getName())) {
                return ActionResult.fail("view.delete.name.required", "待删除视图不能为空", null);
            }
            ViewRequest viewRequest = new ViewRequest();
            viewRequest.setDataSourceId(request.getDataSourceId());
            viewRequest.setTableName(request.getName());
            viewRequest.setSchemaName(request.getSchemaName());
            viewService.drop(viewRequest);
            return ActionResult.isSuccess();
        } catch (Exception e) {
            return ActionResult.fail(EasyToolsConstant.ERROR_CODE, "视图删除出错", null);
        }

    }

    /**
     * 展示SQL语句
     *
     * @param request 视图请求对象
     * @return DataResult<Sql> 包含SQL语句的DataResult对象
     */
    @PostMapping("/showSql")
    public DataResult<Sql> showSql(@Valid @RequestBody ViewRequest request) {
        return viewService.showSql(request);
    }


    /**
     * 获取视图SQL语句
     *
     * @param request 视图请求对象
     * @return DataResult<Sql> 包含SQL语句的DataResult对象
     */
    @PostMapping("/getViewSql")
    public DataResult<Sql> getViewSql(@Valid @RequestBody ViewRequest request) {
        return viewService.getViewSql(request);
    }

    /**
     * 执行视图请求
     *
     * @param request 视图请求对象
     * @return ListResult<ExecuteResult> 包含执行结果的列表
     */
    @PostMapping("/execute")
    @Log(title = "执行视图 DDL", businessType = BusinessType.EXECUTE_DATA)
    public ListResult<ExecuteResult> execute(@RequestBody ViewRequest request) {
        return viewService.execute(request);
    }


    /**
     * 批量执行
     *
     * @param request 视图请求对象
     * @return ListResult<ExecuteResult> 包含执行结果的列表
     */
    @PostMapping("/allExecute")
    @Log(title = "批量编译数据库视图", businessType = BusinessType.EXECUTE_DATA)
    public ListResult<ExecuteResult> allExecute(@Valid @RequestBody ViewRequest request) {
        ListResult executeResultListResult = new ListResult();
        List<ExecuteResult> data = new ArrayList();
        ListResult<ExecuteResult> listResult = new ListResult<>();
        listResult.setSuccess(true);
        if (StringUtils.isEmpty(request.getTableName())) {
            List<Table> tableList = viewService.getViewList(request.getDatabaseName(), request.getSchemaName());
            for (Table table : tableList) {
                request.setTableName(table.getName());
                data.add(viewService.allExecute(request));
            }
        } else {
            if (request.getTableName().contains("[")) {
                String newTableName = request.getTableName().replace("[", "").replace("]", "").replace("\"", "");
                request.setTableName(newTableName);
            }
            String[] tableName = request.getTableName().split(",");
            if (tableName.length == 1) {
                data.add(viewService.allExecute(request));
            } else if (tableName.length > 1) {

                for (int i = 0; i < tableName.length; i++) {
                    request.setTableName(tableName[i]);
                    data.add(viewService.allExecute(request));
                }
            }
        }
        int count = 0;
        int errCount = 0;
        StringBuilder errorMessage = new StringBuilder();
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getSuccess()) {
                count++;
            } else {
                errCount++;
                errorMessage.append(SQLErrorConverter.transfer(data.get(i).getMessage()));
            }
        }
        if (count > 0) {
            if (errCount > 0) {
                executeResultListResult.setSuccess(Boolean.TRUE);
                executeResultListResult.setErrorMessage("编译成功,成功" + count + "条，失败" + errCount + "条,错误信息：" + errorMessage);
            } else {
                executeResultListResult.setSuccess(Boolean.TRUE);
                executeResultListResult.setErrorMessage("编译成功,成功" + count + "条");
            }
        } else {
            executeResultListResult.setSuccess(Boolean.FALSE);
            executeResultListResult.setErrorMessage("编译失败," + errorMessage);
        }
        executeResultListResult.setData(data);
        return executeResultListResult;
    }


    /**
     * 获取依赖
     *
     * @param request 视图请求对象
     * @return ListResult<ExecuteResult> 包含执行结果的列表
     */
    @PostMapping("/getExecuteSQL")
    public ExecuteResult getExecute(@Valid @RequestBody ViewRequest request) {

        return viewService.getExecuteSQL(request);

    }
}
