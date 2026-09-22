package com.jd.biz.controller.rdb;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.DbType;
import com.alibaba.fastjson2.JSON;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.DdlCountRequest;
import com.jd.biz.controller.rdb.request.DmlRequest;
import com.jd.biz.controller.rdb.request.DmlTableRequest;
import com.jd.biz.controller.rdb.request.GetBlobRequest;
import com.jd.biz.controller.rdb.request.OrderByRequest;
import com.jd.biz.controller.rdb.request.SelectResultUpdateRequest;
import com.jd.biz.controller.rdb.request.exportFile;
import com.jd.biz.controller.rdb.vo.ExecuteResultVO;
import com.jd.biz.domain.api.model.Config;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.param.OrderByParam;
import com.jd.biz.domain.api.param.UpdateSelectResultParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.core.util.SqlSafetyChecker;
import com.jd.biz.http.GatewayClientService;
import com.jd.common.annotation.Log;
import com.jd.common.constant.Constants;
import com.jd.common.enums.BusinessType;
import com.jd.common.enums.DBTypeEnum;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.common.util.ConfigUtils;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import com.jd.spi.util.JdbcUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * mysql数据运维类
 *
 * @author moji
 * @version MysqlDataManageController.java, v 0.1 2022年09月16日 17:37 moji Exp $
 * @date 2022/09/16
 */
@ConnectionInfoAspect
@RequestMapping("/api/rdb/dml")
@RestController
@Slf4j
public class RdbDmlController {

    @Resource
    private RdbWebConverter rdbWebConverter;

    @Autowired
    private DlTemplateService dlTemplateService;

    @Autowired
    private GatewayClientService gatewayClientService;

    public static ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 增删改查等数据运维
     * @param request
     * @return List
     */
    @RequestMapping(value = "/execute", method = {RequestMethod.POST, RequestMethod.PUT})
    public ListResult<ExecuteResultVO> manage(@RequestBody DmlRequest request) {
        log.info("入参:{}", JSON.toJSONString(request));
        if (StrUtil.isNotBlank(request.getSql())) {
            String sql = request.getSql();
//            sql = Base64.getEncoder().(sql);
            sql = sql.replace("&gt;", ">");
            sql = sql.replace("&lt;", "<");
//            sql = sql.replaceAll("'", "''");
            request.setSql(sql);
        }
        if (request.getIsDataView() && StrUtil.isNotBlank(request.getSql())) {
            String sql = request.getSql();
            sql = String.format(Constants.SELECT_Y, sql);
            request.setSql(sql);
        }
        if (!Boolean.TRUE.equals(request.getConfirmDangerousSql())) {
            DbType dbType = JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
            List<String> risks = SqlSafetyChecker.findRisks(request.getSql(), dbType);
            if (!risks.isEmpty()) {
                return ListResult.error("sql.confirmRequired", String.join("；", risks));
            }
        }
        DlExecuteParam param = rdbWebConverter.request2param(request);
        param.setIsErrorExecute(request.getIsErrorExecute());
        param.setIsExecuteCompile(request.getIsExecuteCompile());
        ListResult<ExecuteResult> resultDTOListResult = dlTemplateService.execute(param);
        ListResult<ExecuteResultVO> result = toExecuteResult(resultDTOListResult);
        return result;
    }

    @RequestMapping(value = "/cancel", method = RequestMethod.POST)
    public ActionResult cancel(@RequestBody DmlRequest request) {
        if (StrUtil.isBlank(request.getExecutionId())) {
            return ActionResult.fail("缺少执行标识");
        }
        return dlTemplateService.cancelExecution(request.getExecutionId())
                ? ActionResult.isSuccess()
                : ActionResult.fail("SQL 已结束或尚未开始执行");
    }

    private ListResult<ExecuteResultVO> toExecuteResult(ListResult<ExecuteResult> source) {
        List<ExecuteResultVO> resultVOS = rdbWebConverter.dto2vo(source.getData());
        ListResult<ExecuteResultVO> result = ListResult.of(resultVOS);
        result.setSuccess(source.getSuccess());
        result.setErrorCode(source.getErrorCode());
        result.setErrorMessage(source.getErrorMessage());
        result.setErrorDetail(source.getErrorDetail());
        result.setSolutionLink(source.getSolutionLink());
        result.setTraceId(source.getTraceId());
        return result;
    }


