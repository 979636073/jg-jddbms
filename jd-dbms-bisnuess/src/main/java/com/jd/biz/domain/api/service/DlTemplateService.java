package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.DmlRequest;
import com.jd.biz.controller.rdb.request.GetBlobRequest;
import com.jd.biz.controller.rdb.vo.ExecuteResultVO;
import com.jd.biz.domain.api.param.DlCountParam;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.param.OrderByParam;
import com.jd.biz.domain.api.param.UpdateSelectResultParam;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.ExecuteResult;

import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据源管理服务
 *
 * @author moji
 * @version DataSourceCoreService.java, v 0.1 2022年09月23日 15:22 moji Exp $
 * @date 2022/09/23
 */
public interface DlTemplateService {

    /**
     * 数据源执行dl
     *
     * @param param
     * @return
     */
    ListResult<ExecuteResult> execute(DlExecuteParam param);

    /**
     * 删除session会话
     * @param param
     * @return boolean
     */
    boolean delSession(DlExecuteParam param);

    /**
     *
     * @param param
     * @return
     */
    ListResult<ExecuteResult> executeSelectTable(DlExecuteParam param);


    /**
     * 数据源执行update
     *
     * @param param
     * @return
     */
    DataResult<ExecuteResult> executeUpdate(DlExecuteParam param);

    /**
     * 执行统计sql
     *
     * @param param
     * @return
     */
    DataResult<Long> count(DlCountParam param);


    /**
     * 更新查询结果
     * @param param
     * @return
     */
    DataResult<String> updateSelectResult(UpdateSelectResultParam param);


    /**
     *
     * @param param
     * @return
     */
    DataResult<String> getOrderBySql(OrderByParam param);

    /**
     * 执行存储过程
     * @param param
     * @return
     */
    DataResult<ExecuteResult> JDBCExecute(DlExecuteParam param);

    DataResult<Boolean> executeDataBlobSql(UpdateSelectResultParam param);

    String parseFileType(String blobStr);

    void exportFile(String blobStr, String fileName, String prefix, HttpServletResponse response);

    boolean rollbackSession(DlExecuteParam param);

    boolean commitSession(DlExecuteParam param);

    boolean cancelExecution(String executionId);

    String getBlobData(GetBlobRequest request) throws SQLException;
}
