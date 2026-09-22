package com.jd.biz.domain.core.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.PagerUtils;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.fastjson2.JSONObject;
import com.jd.biz.controller.rdb.request.GetBlobRequest;
import com.jd.biz.domain.api.param.DlCountParam;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.param.OrderByParam;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.param.UpdateSelectResultParam;
import com.jd.biz.domain.api.param.operation.OperationLogCreateParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.OperationLogService;
import com.jd.biz.domain.api.service.TableService;
import com.jd.biz.domain.core.converter.CommandConverter;
import com.jd.biz.domain.core.util.MetaNameUtils;
import com.jd.biz.domain.core.util.SqlAuditUtils;
import com.jd.biz.util.FileUtils;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import com.jd.spi.CommandExecutor;
import com.jd.spi.SqlBuilder;
import com.jd.spi.ValueHandler;
import com.jd.spi.enums.DataTypeEnum;
import com.jd.spi.model.BlobSqlResult;
import com.jd.spi.model.Command;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Header;
import com.jd.spi.model.QueryResult;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableIndex;
import com.jd.spi.model.TableIndexColumn;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.sql.SQLExecutor;
import com.jd.spi.util.JdbcUtils;
import com.jd.spi.util.SqlUtils;
import com.jd.system.service.impl.SysDictDataServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 2022年09月23日 15:51 moji Exp $
 * @date 2022/09/23
 */
@Slf4j
@Service
public class DlTemplateServiceImpl implements DlTemplateService {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private TableService tableService;

    @Resource
    private CommandConverter commandConverter;

    @Override
    public ListResult<ExecuteResult> execute(DlExecuteParam param) {
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        Command command = commandConverter.param2model(param);
        command.setIsErrorExecute(param.getIsErrorExecute());
        command.setIsExecuteCompile(param.getIsExecuteCompile());
        long l = System.currentTimeMillis();
        log.info("SESSION_ID" + command.getSessionId());
        List<ExecuteResult> results = executor.execute(command);
        log.info("表数据耗时:{}ms", System.currentTimeMillis() - l);
        return reBuildHeader(results, param.getSchemaName(), param.getDatabaseName(), param.getIsLog(), param.getIsColumn());
    }

    @Override
    public boolean delSession(DlExecuteParam param) {
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        Command command = commandConverter.param2model(param);
        command.setIsErrorExecute(param.getIsErrorExecute());
        command.setIsExecuteCompile(param.getIsExecuteCompile());
        return executor.delSession(command);
    }


    @Override
    public boolean rollbackSession(DlExecuteParam param) {
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        Command command = commandConverter.param2model(param);
        command.setIsErrorExecute(param.getIsErrorExecute());
        command.setIsExecuteCompile(param.getIsExecuteCompile());
        return executor.rollbackSession(command);
    }


    @Override
    public boolean commitSession(DlExecuteParam param) {
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        Command command = commandConverter.param2model(param);
        command.setIsErrorExecute(param.getIsErrorExecute());
        command.setIsExecuteCompile(param.getIsExecuteCompile());
        return executor.commitSession(command);
    }

    @Override
    public boolean cancelExecution(String executionId) {
        return Chat2DBContext.getMetaData().getCommandExecutor().cancel(executionId);
    }

