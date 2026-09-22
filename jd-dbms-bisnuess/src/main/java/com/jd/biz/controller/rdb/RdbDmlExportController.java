package com.jd.biz.controller.rdb;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.SQLUtils.FormatOption;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement.ValuesClause;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.visitor.VisitorFeature;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.config.ExcelCellWriteHandler;
import com.jd.biz.config.ExcelSheetWriteHandler;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.controller.rdb.request.ExportWordRequest;
import com.jd.biz.controller.rdb.request.TableDataExportRequest;
import com.jd.biz.controller.rdb.vo.WordRealTextVO;
import com.jd.biz.controller.rdb.vo.WordStyleConfigVO;
import com.jd.biz.domain.api.enums.ExportSizeEnum;
import com.jd.biz.domain.api.enums.ExportTypeEnum;
import com.jd.biz.util.DocUtils;
import com.jd.common.annotation.Log;
import com.jd.common.constant.GenConstants;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.common.exception.ParamBusinessException;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import com.jd.common.tools.common.util.EasyEnumUtils;
import com.jd.spi.MetaData;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Header;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import com.jd.spi.util.JdbcUtils;
import com.jd.spi.util.SqlUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Export Database Exclusive
 *
 * @author Jiaju Zhuang
 */
@ConnectionInfoAspect
@RequestMapping("/api/rdb/table")
@RestController
@Slf4j
public class RdbDmlExportController {

    /**
     * Format insert statement
     */
    private static final FormatOption INSERT_FORMAT_OPTION = new FormatOption(true, false);

    static {
        INSERT_FORMAT_OPTION.config(VisitorFeature.OutputNameQuote, true);
    }

