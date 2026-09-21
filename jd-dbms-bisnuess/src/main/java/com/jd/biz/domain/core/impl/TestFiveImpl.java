package com.jd.biz.domain.core.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.core.util.MetaNameUtils;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.Command;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Header;
import com.jd.spi.model.TableColumn;
import com.jd.spi.model.TableIndex;
import com.jd.spi.model.TableIndexColumn;
import com.jd.spi.sql.Chat2DBContext;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TestFiveImpl {

    @Autowired
    private DlTemplateServiceImpl dlTemplateService;
    public ListResult<ExecuteResult> getTable(DlExecuteParam param) {
        return dlTemplateService.executeSelectTable(param);
//        List<ExecuteResult> results = Chat2DBContext.getMetaData().getCommandExecutor().executeSelectTable(command);
//        return reBuildHeader(results, param.getSchemaName(), param.getDatabaseName());
    }

//    private ListResult<ExecuteResult> reBuildHeader(List<ExecuteResult> results, String schemaName, String databaseName) {
//        ListResult<ExecuteResult> listResult = ListResult.of(results);
//        for (ExecuteResult executeResult : results) {
//            List<Header> headers = executeResult.getHeaderList();
//            if (CollectionUtil.isNotEmpty(headers)) {
//                if (executeResult.getSuccess() && executeResult.isCanEdit() && CollectionUtils.isNotEmpty(headers)) {
//                    headers = setColumnInfo(headers, executeResult.getTableName(), schemaName, databaseName);
//                    executeResult.setHeaderList(headers);
//                }
//                if (!executeResult.getSuccess()) {
//                    listResult.setSuccess(false);
//                    listResult.errorCode(executeResult.getDescription());
//                    listResult.setErrorMessage(executeResult.getMessage());
//                }
//                addOperationLog(executeResult);
//            }
//        }
//        return listResult;
//    }
//
//    private List<Header> setColumnInfo(List<Header> headers, String tableName, String schemaName, String databaseName) {
//        try {
//            if (StrUtil.isEmpty(tableName) || (StrUtil.isEmpty(schemaName) && StrUtil.isEmpty(databaseName))) {
//                return headers;
//            }
//            tableName = tableName.replaceAll("\"", "")
//                    .replaceAll("`", "")
//                    .replaceAll("'", "");
//            if (tableName.indexOf(".") > -1) {
//                tableName = StrUtil.split(tableName, ".").get(1);
//            }
//            TableQueryParam tableQueryParam = new TableQueryParam();
//            tableQueryParam.setTableName(MetaNameUtils.getMetaName(tableName));
//            tableQueryParam.setSchemaName(schemaName);
//            tableQueryParam.setDatabaseName(databaseName);
//            tableQueryParam.setRefresh(true);
//            List<TableColumn> columns = tableService.queryColumns(tableQueryParam);
//            if (CollectionUtils.isEmpty(columns)) {
//                return headers;
//            }
//            Map<String, TableColumn> columnMap = columns.stream().collect(
//                    Collectors.toMap(TableColumn::getName, tableColumn -> tableColumn));
//            List<TableIndex> tableIndices = tableService.queryIndexes(tableQueryParam);
//            if (!CollectionUtils.isEmpty(tableIndices)) {
//                for (TableIndex tableIndex : tableIndices) {
//                    if ("PRIMARY".equalsIgnoreCase(tableIndex.getType())) {
//                        List<TableIndexColumn> columnList = tableIndex.getColumnList();
//                        if (!CollectionUtils.isEmpty(columnList)) {
//                            for (TableIndexColumn tableIndexColumn : columnList) {
//                                TableColumn tableColumn = columnMap.get(tableIndexColumn.getColumnName());
//                                if (tableColumn != null) {
//                                    tableColumn.setPrimaryKey(true);
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            for (Header header : headers) {
//                TableColumn tableColumn = columnMap.get(header.getName());
//                if (tableColumn != null) {
//                    header.setPrimaryKey(tableColumn.getPrimaryKey());
//                    //解决达梦自增序列问题
//                    header.setAutoIncrement(Validator.isNotEmpty(tableColumn.getAutoIncrement()) && tableColumn.getAutoIncrement() ? 1 : 0);
//                    header.setComment(tableColumn.getComment());
//                    header.setDefaultValue(tableColumn.getDefaultValue());
//                    header.setNullable(tableColumn.getNullable());
//                    header.setColumnSize(tableColumn.getColumnSize());
//                    header.setDecimalDigits(tableColumn.getDecimalDigits());
//                }
//            }
//
//        } catch (Exception e) {
//            log.error("setColumnInfo error:", e);
//        }
//        return headers;
//    }
}
