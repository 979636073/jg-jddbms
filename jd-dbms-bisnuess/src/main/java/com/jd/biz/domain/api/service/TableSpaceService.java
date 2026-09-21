package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.controller.rdb.request.TableSpaceCreateRequest;
import com.jd.biz.controller.rdb.request.TableSpaceUpdateRequest;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.Sql;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableDetails;

import java.util.List;
import java.util.Map;

/**
 * 表空间管理
 */
public interface TableSpaceService {

    /**
     * 表空间列表查询
     * @param databaseName
     * @param requestType
     * @return ListResult
     */
    ListResult<Table> list(String databaseName, Integer requestType);


    /**
     * 表空间详情
     * @param path
     * @param dbType
     * @return
     */
    Table query(String path, String dbType);
    /**
     * 创建表空间
     * @param request
     * @return
     */
    ActionResult createTablespace(TableSpaceCreateRequest request);

    /**
     * 删除表空间
     * @param request
     */
    void dropTablespace(TableSpaceCreateRequest request);

    /**
     * 查看现有表空间文件列表
     * @param request
     * @return
     */
    List<TableDetails> listPath(TableBriefQueryRequest request);

    /**
     * 查看临时表空间
     * @return
     */
    List<Map<String, String>> tempList(Integer type);

    /**
     * 修改表空间
     * @param request
     * @return
     */
    ActionResult updateTablespaceSql(TableSpaceUpdateRequest request);
}