    /**
     * export data
     *
     * @param request
     * @return
     */
    @Log(title = "导出管理", businessType = BusinessType.EXPORT)
    @PostMapping("/exportWord")
    public void exportWord(@Valid @RequestBody ExportWordRequest request, HttpServletResponse response) throws Exception {
        ExecuteResult execute = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), request.getWordSql(), new DefaultValueHandler());
        if (!execute.getSuccess() || CollUtil.isEmpty(execute.getHeaderList()) || CollUtil.isEmpty(execute.getDataList())) {
            log.warn("导出wordSql执行失败或数据为空,SQL:{}", request.getWordSql());
            throw new BusinessException("导出wordSql执行失败或数据为空");
        }
        List<String> headerList = execute.getHeaderList().stream().map(Header::getName).collect(Collectors.toList());
        XWPFDocument xwpfDocument = new XWPFDocument();
        WordStyleConfigVO wordStyleConfigDO = new WordStyleConfigVO();
        // 初版 样式信息固定
        wordStyleConfigDO.setFontSize("6");
        wordStyleConfigDO.setIsTitle(GenConstants.ONE);
        wordStyleConfigDO.setTitleType(GenConstants.TWO);
        wordStyleConfigDO.setAlignStyle(String.valueOf(GenConstants.ONE));
        wordStyleConfigDO.setLineSpace(GenConstants.TWO);
        List<List<String>> dataList = execute.getDataList();
        Map<String, List<List<String>>> linkedHashMap = new LinkedHashMap<>();
        for (List<String> list : dataList) {
            if (StrUtil.isBlank(request.getSchemaName()) || !list.get(0).toUpperCase(Locale.ROOT).contains(request.getSchemaName().toUpperCase(Locale.ROOT))) {
                log.warn("模式名为空或sql语句不符合规则,入参:{}", JSON.toJSONString(request));
                throw new BusinessException("模式名为空或sql语句不符合规则");
            }
            List<List<String>> lists = new ArrayList<>();
            if (linkedHashMap.containsKey(list.get(0))) {
                lists = linkedHashMap.get(list.get(0));
            }
            lists.add(list);
            linkedHashMap.put(list.get(0), lists);
        }
        int i = 1;
        for (Map.Entry<String, List<List<String>>> stringListEntry : linkedHashMap.entrySet()) {
            WordRealTextVO wordRealTextDO = new WordRealTextVO();
            wordRealTextDO.setHeaderList(headerList);
            wordRealTextDO.setDataList(stringListEntry.getValue());
            wordRealTextDO.setHeaderInfo("表" + i);
            wordRealTextDO.setName(stringListEntry.getKey());
            DocUtils.generateDocxTable(xwpfDocument, wordStyleConfigDO, wordRealTextDO);
            i++;
        }
        try {
            DocUtils.responseWord(xwpfDocument, response,
                    URLEncoder.encode(LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER), StandardCharsets.UTF_8.toString()) + "_文档");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * export data
     *
     * @param request
     * @return
     */
    @PostMapping("/customExport")
    public void customExport(@RequestBody TableDataExportRequest request, HttpServletResponse response) throws Exception {
        ExportTypeEnum exportType = EasyEnumUtils.getEnum(ExportTypeEnum.class, request.getExportType());
        DbType dbType = JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
//            List<String> tableName = request.getTableNames();
        String tableName = null;
        String table = null;
        String replace = request.getSql();
        List<String> sqlList = new ArrayList<>();
        List<String> tableNames = new ArrayList<>();
        if (dbType != null) {
            if (DBTypeEnum.ORACLE.name().equals(dbType.name().toUpperCase(Locale.ROOT))) {
                replace = request.getSql().replace(";", "");
            }
            SQLStatement sqlStatement = SQLUtils.parseSingleStatement(replace, dbType);
            if (!(sqlStatement instanceof SQLSelectStatement)) {
                throw new BusinessException("dataSource.sqlAnalysisError");
            }
            tableName = SqlUtils.getTableName(replace, dbType).replaceAll("\"","");
            table = Chat2DBContext.getMetaData().getMetaDataName(request.getDatabaseName(), request.getSchemaName(),
                    tableName);
        } else {
            tableName = StringUtils.join(Lists.newArrayList(request.getDatabaseName(), request.getSchemaName()), "_");
        }

        response.setCharacterEncoding("utf-8");
        String fileName = tableName + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.PURE_DATETIME_PATTERN))
                .replaceAll("\\+", "%20");
        sqlList.add(replace);
        tableNames.add(tableName);
        if (exportType == ExportTypeEnum.CSV || exportType == ExportTypeEnum.EXCEL) {
            doExportCsv(replace, response, fileName, exportType.getCode());
        }
    }

    /**
     * export data
     *
     * @param request
     * @return
     */
    @Log(title = "导出管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@Valid @RequestBody TableDataExportRequest request, HttpServletResponse response) throws Exception {
        if (CollUtil.isNotEmpty(request.getDataList())
                && request.getDataList().size() > EasyToolsConstant.MAX_EXPORT_SIZE) {
            throw new BusinessException("单次最多导出 " + EasyToolsConstant.MAX_EXPORT_SIZE + " 行数据");
        }
        ExportSizeEnum exportSize = EasyEnumUtils.getEnum(ExportSizeEnum.class, request.getExportSize());
        ExportTypeEnum exportType = EasyEnumUtils.getEnum(ExportTypeEnum.class, request.getExportType());
        DbType dbType = JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        String tableName = request.getExportTableName();
        String table = request.getExportTableName();
        response.setCharacterEncoding("utf-8");
        String fileName = tableName + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                .replaceAll("\\+", "%20");
        if (CollUtil.isEmpty(request.getDataList())) {
            String filter = StrUtil.isNotBlank(request.getFilterSql()) ? " where " + request.getFilterSql() : "";
            String sql = "select * from " + request.getExportTableName() + filter;
            if (StrUtil.isNotBlank(request.getFilterColumn())) {
                String desc = "";
                if (Boolean.TRUE.equals(request.getIsDesc())) {
                    desc = String.format(" ORDER BY \"%s\" DESC", request.getFilterColumn());
                } else {
                    desc = String.format(" ORDER BY \"%s\" ASC", request.getFilterColumn());
                }
                sql = sql + desc;
            }
            if (exportSize == ExportSizeEnum.CURRENT_PAGE) {
                int offset = (Validator.isNotEmpty(request.getPageNum()) ? request.getPageNum() - 1 : 0) * (Validator.isNotEmpty(request.getPageSize()) ? request.getPageSize() :
                        EasyToolsConstant.MAX_EXPORT_SIZE);
                sql = Chat2DBContext.getSqlBuilder().pageLimit(sql, offset, request.getPageNum(), request.getPageSize());
            }
            if (exportSize == ExportSizeEnum.CHOOSE) {
                if (request.getExportPrimaryKeyList().size() > 0) {
                    List<Map<String, Object>> multiPrimaryKeyList = request.getExportPrimaryKeyList();
                    Map<String, Object> stringObjectMap = multiPrimaryKeyList.get(0);
                    String collect = String.join("||\'-\'||", stringObjectMap.keySet());
                    List<String> dataList = new ArrayList<>();
                    for (int i = 0; i < multiPrimaryKeyList.size(); i++) {
                        StringBuilder data = new StringBuilder();
                        data.append("'");
                        for (String key : stringObjectMap.keySet()) {
                            data = data.append(multiPrimaryKeyList.get(i).get(key) + "-");
                        }
                        dataList.add(data.deleteCharAt(data.length() - 1).append("'").toString());
                    }
                    String collect1 = String.join(",", dataList);
                    sql = sql + (StrUtil.isNotEmpty(request.getFilterSql()) ? " and " : " where ") + collect + " in (" + collect1 + ")";
                } else {
                    throw new ParamBusinessException("exportSize");
                }
            }
            if (dbType != null) {
                SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
                if (!(sqlStatement instanceof SQLSelectStatement)) {
                    throw new BusinessException("dataSource.sqlAnalysisError");
                }
                tableName = SqlUtils.getTableName(sql, dbType).replaceAll("\"", "");
                table = Chat2DBContext.getMetaData().getMetaDataName(request.getDatabaseName(), request.getSchemaName(),
                        tableName);
            } else {
                tableName = StringUtils.join(Lists.newArrayList(request.getDatabaseName(), request.getSchemaName()), "_");
            }
            fileName = tableName + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                    .replaceAll("\\+", "%20");

            if (exportType == ExportTypeEnum.CSV || exportType == ExportTypeEnum.EXCEL) {
                doExportCsv(sql, response, fileName, exportType.getCode());
            } else {
                doExportInsert(sql, response, fileName, dbType, table);
            }
        } else {
            if (exportType == ExportTypeEnum.CSV || exportType == ExportTypeEnum.EXCEL) {
                doExportCsv(request.getDataList(), response, fileName, exportType.getCode());
            } else {
                doExportInsert(request.getDataList(), response, fileName, dbType, table);
            }
        }
    }

    /**
     * export data
     *
     * @param request
     * @return
     */
    @Log(title = "导出DDL", businessType = BusinessType.EXPORT)
    @PostMapping("/exportDDL")
    public void exportDDL(@RequestBody TableDataExportRequest request, HttpServletResponse response) throws Exception {
        String tableName = "";
        String schemaName = "";
        if(StrUtil.isNotBlank(request.getSchemaName()) && StrUtil.isNotBlank(request.getTableName())){
            tableName = request.getTableName();
            schemaName = request.getSchemaName();
        }else{
            throw new BusinessException("参数为空");
        }
        response.setCharacterEncoding("utf-8");
        String fileName = tableName + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                .replaceAll("\\+", "%20");
        response.setContentType("text/sql");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(fileName + ".sql", CharsetUtil.UTF_8));
        MetaData metaSchema = Chat2DBContext.getMetaData();
        try (PrintWriter printWriter = response.getWriter()) {
            String dropSql = Chat2DBContext.getSqlBuilder().dropTableSql(schemaName, tableName);
            String ddl = metaSchema.tableDDL(Chat2DBContext.getConnection(), request.getDatabaseName(), schemaName, tableName);
            String sql = dropSql + ";\n" +ddl;
            printWriter.println(sql);
        }
    }


    /**
     * export data
     *
     * @param request
     * @return
     */
    @Log(title = "导出视图语句", businessType = BusinessType.EXPORT)
    @PostMapping("/exportViewDDL")
    public void exportViewDDL(@RequestBody TableDataExportRequest request, HttpServletResponse response) throws Exception {
        if (StrUtil.isBlank(request.getSql())) {
            throw new BusinessException("参数缺失");
        }
        response.setCharacterEncoding("utf-8");
        String fileName = LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                .replaceAll("\\+", "%20");
        response.setContentType("text/sql");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(fileName + ".sql", CharsetUtil.UTF_8));
        MetaData metaSchema = Chat2DBContext.getMetaData();
        try (PrintWriter printWriter = response.getWriter()) {
            String sql = request.getSql();
            printWriter.println(sql);
        }
    }

    @PostMapping("/exportSql")
    public AjaxResult exportSql(@Valid @RequestBody TableDataExportRequest request, HttpServletResponse response) {
        ExportSizeEnum exportSize = EasyEnumUtils.getEnum(ExportSizeEnum.class, request.getExportSize());
        String filter = StrUtil.isNotBlank(request.getFilterSql()) ? " where " + request.getFilterSql() : "";
        String sql = "select * from " + request.getExportTableName() + filter;


        if (exportSize == ExportSizeEnum.CURRENT_PAGE) {
            int offset = (Validator.isNotEmpty(request.getPageNum()) ? request.getPageNum() - 1 : 1 - 1) * (Validator.isNotEmpty(request.getPageSize()) ? request.getPageSize() :
                    EasyToolsConstant.MAX_EXPORT_SIZE);
            sql = Chat2DBContext.getSqlBuilder().pageLimit(sql, offset, request.getPageNum(), request.getPageSize());
        }
        if (exportSize == ExportSizeEnum.CHOOSE) {
            if (request.getExportPrimaryKeyList().size() > 0) {
//                if (request.getIsMultiPrimaryKey()){
//                    List<String> res = new ArrayList<>();
//                    for (List<String> list:request.getMultiPrimaryKeyList()){
//                        res.add(list.stream().collect(Collectors.joining("-")));
//                    }
//                    request.setExportIdList(res);
//                    request.setColumnName(request.getColumnList().stream().collect(Collectors.joining("||-||")));
//                }
//
//                String collect = request.getExportIdList().stream().collect(Collectors.joining(","));
//                sql = sql + (StrUtil.isNotEmpty(request.getFilterSql())?" and ":" where " )+ request.getColumnName() +" in (" + collect + ")";
                List<Map<String, Object>> multiPrimaryKeyList = request.getExportPrimaryKeyList();
                Map<String, Object> stringObjectMap = multiPrimaryKeyList.get(0);
                String collect = String.join("||-||", stringObjectMap.keySet());
                List<String> dataList = new ArrayList<>();
                for (int i = 0; i < multiPrimaryKeyList.size(); i++) {
                    StringBuilder data = new StringBuilder();
                    for (String key : stringObjectMap.keySet()) {
                        data = data.append(multiPrimaryKeyList.get(i).get(key) + "-");
                    }
                    dataList.add(data.deleteCharAt(data.length() - 1).toString());
                }
                String collect1 = String.join("-", dataList);
                sql = sql + (StrUtil.isNotEmpty(request.getFilterSql()) ? " and " : " where ") + collect + " in (" + collect1 + ")";
            } else {
                throw new ParamBusinessException("exportSize");
            }
        }
//        if (StringUtils.isBlank(sql)) {
//            throw new ParamBusinessException("exportSize");
//        }
        DbType dbType = JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        String tableName;
        String table = null;
        if (dbType != null) {
            SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sql, dbType);
            if (!(sqlStatement instanceof SQLSelectStatement)) {
                throw new BusinessException("dataSource.sqlAnalysisError");
            }
            tableName = SqlUtils.getTableName(sql, dbType).replaceAll("\"", "");
            table = Chat2DBContext.getMetaData().getMetaDataName(request.getDatabaseName(), request.getSchemaName(),
                    tableName);
        }
        String tab = table;
        InsertWrapper insertWrapper = new InsertWrapper();
        List<String> list = new ArrayList<>();
        SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql,
                headerList -> insertWrapper.setHeaderList(
                        EasyCollectionUtils.toList(headerList, header -> new SQLIdentifierExpr(header.getName())))
                , dataList -> {
                    SQLInsertStatement sqlInsertStatement = new SQLInsertStatement();
                    sqlInsertStatement.setDbType(dbType);
                    sqlInsertStatement.setTableSource(new SQLExprTableSource(tab));
                    sqlInsertStatement.getColumns().addAll(insertWrapper.getHeaderList());
                    ValuesClause valuesClause = new ValuesClause();
                    for (String s : dataList) {
                        valuesClause.addValue(s);
                    }
                    sqlInsertStatement.setValues(valuesClause);

                    list.add(SQLUtils.toSQLString(sqlInsertStatement, dbType, INSERT_FORMAT_OPTION) + ";");
                }, false, new DefaultValueHandler());
        return AjaxResult.success(list);
    }


    private void doExportCsv(String sql, HttpServletResponse response, String fileName, String exportType)
            throws Exception {
        response.setHeader("X-Export-Row-Limit", String.valueOf(EasyToolsConstant.MAX_EXPORT_SIZE));
        ExportTypeEnum exportType1 = EasyEnumUtils.getEnum(ExportTypeEnum.class, exportType);
        ExcelWrapper excelWrapper = new ExcelWrapper();
        ExcelWriterBuilder excelWriterBuilder = new ExcelWriterBuilder();
        try {
            if (exportType1 == ExportTypeEnum.CSV) {
                response.setContentType("text/csv");
                response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(fileName + ".csv", CharsetUtil.UTF_8));
                excelWriterBuilder = EasyExcel.write(response.getOutputStream())
                        .charset(StandardCharsets.UTF_8)
                        .excelType(ExcelTypeEnum.CSV);
            }
            if (exportType1 == ExportTypeEnum.EXCEL) {
                response.setContentType("application/vnd.ms-excel");
                response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(fileName + ".xlsx", CharsetUtil.UTF_8));
                excelWriterBuilder = EasyExcel.write(response.getOutputStream())
                        .charset(StandardCharsets.UTF_8)
                        .excelType(ExcelTypeEnum.XLSX)
                        .registerWriteHandler(new ExcelCellWriteHandler());

            }
            ExcelWriterBuilder WriterBuilder = excelWriterBuilder;
            excelWrapper.setExcelWriterBuilder(WriterBuilder);
            ExecuteResult execute = SQLExecutor.getInstance().execute(sql, Chat2DBContext.getConnection(), true,
                    0, EasyToolsConstant.MAX_EXPORT_SIZE, new DefaultValueHandler());
            WriterBuilder.registerWriteHandler(new ExcelSheetWriteHandler(execute.getHeaderList().size()));
            List<List<String>> arrayLists = EasyCollectionUtils.toList(execute.getHeaderList(), header -> Lists.newArrayList(header.getName()));
            WriterBuilder.head(arrayLists);
            excelWrapper.setExcelWriter(WriterBuilder.build());
            excelWrapper.setWriteSheet(EasyExcel.writerSheet(0).build());
            List<List<String>> writeDataList = Lists.newArrayList();
            writeDataList.addAll(execute.getDataList());
            excelWrapper.getExcelWriter().write(writeDataList, excelWrapper.getWriteSheet());
        } finally {
            if (excelWrapper.getExcelWriter() != null) {
                excelWrapper.getExcelWriter().finish();
            }
        }
    }


    private void doExportCsv(List<Map<String, String>> dataList, HttpServletResponse response, String fileName, String exportType)
            throws Exception {
        response.setHeader("X-Export-Row-Limit", String.valueOf(EasyToolsConstant.MAX_EXPORT_SIZE));
        ExportTypeEnum exportType1 = EasyEnumUtils.getEnum(ExportTypeEnum.class, exportType);
        ExcelWrapper excelWrapper = new ExcelWrapper();
        ExcelWriterBuilder excelWriterBuilder = new ExcelWriterBuilder();
        try {
            if (exportType1 == ExportTypeEnum.CSV) {
                response.setContentType("text/csv");
                response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(fileName + ".csv", CharsetUtil.UTF_8));
                excelWriterBuilder = EasyExcel.write(response.getOutputStream())
                        .charset(StandardCharsets.UTF_8)
                        .excelType(ExcelTypeEnum.CSV);
            }
            if (exportType1 == ExportTypeEnum.EXCEL) {
                response.setContentType("application/vnd.ms-excel");
                response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(fileName + ".xlsx", CharsetUtil.UTF_8));
                excelWriterBuilder = EasyExcel.write(response.getOutputStream())
                        .charset(StandardCharsets.UTF_8)
                        .excelType(ExcelTypeEnum.XLSX)
                        .useDefaultStyle(Boolean.TRUE)
                        .registerWriteHandler(new ExcelCellWriteHandler());
            }
            ExcelWriterBuilder WriterBuilder = excelWriterBuilder;
            excelWrapper.setExcelWriterBuilder(WriterBuilder);
            Set<String> headerList = new HashSet<>();
            List<List<String>> datas = new ArrayList<>();
            for (Map<String, String> map : dataList) {
                headerList = map.keySet();
                datas.add(new ArrayList<>(map.values()));
            }
            WriterBuilder.head(EasyCollectionUtils.toList(headerList, Lists::newArrayList));
            WriterBuilder.registerWriteHandler(new ExcelSheetWriteHandler(headerList.size()));
            excelWrapper.setExcelWriter(WriterBuilder.build());
            excelWrapper.setWriteSheet(EasyExcel.writerSheet(0).build());
            List<List<String>> writeDataList = Lists.newArrayList();
            writeDataList.addAll(datas);
            excelWrapper.getExcelWriter().write(writeDataList, excelWrapper.getWriteSheet());
        } finally {
            if (excelWrapper.getExcelWriter() != null) {
                excelWrapper.getExcelWriter().finish();
            }
        }
    }

    private void doExportInsert(String sql, HttpServletResponse response, String fileName, DbType dbType,
                                String tableName)
            throws Exception {
        response.setHeader("X-Export-Row-Limit", String.valueOf(EasyToolsConstant.MAX_EXPORT_SIZE));
        response.setContentType("text/sql");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(fileName + ".sql", CharsetUtil.UTF_8));
        try (PrintWriter printWriter = response.getWriter()) {
            InsertWrapper insertWrapper = new InsertWrapper();
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql,
                    headerList -> insertWrapper.setHeaderList(
                            EasyCollectionUtils.toList(headerList, header -> new SQLIdentifierExpr(header.getName())))
                    , dataList -> {
                        SQLInsertStatement sqlInsertStatement = new SQLInsertStatement();
                        sqlInsertStatement.setDbType(dbType);
                        sqlInsertStatement.setTableSource(new SQLExprTableSource(tableName));
                        sqlInsertStatement.getColumns().addAll(insertWrapper.getHeaderList());
                        ValuesClause valuesClause = new ValuesClause();
                        for (String s : dataList) {
                            valuesClause.addValue(s);
                        }
                        sqlInsertStatement.setValues(valuesClause);
                        printWriter.println(SQLUtils.toSQLString(sqlInsertStatement, dbType, INSERT_FORMAT_OPTION) + ";");
                    }, false, EasyToolsConstant.MAX_EXPORT_SIZE, new DefaultValueHandler());
        }
    }


    private void doExportInsert(List<Map<String, String>> dataList, HttpServletResponse response, String fileName, DbType dbType,
                                String tableName)
            throws Exception {
        response.setHeader("X-Export-Row-Limit", String.valueOf(EasyToolsConstant.MAX_EXPORT_SIZE));
        Set<String> headerList = new HashSet<>();
        List<List<String>> datas = new ArrayList<>();
        for (Map<String, String> map : dataList) {
            headerList = map.keySet();
            datas.add(new ArrayList<>(map.values()));
        }
        response.setContentType("text/sql");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(fileName + ".sql", CharsetUtil.UTF_8));
        try (PrintWriter printWriter = response.getWriter()) {
            InsertWrapper insertWrapper = new InsertWrapper();
            insertWrapper.setHeaderList(EasyCollectionUtils.toList(headerList, SQLIdentifierExpr::new));
            SQLInsertStatement sqlInsertStatement = new SQLInsertStatement();
            sqlInsertStatement.setDbType(dbType);
            sqlInsertStatement.setTableSource(new SQLExprTableSource(tableName));
            sqlInsertStatement.getColumns().addAll(insertWrapper.getHeaderList());
            for (List<String> data : datas) {
                ValuesClause valuesClause = new ValuesClause();
                for (String s : data) {
                    valuesClause.addValue(s);
                }
                sqlInsertStatement.setValues(valuesClause);
                printWriter.println(SQLUtils.toSQLString(sqlInsertStatement, dbType, INSERT_FORMAT_OPTION) + ";");
            }
        }
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsertWrapper {
        private List<SQLIdentifierExpr> headerList;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExcelWrapper {
        private ExcelWriterBuilder excelWriterBuilder;
        private ExcelWriter excelWriter;
        private WriteSheet writeSheet;
    }

}