    @Override
    public String getBlobData(GetBlobRequest request) throws SQLException {
        boolean ref = Boolean.FALSE;
        try {
            Integer.parseInt(request.getRowId());
            ref = Boolean.TRUE;
        } catch (Exception ignored) {}
        String sql = null;
        if (ref) {
            sql = "SELECT \"%s\" FROM \"%s\".\"%s\" WHERE ROWID = %s";
        } else {
            sql = "SELECT \"%s\" FROM \"%s\".\"%s\" WHERE ROWID ='%s'";
        }
        ExecuteResult execute = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), String.format(sql, request.getBlobColumn(), request.getSchemaName(), request.getTableName(), request.getRowId()));
        if (execute.getSuccess() && CollUtil.isNotEmpty(execute.getDataList())) {
            List<List<String>> dataList = execute.getDataList();
            List<String> strings = dataList.get(0);
            if (CollUtil.isNotEmpty(strings)) {
                return StrUtil.isNotBlank(strings.get(0)) ? strings.get(0) : null;
            } else {
                return null;
            }
        }
        return null;
    }

    private ListResult<ExecuteResult> reBuildHeader(List<ExecuteResult> results, String schemaName, String databaseName, Boolean isLog, Boolean isColumn) {
        if (results == null) {
            return ListResult.error("execute error", "未返回执行结果");
        }
        ListResult<ExecuteResult> listResult = ListResult.of(results);
        StringBuilder allMessage = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            ExecuteResult executeResult = results.get(i);
            allMessage.append("第").append(i+1).append("条");
            if (executeResult.getSuccess()) {
                allMessage.append(StrUtil.isEmpty(executeResult.getDescription()) ? "查询成功": executeResult.getDescription()).append(";\n");
            } else {
                allMessage.append("执行失败").append(StrUtil.nullToEmpty(executeResult.getMessage()).replace("\n", "")).append(";\n");
            }
        }
        for (ExecuteResult executeResult : results) {
            if(results.size() > 1) {
                executeResult.setSign(results.get(1).getSign());
            }
            executeResult.setAllMessage(allMessage.toString());
            if (!executeResult.getSuccess()) {
                listResult.setSuccess(false);
                listResult.errorCode(executeResult.getDescription());
                listResult.setErrorMessage(executeResult.getMessage());
            }
            List<Header> headers = executeResult.getHeaderList();
            if (CollectionUtil.isNotEmpty(headers)) {
                if (executeResult.getSuccess() && CollectionUtils.isNotEmpty(headers)) {
                    if (Boolean.TRUE.equals(isColumn)) {
                        long l = System.currentTimeMillis();
                        setColumnInfo(headers, executeResult.getTableName(), schemaName, databaseName);
                        log.info("列头耗时:{}ms", System.currentTimeMillis() - l);
                        executeResult.setHeaderList(headers);
                    }
                    List<List<String>> dataList = executeResult.getDataList();
                    List<Header> headerList = executeResult.getHeaderList();
                    // 设置byte为空
                    replaceByteData(dataList, headerList);
                }
            }
            if (Boolean.TRUE.equals(isLog)) {
                addOperationLog(executeResult);
            }
        }
        return listResult;
    }

    private void replaceByteData(List<List<String>> dataList, List<Header> headerList) {
        List<Integer> byteIndex = new ArrayList<>();
        for (int i = 1; i < headerList.size(); i++) {
            if (Objects.nonNull(headerList.get(i)) && DataTypeEnum.BYTE.getCode().equals(headerList.get(i).getDataType())) {
                byteIndex.add(i);
            }
        }
        if (CollUtil.isNotEmpty(byteIndex) && CollUtil.isNotEmpty(dataList)) {
            for (List<String> strings : dataList) {
                if (CollUtil.isNotEmpty(strings)) {
                    for (int i = 0; i < strings.size(); i++) {
                        if (byteIndex.contains(i) && StrUtil.isNotBlank(strings.get(i))) {
                            // 前后端约定
                            strings.set(i, "A");
                        }
                    }
                }
            }
        }
    }

    private ListResult<ExecuteResult> reBuildHeader(List<ExecuteResult> results, String schemaName, String databaseName) {
        if (results == null) {
            return ListResult.error("execute error", "未返回执行结果");
        }
        ListResult<ExecuteResult> listResult = ListResult.of(results);
        for (ExecuteResult executeResult : results) {
            if (!executeResult.getSuccess()) {
                listResult.setSuccess(false);
                listResult.errorCode(executeResult.getDescription());
                listResult.setErrorMessage(executeResult.getMessage());
            }
            List<Header> headers = executeResult.getHeaderList();
            if (CollectionUtil.isNotEmpty(headers)) {
                if (executeResult.getSuccess() && executeResult.isCanEdit() && CollectionUtils.isNotEmpty(headers)) {
                    headers = setColumnInfo(headers, executeResult.getTableName(), schemaName, databaseName);
                    executeResult.setHeaderList(headers);
                }
                addOperationLog(executeResult);
            }
        }
        return listResult;
    }

    @Override
    public ListResult<ExecuteResult> executeSelectTable(DlExecuteParam param) {
        Command command = commandConverter.param2model(param);
        List<ExecuteResult> results = Chat2DBContext.getMetaData().getCommandExecutor().executeSelectTable(command);
        return reBuildHeader(results, param.getSchemaName(), param.getDatabaseName());
    }

    @Override
    public DataResult<ExecuteResult> executeUpdate(DlExecuteParam param) {
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        DataResult<ExecuteResult> dataResult = new DataResult<>();
        dataResult.setSuccess(true);
        //RemoveSpecialGO(param);
        DbType dbType =
                JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        List<String> sqlList = SqlUtils.parse(param.getSql(), dbType);
        Connection connection = Chat2DBContext.getConnection();
        try {
            connection.setAutoCommit(false);
            for (String originalSql : sqlList) {
                ExecuteResult executeResult = executor.executeUpdate(originalSql, connection, 1);
                dataResult.setData(executeResult);
                if (executeResult == null || !Boolean.TRUE.equals(executeResult.getSuccess())) {
                    dataResult.setSuccess(false);
                    dataResult.setErrorCode("execute error");
                    dataResult.setErrorMessage(executeResult == null ? "未返回执行结果" : executeResult.getMessage());
                    connection.rollback();
                    return dataResult;
                }
                addOperationLog(executeResult);
            }
            connection.commit();
        } catch (Exception e) {
            log.error("executeUpdate error", e);
            dataResult.setSuccess(false);
            dataResult.setErrorCode("connection error");
            dataResult.setErrorMessage(e.getMessage());
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    log.error("rollback connection error:{}", rollbackException.getMessage());
                }
            }
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                log.error("close connection error:{}", e);
            }
        }
        return dataResult;
    }

    @Autowired
    @Lazy
    private SysDictDataServiceImpl sysDictDataService;

    @Override
    public String parseFileType(String blobStr) {
        byte[] bytes = Base64.getDecoder().decode(blobStr);
        String blobType = FileUtils.getBlobType(bytes);
        if (StrUtil.isNotBlank(blobType)) {
            return sysDictDataService.selectDictLabel("file_parse_type", blobType);
        }
        return "";
    }

    @Override
    public void exportFile(String blobStr, String fileName, String prefix, HttpServletResponse response) {
        ServletOutputStream outputStream = null;
        try {
            fileName = fileName + prefix;
            byte[] bytes = Base64.getDecoder().decode(blobStr);
            response.setContentType(FileUtils.getContentType(fileName));
            // 设置下载文件名
            response.addHeader("Content-Disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(fileName, CharsetUtil.UTF_8));
            outputStream = response.getOutputStream();
            response.setContentLength(bytes.length);
            outputStream.write(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public DataResult<Long> count(DlCountParam param) {
        if (StringUtils.isBlank(param.getSql())) {
            return DataResult.of(0L);
        }
        DbType dbType =
                JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        String sql = param.getSql();
        // 解析sql分页
        SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
        if (!(sqlStatement instanceof SQLSelectStatement)) {
            throw new BusinessException("dataSource.sqlAnalysisError");
        }
        sql = PagerUtils.count(sql, dbType);
        ExecuteResult executeResult;
        try {
            ValueHandler valueHandler = Chat2DBContext.getMetaData().getValueHandler();
            executeResult = Chat2DBContext.getMetaData().getCommandExecutor().execute(sql, Chat2DBContext.getConnection(), true, null, null,
                    valueHandler);
        } catch (SQLException e) {
            log.warn("执行sql:{}异常", sql, e);
            executeResult = ExecuteResult.builder()
                    .sql(sql)
                    .success(Boolean.FALSE)
                    .message(e.getMessage())
                    .build();
        }

        List<List<String>> dataList = executeResult.getDataList();
        if (CollectionUtils.isEmpty(dataList)) {
            return DataResult.of(0L);
        }
        String count = EasyCollectionUtils.stream(executeResult.getDataList())
                .findFirst()
                .orElse(Collections.emptyList())
                .stream()
                .findFirst()
                .orElse("0");
        return DataResult.of(Long.valueOf(count));
    }

    @Override
    public DataResult<String> updateSelectResult(UpdateSelectResultParam param) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        QueryResult queryResult = new QueryResult();
        BeanUtils.copyProperties(param, queryResult);
        String sql = sqlBuilder.buildSqlByQuery(queryResult);
        return DataResult.of(sql);
    }


    @Override
    public DataResult<Boolean> executeDataBlobSql(UpdateSelectResultParam param) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        QueryResult queryResult = new QueryResult();
        BeanUtils.copyProperties(param, queryResult);
        BlobSqlResult blobSqlResult = sqlBuilder.buildBlobSql(queryResult);
        boolean status = Boolean.FALSE;
        try {
            ExecuteResult executeResult = SQLExecutor.getInstance().executeBlob(Chat2DBContext.getConnection(), blobSqlResult.getDataSql(), blobSqlResult.getBlobValues());
            if (null != executeResult && executeResult.getSuccess()) {
                status = Boolean.TRUE;
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.warn("执行BLOB数据修改或新增失败,数据:{}, blob数:{}", JSONObject.toJSONString(blobSqlResult.getDataSql()), blobSqlResult.getBlobValues().size());
        }
        return DataResult.of(status);
    }

    @Override
    public DataResult<String> getOrderBySql(OrderByParam param) {
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        String orderSql = sqlBuilder.buildOrderBySql(param.getOriginSql(), param.getOrderByList());
        return DataResult.of(orderSql);
    }

    @Override
    public DataResult<ExecuteResult> JDBCExecute(DlExecuteParam param) {
        CommandExecutor executor = Chat2DBContext.getMetaData().getCommandExecutor();
        Command command = commandConverter.param2model(param);
        ExecuteResult results = executor.JDBCExecute(command);
        return DataResult.of(results);
    }

//    @Override
//    public String executeExplain(DmlRequest request) throws SQLException {
//        if (DBTypeEnum.DM.name().equals(Chat2DBContext.getConnectInfo().getDbType())) {
//            request.setSql("EXPLAIN FOR " + request.getSql());
//        } else {
//            request.setSql("EXPLAIN PLAN FOR " + request.getSql());
//        }
//        ExecuteResult execute = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), request.getSql(), new DefaultValueHandler());
//        if (!execute.getSuccess()) {
//            throw new BusinessException("执行计划执行异常");
//        }
//        return "SELECT * FROM PLAN_TABLE";
//    }


    private List<Header> setColumnInfo(List<Header> headers, String tableName, String schemaName, String databaseName) {
        try {
            if (StrUtil.isEmpty(tableName) || (StrUtil.isEmpty(schemaName) && StrUtil.isEmpty(databaseName))) {
                return headers;
            }
            tableName = tableName.replaceAll("\"", "")
                    .replaceAll("`", "")
                    .replaceAll("'", "");
            if (tableName.indexOf(".") > -1) {
                tableName = StrUtil.split(tableName, ".").get(1);
            }
            TableQueryParam tableQueryParam = new TableQueryParam();
            tableQueryParam.setTableName(MetaNameUtils.getMetaName(tableName));
            tableQueryParam.setSchemaName(schemaName);
            tableQueryParam.setDatabaseName(databaseName);
            tableQueryParam.setRefresh(true);
            List<TableColumn> columns = tableService.queryColumns(tableQueryParam);
            if (CollectionUtils.isEmpty(columns)) {
                return headers;
            }
            Map<String, TableColumn> columnMap = columns.stream().collect(
                    Collectors.toMap(TableColumn::getName, tableColumn -> tableColumn));
            List<TableIndex> tableIndices = tableService.queryIndexes(tableQueryParam);
            if (!CollectionUtils.isEmpty(tableIndices)) {
                for (TableIndex tableIndex : tableIndices) {
                    if ("PRIMARY".equalsIgnoreCase(tableIndex.getType())) {
                        List<TableIndexColumn> columnList = tableIndex.getColumnList();
                        if (!CollectionUtils.isEmpty(columnList)) {
                            for (TableIndexColumn tableIndexColumn : columnList) {
                                TableColumn tableColumn = columnMap.get(tableIndexColumn.getColumnName());
                                if (tableColumn != null) {
                                    tableColumn.setPrimaryKey(true);
                                }
                            }
                        }
                    }
                }
            }
            for (Header header : headers) {
                TableColumn tableColumn = columnMap.get(header.getName());
                if (tableColumn != null) {
                    header.setPrimaryKey(tableColumn.getPrimaryKey());
                    //解决达梦自增序列问题
                    header.setAutoIncrement(Validator.isNotEmpty(tableColumn.getAutoIncrement()) && tableColumn.getAutoIncrement() ? 1 : 0);
                    header.setComment(tableColumn.getComment());
                    header.setDefaultValue(tableColumn.getDefaultValue());
                    header.setNullable(tableColumn.getNullable());
                    header.setColumnSize(tableColumn.getColumnSize());
                    header.setDecimalDigits(tableColumn.getDecimalDigits());
                }
            }

        } catch (Exception e) {
            log.error("setColumnInfo error:", e);
        }
        return headers;
    }


    private void addOperationLog(ExecuteResult executeResult) {
        if (executeResult == null) {
            return;
        }
        try {
            ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
            OperationLogCreateParam createParam = new OperationLogCreateParam();
            String auditSql = StrUtil.isNotBlank(executeResult.getOriginalSql())
                    ? executeResult.getOriginalSql()
                    : executeResult.getSql();
            createParam.setDdl(SqlAuditUtils.sanitizeSql(auditSql));
            createParam.setStatus(executeResult.getSuccess() ? "success" : "fail");
            createParam.setDatabaseName(connectInfo.getDatabaseName());
            createParam.setDataSourceId(connectInfo.getDataSourceId());
            createParam.setSchemaName(connectInfo.getSchemaName());
            createParam.setUseTime(executeResult.getDuration());
            createParam.setType(connectInfo.getDbType());
            Integer updateCount = executeResult.getUpdateCount();
            createParam.setOperationRows(updateCount != null && updateCount >= 0
                    ? Long.valueOf(updateCount)
                    : null);
            JSONObject extendInfo = new JSONObject();
            extendInfo.put("sqlType", SqlAuditUtils.resolveSqlType(executeResult.getSqlType(), auditSql));
            String errorMessage = SqlAuditUtils.sanitizeErrorMessage(executeResult.getMessage());
            if (StrUtil.isNotBlank(errorMessage)) {
                extendInfo.put("errorMessage", errorMessage);
            }
            createParam.setExtendInfo(extendInfo.toJSONString());
            operationLogService.create(createParam);
        } catch (Exception e) {
            log.error("addOperationLog error:", e);
        }
    }
}
