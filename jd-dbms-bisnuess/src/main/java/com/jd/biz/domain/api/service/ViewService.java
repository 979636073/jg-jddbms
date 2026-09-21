package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.*;
import com.jd.biz.domain.api.param.DropParam;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Sql;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * author jipengfei
 * date 2021/9/23 15:22
 */
public interface ViewService {

    /**
     * Querying all views under a schema.
     *
     * @param databaseName
     * @return
     */
    ListResult<Table> views(@NotEmpty String databaseName, String schemaName, Integer requestType);


    /**
     * Querying the details of a view.
     *
     * @param databaseName
     * @return
     */
    DataResult<Table> detail(@NotEmpty String databaseName, String schemaName,String tableName);

    /**
     * 展示SQL语句
     *
     * @param request 视图请求对象
     * @return DataResult<Sql> 包含SQL语句的DataResult对象
     */
    DataResult<Sql> showSql(ViewRequest request);

    /**
     * 获取视图SQL语句
     *
     * @param request 视图请求对象
     * @return DataResult<Sql> 包含SQL语句的DataResult对象
     */
    DataResult<Sql> getViewSql(ViewRequest request);

    /**
     * 查询表的列信息
     *
     * @param queryParam 表查询参数
     * @return ListResult<TableColumn> 包含查询到的列信息的列表
     */
    ListResult<TableColumn> queryColumns(TableQueryParam queryParam);

    ListResult<ExecuteResult> execute(ViewRequest request);

    /**
     * 删除视图
     *
     * @param request 视图请求对象
     * @return ActionResult 删除操作结果
     */
    void dropSelect(ViewNewRequest request);

    void drop(ViewRequest request);

    List<Table> getViewList(String databaseName, String schemaName);

    ExecuteResult allExecute( ViewRequest request);

    ExecuteResult getExecuteSQL(ViewRequest request);

    Boolean updateViewTableName(ViewQueryRequest request);

    Boolean updateViewColumnName(ViewQueryRequest request);

//    void batchAllExecute(ViewRequest request);
}