    /**
     * 增删改查等数据运维
     * @param request
     * @return List
     */
    @RequestMapping(value = "/getBlobData", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<String> getBlobData(@Valid @RequestBody GetBlobRequest request) throws SQLException {
        log.info("入参:{}", JSON.toJSONString(request));
        String blobData = dlTemplateService.getBlobData(request);
        return DataResult.of(blobData);
    }




    /**
     * 会话回退
     * @param request
     * @return List
     */
    @RequestMapping(value = "/rollbackSession", method = {RequestMethod.POST, RequestMethod.PUT})
    public ListResult<ExecuteResultVO> rollbackSession(@RequestBody DmlRequest request) {
        DlExecuteParam param = rdbWebConverter.request2param(request);
        param.setIsErrorExecute(request.getIsErrorExecute());
        param.setIsExecuteCompile(request.getIsExecuteCompile());
        boolean isSuccess = dlTemplateService.rollbackSession(param);
        return ListResult.of(build(request, isSuccess, "回退"));
    }

    /**
     * build返回值
     * @param request
     * @param isSuccess
     * @return List
     */
    public List<ExecuteResultVO> build(DmlRequest request, Boolean isSuccess, String message) {
        List<ExecuteResultVO> resultVOS = new ArrayList<>();
        ExecuteResultVO resultVO = new ExecuteResultVO();
        resultVO.setSessionId(request.getSessionId());
        resultVO.setSign(false);
        resultVOS.add(resultVO);
        String meg = isSuccess ? message + "执行成功" : message + "执行失败";
        resultVO.setDescription(meg);
        resultVO.setAllMessage(meg);
        resultVO.setSuccess(isSuccess);
        return resultVOS;
    }


    /**
     * 提交会话
     * @param request
     * @return List
     */
    @RequestMapping(value = "/commitSession", method = {RequestMethod.POST, RequestMethod.PUT})
    public ListResult<ExecuteResultVO> commitSession(@RequestBody DmlRequest request) {
        DlExecuteParam param = rdbWebConverter.request2param(request);
        param.setIsErrorExecute(request.getIsErrorExecute());
        param.setIsExecuteCompile(request.getIsExecuteCompile());
        boolean isSuccess= dlTemplateService.commitSession(param);
        return ListResult.of(build(request, isSuccess, "提交"));
    }


    /**
     * 删除无效会话
     * @param request 入参
     * @return List
     */
    @RequestMapping(value = "/delSession", method = {RequestMethod.POST, RequestMethod.PUT})
    public ListResult<ExecuteResultVO> delSession(@RequestBody DmlRequest request) {
        DlExecuteParam param = rdbWebConverter.request2param(request);
        param.setIsErrorExecute(request.getIsErrorExecute());
        param.setIsExecuteCompile(request.getIsExecuteCompile());
        boolean isSuccess = dlTemplateService.delSession(param);
        return ListResult.of(build(request, isSuccess, "删除"));
    }


    /**
     * 执行计划
     * @param request
     * @return
     */
    @RequestMapping(value = "/executeExplain", method = {RequestMethod.POST, RequestMethod.PUT})
    public ListResult<ExecuteResultVO> executeExplain(@RequestBody DmlRequest request) throws SQLException {
        boolean ref = false;
        try {
            if (DBTypeEnum.DM.name().equals(Chat2DBContext.getConnectInfo().getDbType())) {
                request.setSql("EXPLAIN FOR " + request.getSql());
            } else {
                String sql = "EXPLAIN PLAN FOR " + request.getSql();
                ExecuteResult execute = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql, new DefaultValueHandler());
                if (execute == null || !execute.getSuccess()) {
                    throw new BusinessException("ORACLE的执行计划失败");
                }
                ref = true;
                request.setSql("SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY())");
            }
            DlExecuteParam param = rdbWebConverter.request2param(request);
            ListResult<ExecuteResult> resultDTOListResult = dlTemplateService.execute(param);
            return toExecuteResult(resultDTOListResult);
        } catch (SQLException e) {
            String[] split = e.getMessage().split("\n");
            List<String> list = Arrays.asList(split);
            String errorMas = "执行计划失败";
            if (CollUtil.isNotEmpty(list)) {
                errorMas = list.get(0);
            }
            throw new BusinessException(errorMas);
        } finally {
            if (ref) {
                String sql = "SET AUTOTRACE OFF";
                SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql, new DefaultValueHandler());
            }
        }
    }

    /**
     * jdbc执行sql
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/JDBCExecute", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<ExecuteResult> JDBCExecute(@RequestBody DmlRequest request) {
        DlExecuteParam param = rdbWebConverter.request2param(request);
        return dlTemplateService.JDBCExecute(param);
    }


    /**
     * query chat2db apikey
     *
     * @return
     */
    private String getClientId() {
        //ConfigService configService = ApplicationContextUtil.getBean(ConfigService.class);
        Config keyConfig = null;
        if (Objects.isNull(keyConfig) || StringUtils.isBlank(keyConfig.getContent())) {
            return ConfigUtils.getClientId();
        }
        return keyConfig.getContent();
    }

    /**
     * 查询表结构信息
     * @param request
     * @return
     */
    @RequestMapping(value = "/execute_table", method = {RequestMethod.POST, RequestMethod.PUT})
    public ListResult<ExecuteResultVO> executeTable(@RequestBody DmlTableRequest request) {
        DlExecuteParam param = rdbWebConverter.request2param(request);
        return dlTemplateService.executeSelectTable(param)
                .map(rdbWebConverter::dto2vo);
    }

    /**
     * update 查询结果
     * @param request
     * @return
     */
    @Log(title = "数据操作", businessType = BusinessType.EXECUTE_DATA)
    @RequestMapping(value = "/execute_update", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<ExecuteResultVO> executeSelectResultUpdate(@RequestBody DmlRequest request) {
        DlExecuteParam param = rdbWebConverter.request2param(request);
        DataResult<ExecuteResult> result = dlTemplateService.executeUpdate(param);
        if (!result.success()) {
            return DataResult.error(result.getErrorCode(), result.getErrorMessage());
        }
        ExecuteResultVO executeResultVO = rdbWebConverter.dto2vo(result.getData());
        return DataResult.of(executeResultVO);

    }

    @RequestMapping(value = "/get_update_sql", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<String> getUpdateSelectResultSql(@RequestBody SelectResultUpdateRequest request) {
        UpdateSelectResultParam param = rdbWebConverter.request2param(request);
        return dlTemplateService.updateSelectResult(param);
    }


    @RequestMapping(value = "/execute_blob_sql", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<Boolean> executeDataBlobSql(@RequestBody SelectResultUpdateRequest request) {
        UpdateSelectResultParam param = rdbWebConverter.request2param(request);
        return dlTemplateService.executeDataBlobSql(param);
    }



    @RequestMapping(value = "/parseFileType", method = {RequestMethod.POST})
    public DataResult<String> parseFileType(String blobStr) {
        return DataResult.of(dlTemplateService.parseFileType(blobStr));
    }

    /**
     * blob 下载
     * @param exportFile
     * @param response
     */
    @RequestMapping(value = "/exportFile", method = {RequestMethod.POST})
    public void exportFile(@RequestBody exportFile exportFile, HttpServletResponse response) {
        if (StrUtil.isBlank(exportFile.getBlobStr()) || StrUtil.isBlank(exportFile.getFileName()) || StrUtil.isBlank(exportFile.getPrefix())) {
            throw new BusinessException("参数缺失");
        }
        if (!exportFile.getPrefix().contains(".")) {
            throw new BusinessException("文件后缀名异常");
        }
        dlTemplateService.exportFile(exportFile.getBlobStr(), exportFile.getFileName(), exportFile.getPrefix(), response);
    }


    @RequestMapping(value = "/get_order_by_sql", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<String> getOrderBySql(@RequestBody OrderByRequest request) {

        OrderByParam param = rdbWebConverter.request2param(request);

        return dlTemplateService.getOrderBySql(param);
    }

    /**
     * Data operation and maintenance such as addition, deletion, modification and query
     * @param request
     * @return
     */
    @RequestMapping(value = "/execute_ddl", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<ExecuteResultVO> executeDDL(@RequestBody DmlRequest request) {
        DlExecuteParam param = rdbWebConverter.request2param(request);
        Connection connection = Chat2DBContext.getConnection();
        if (connection != null) {
            try {
                boolean flag = true;
                ExecuteResultVO executeResult = null;
                //connection.setAutoCommit(false);
                ListResult<ExecuteResult> resultDTOListResult = dlTemplateService.execute(param);
                List<ExecuteResultVO> resultVOS = rdbWebConverter.dto2vo(resultDTOListResult.getData());
                if (resultVOS == null) {
                    return DataResult.error("execute error", "未返回执行结果");
                }
                if (!CollectionUtils.isEmpty(resultVOS)) {
                    for (ExecuteResultVO resultVO : resultVOS) {
                        if (!resultVO.getSuccess()) {
                            flag = false;
                            executeResult = resultVO;
                            break;

                        }
                    }
                }
                if (flag) {
                    //connection.commit();
                    if (resultVOS.isEmpty()) {
                        return DataResult.error("execute error", "未返回执行结果");
                    }
                    return DataResult.of(resultVOS.get(0));
                } else {
                    //connection.rollback();
                    DataResult<ExecuteResultVO> result = DataResult.of(executeResult);
                    result.setSuccess(false);
                    if (executeResult != null) {
                        result.setErrorMessage(executeResult.getMessage());
                    }
                    return result;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }finally {
                if (connection != null) {
                    try {
                        if (connection != null) {
                            connection.close();
                        }
                    } catch (SQLException e) {
                        log.error("close connection error:{}", e);
                    }
                }
            }

        } else {
            return DataResult.error("connection error", "");
        }
    }

    /**
     * 统计行的数量
     * @param request
     * @return
     */
    @RequestMapping(value = "/count", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<Long> count(@RequestBody DdlCountRequest request) {
        return dlTemplateService.count(rdbWebConverter.request2param(request));
    }



}
