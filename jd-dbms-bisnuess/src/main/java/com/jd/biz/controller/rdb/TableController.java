package com.jd.biz.controller.rdb;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Lists;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.*;
import com.jd.biz.controller.rdb.vo.*;
import com.jd.biz.domain.api.enums.TaskStatusEnum;
import com.jd.biz.domain.api.enums.TaskTypeEnum;
import com.jd.biz.domain.api.param.*;
import com.jd.biz.domain.api.service.DatabaseService;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.TableService;
import com.jd.biz.domain.api.service.TaskService;
import com.jd.biz.util.ToolUtil;
import com.jd.common.constant.CacheConstants;
import com.jd.common.core.redis.RedisCache;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.common.tools.common.model.Context;
import com.jd.common.tools.common.model.LoginUser;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.utils.StringUtils;
import com.jd.common.utils.file.FileUtils;
import com.jd.plugin.dm.type.DMIndexTypeEnum;
import com.jd.spi.MetaData;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.*;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.sql.SQLExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@ConnectionInfoAspect
@RequestMapping("/api/rdb/table")
@RestController
public class TableController {

    private static final int BATCH_DELETE_TASK_THRESHOLD = 20;

    @Autowired
    private TableService tableService;

    @Autowired
    private DlTemplateService dlTemplateService;

    @Resource
    private RdbWebConverter rdbWebConverter;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private TaskService taskService;

    public static ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    @Autowired
    private RedisCache redisCache;


    /**
     * 查询当前DB下的表列表
     *
     * @param request
     * @return
     */
    @GetMapping("/list")
    public WebPageResult<TableVO> list(@Valid TableBriefQueryRequest request) {
        long l = System.currentTimeMillis();
        TablePageQueryParam queryParam = rdbWebConverter.tablePageRequest2param(request);
        TableSelector tableSelector = new TableSelector();
        tableSelector.setColumnList(false);
        tableSelector.setIndexList(false);
        PageResult<Table> tableDTOPageResult = tableService.pageQuery(queryParam, tableSelector);
        List<TableVO> tableVOS = new ArrayList<>();
        Long total = 0L;
        if (null != tableDTOPageResult && CollUtil.isNotEmpty(tableDTOPageResult.getData())) {
            tableVOS = rdbWebConverter.tableDto2vo(tableDTOPageResult.getData());
            total = tableDTOPageResult.getTotal();
        }
        long timeMillis = System.currentTimeMillis();
        log.info("查询全表信息耗时:{}MS", timeMillis - l);
//        tableVOS = tableVOS.stream().sorted(Comparator.comparing(TableVO::getName, Collator.getInstance(Locale.ENGLISH))).collect(Collectors.toList());
//        tableVOS = tableVOS.stream().sorted(Comparator.comparing(TableVO::getName, Collator.getInstance(Locale.CHINA))).collect(Collectors.toList());
        return WebPageResult.of(tableVOS, total, request.getPageNo(),
                queryParam.getPageSize());
    }

    /**
     * 查询当前DB下的表列表
     *
     * @param request
     * @return
     */
    @GetMapping("/table_list")
    public ListResult<SimpleTable> tableList(@Valid TableBriefQueryRequest request) {
        TablePageQueryParam queryParam = rdbWebConverter.tablePageRequest2param(request);
        return tableService.queryTables(queryParam);

    }

    /**
     * DDL建表语句
     *
     * @param request
     * @return
     */
    @GetMapping("/createTableSql")
    public DataResult<String> createTableSql(@Valid DdlExportRequest request) {
        ShowCreateTableParam param = rdbWebConverter.ddlExport2showCreate(request);
        return tableService.showCreateTable(param);
    }

    @GetMapping("/compare")
    public DataResult<Map<String, Object>> compare(@Valid TableCompareRequest request) {
        TableQueryParam source = new TableQueryParam();
        source.setDataSourceId(request.getDataSourceId());
        source.setDatabaseName(request.getDatabaseName());
        source.setSchemaName(request.getSchemaName());
        source.setTableName(request.getSourceTableName());
        source.setRefresh(true);
        TableQueryParam target = new TableQueryParam();
        target.setDataSourceId(request.getDataSourceId());
        target.setDatabaseName(request.getDatabaseName());
        target.setSchemaName(request.getSchemaName());
        target.setTableName(request.getTargetTableName());
        target.setRefresh(true);
        return tableService.compareTables(source, target);
    }


    /**
     * 查询当前DB下的表columns
     * d
     *
     * @param request
     * @return
     */
    @GetMapping("/column_list")
    public ListResult<ColumnVO> columnList(@Valid TableDetailQueryRequest request) {
        TableQueryParam queryParam = rdbWebConverter.tableRequest2param(request);
        List<TableColumn> tableColumns = tableService.queryColumns(queryParam);
        List<ColumnVO> tableVOS = rdbWebConverter.columnDto2vo(tableColumns);
        return ListResult.of(tableVOS);
    }

    /**
     * 查询当前DB下的表index
     *
     * @param request
     * @return
     */
    @GetMapping("/index_list")
    public ListResult<IndexVO> indexList(@Valid TableDetailQueryRequest request) {
        TableQueryParam queryParam = rdbWebConverter.tableRequest2param(request);
        List<TableIndex> tableIndices = tableService.queryIndexes(queryParam);
        List<IndexVO> indexVOS = rdbWebConverter.indexDto2vo(tableIndices);
        return ListResult.of(indexVOS);
    }

    /**
     * 查询当前DB下的表key
     *
     * @param request
     * @return
     */
    @GetMapping("/key_list")
    public ListResult<IndexVO> keyList(@Valid TableDetailQueryRequest request) {
        // TODO 增加查询key实现
        return ListResult.of(Lists.newArrayList());
    }

    /**
     * 导出建表语句
     *
     * @param request
     * @return
     */
    @GetMapping("/export")
    public DataResult<String> export(@Valid DdlExportRequest request) {
        ShowCreateTableParam param = rdbWebConverter.ddlExport2showCreate(request);
        return tableService.showCreateTable(param);
    }

    /**
     * 建表语句样例
     *
     * @param request
     * @return
     */
    @GetMapping("/create/example")
    public DataResult<String> createExample(@Valid TableCreateDdlQueryRequest request) {
        return tableService.createTableExample(request.getDbType());
    }

    /**
     * 更新表语句样例
     * @param request
     * @return
     */
    @GetMapping("/update/example")
    public DataResult<String> updateExample(@Valid TableUpdateDdlQueryRequest request) {
        return tableService.alterTableExample(request.getDbType());
    }

    /**
     * 查询源表字段对应的目的表映射的字段
     * @param request
     * @return
     */
    @PostMapping("/columnMapping")
    public ListResult<ColumnWHVO> columnMapping(@RequestBody TableColumnMappingRequest request) {
        List<ColumnWHVO> columnWHVOS = tableService.columnMapping(request);
        return ListResult.of(columnWHVOS);
    }

    /**
     * 获取表下列和索引等信息
     *
     * @param request
     * @return
     */
    @GetMapping("/query")
    public DataResult<Table> query(@Valid TableDetailQueryRequest request) {
        long l = System.currentTimeMillis();
        TableQueryParam queryParam = rdbWebConverter.tableRequest2param(request);
        TableSelector tableSelector = new TableSelector();
        tableSelector.setColumnList(true);
        tableSelector.setIndexList(true);
        DataResult<Table> query = tableService.query(queryParam, tableSelector);
        long timeMillis = System.currentTimeMillis();
        log.info("查询表详情信息耗时:{}ms", timeMillis - l);
        return query;
    }


    /**
     *
     *复制表
     * @param request
     * @return
     */
    @GetMapping("/copyTable")
    public ListResult<ExecuteResultVO> copyTable(@Valid TableDetailQueryRequest request) {
        try {
            if (StrUtil.isEmpty(request.getCopySchemaName())) {
                throw new BusinessException("复制的目的模式缺失");
            }
            String sql = Chat2DBContext.getSqlBuilder().copyTable(request.getCopySchemaName(), request.getTableName(), request.getSchemaName(), request.getIsData());
            DmlRequest request1 = new DmlRequest();
            request1.setSql(sql);
            request1.setDataSourceId(request.getDataSourceId());
            request1.setSchemaName(request.getSchemaName());
            DlExecuteParam param1 = rdbWebConverter.request2param(request1);
            param1.setIsErrorExecute(request1.getIsErrorExecute());
            param1.setIsExecuteCompile(request1.getIsExecuteCompile());
            ListResult<ExecuteResult> resultDTOListResult = dlTemplateService.execute(param1);
            List<ExecuteResultVO> resultVOS = rdbWebConverter.dto2vo(resultDTOListResult.getData());
            return ListResult.of(resultVOS);
        } catch (Exception e) {
            throw  new BusinessException("操作失败");
        }
    }

    /**
     * 获取修改表的sql语句
     *
     * @param request
     * @return
     */
    @PostMapping("/modify/sql")
    public ListResult<SqlVO> modifySql(@Valid @RequestBody TableModifySqlRequest request) {
        Table table = rdbWebConverter.tableRequest2param(request.getNewTable());
        table.setSchemaName(request.getSchemaName());
        table.setDatabaseName(request.getDatabaseName());
        for (TableColumn tableColumn : table.getColumnList()) {
            tableColumn.setSchemaName(request.getSchemaName());
            tableColumn.setTableName(table.getName());
            tableColumn.setDatabaseName(request.getDatabaseName());
        }
        for (TableIndex tableIndex : table.getIndexList()) {
            tableIndex.setSchemaName(request.getSchemaName());
            tableIndex.setTableName(table.getName());
            tableIndex.setDatabaseName(request.getDatabaseName());
        }
        return tableService.buildSql(rdbWebConverter.tableRequest2param(request.getOldTable()), table)
                .map(rdbWebConverter::dto2vo);
    }


    /**
     * 数据库支持的数据类型
     *
     * @param request
     * @return
     */
    @GetMapping("/type_list")
    public ListResult<Type> types(@Valid TypeQueryRequest request) {
        TypeQueryParam typeQueryParam = TypeQueryParam.builder().dataSourceId(request.getDataSourceId()).build();
        List<Type> types = tableService.queryTypes(typeQueryParam);
        return ListResult.of(types);
    }

    /**
     * 数据库支持的数据类型
     * @param request
     * @return
     */
    @GetMapping("/columnDefault")
    public DataResult<String> columnDefault(@Valid TypeQueryRequest request) {
        String defaultValue = tableService.columnDefault(request);
        return DataResult.of(defaultValue);
    }


    /**
     * 数据表的权限
     *
     * @param request
     * @return
     */
    @GetMapping("/queryRoles")
    public ListResult<TableRole> queryRoles(@Valid TypeQueryRequest request) {
        List<TableRole> tableRoles = tableService.queryRoles(request);
        return ListResult.of(tableRoles);
    }

    /**
     * 数据表的被引用情况
     * @param request
     * @return
     */
    @GetMapping("/queryReferenced")
    public DataResult<AssociationTree> queryReferenced(@Valid TypeQueryRequest request) {
//        AssociationTree associationTree = tableService.queryReferenced(request);
        AssociationTree associationTree = tableService.queryReferencedList(request);
        return DataResult.of(associationTree);
    }

    /**
     * 数据视图的被引用情况
     * @param request
     * @return
     */
    @GetMapping("/queryViewReferenced")
    public DataResult<AssociationTree> queryViewReferenced(@Valid TypeQueryRequest request) {
        AssociationTree associationTree = tableService.queryViewReferenced(request);
        return DataResult.of(associationTree);
    }



    @GetMapping("/table_meta")
    public DataResult<TableMeta> tableMeta(@Valid TypeQueryRequest request) {
        TypeQueryParam typeQueryParam = TypeQueryParam.builder().dataSourceId(request.getDataSourceId()).build();
        TableMeta tableMeta = tableService.queryTableMeta(typeQueryParam);
        return DataResult.of(tableMeta);
    }


    @GetMapping("/drop_table_constraint")
    public ActionResult dropConstraint(@Valid TypeQueryRequest request) throws SQLException {
        tableService.dropConstraint(request, Chat2DBContext.getConnection());
        return ActionResult.isSuccess();
    }


    @PostMapping("/createTable_constraint")
    public ActionResult createTableConstraint(@Valid @RequestBody ConstraintInfoRequest request) throws SQLException {
        tableService.createTableConstraint(request);
        return ActionResult.isSuccess();
    }

    /**
     * 删除表
     *
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public ActionResult delete(@Valid @RequestBody TableDeleteRequest request) {
        DropParam dropParam = rdbWebConverter.tableDelete2dropParam(request);
        return tableService.drop(dropParam);
    }

    /**
     * 导入数据库表
     *
     * @param request
     * @return
     */
    @PostMapping("/import")
    public DataResult<TableImportVo> importTable(@Valid @RequestBody TableImportRequest request) {
        return tableService.importTable1(request);
    }

    /**
     * 导入前检查是否正在导入数据库表
     *
     * @param request
     * @return
     */
    @PostMapping("/import/check")
    public ActionResult importCheck(@Valid @RequestBody TableImportRequest request) {
        LoginUser loginUser = ContextUtils.getLoginUser();
        String key = CacheConstants.IMPORT_TABLE_KEY + loginUser.getId() + ":" + StrUtil.nullToEmpty(request.getDatabaseName())
                + ":" + StrUtil.nullToEmpty(request.getSchemaName()) + ":" + StrUtil.nullToEmpty(request.getTableName());
        TableImportVo vo = null;
        if (redisCache.hasKey(key)) {
            vo = redisCache.getCacheObject(key);
        }
        if (Validator.isEmpty(vo)) {
            return ActionResult.isSuccess();
        }
        if (!StrUtil.equals(vo.getStatus(), TaskStatusEnum.FINISH.name()) &&
                !StrUtil.equals(vo.getStatus(), TaskStatusEnum.ERROR.name())) {
            return ActionResult.fail(EasyToolsConstant.ERROR_CODE, "数据库导出未完成。", null);
        }
        redisCache.deleteObject(key);
        FileUtils.deleteFile(vo.getLogFile());
        return ActionResult.isSuccess();
    }

    /**
     * 下载日志文件
     *
     * @param request
     * @param response
     */
    @PostMapping("/import/download")
    public ActionResult importDownload(@Valid @RequestBody TableImportRequest request, HttpServletResponse response) {
        try {
            if (StrUtil.isAllEmpty(request.getDatabaseName(), request.getSchemaName())) {
                throw new RuntimeException("参数异常");
            }
            LoginUser loginUser = ContextUtils.getLoginUser();
            String key = CacheConstants.IMPORT_TABLE_KEY + loginUser.getId() + ":" + StrUtil.nullToEmpty(request.getDatabaseName())
                    + ":" + StrUtil.nullToEmpty(request.getSchemaName()) + ":" + StrUtil.nullToEmpty(request.getTableName());
            TableImportVo vo = null;
            if (redisCache.hasKey(key)) {
                vo = redisCache.getCacheObject(key);
            }
            if (Validator.isEmpty(vo)) {
                return ActionResult.isSuccess();
            }
            if (!StrUtil.equals(vo.getStatus(), TaskStatusEnum.FINISH.name()) &&
                    !StrUtil.equals(vo.getStatus(), TaskStatusEnum.ERROR.name())) {
                return ActionResult.fail(EasyToolsConstant.ERROR_CODE, "数据库导出未完成。", null);
            }
            File file = FileUtil.newFile(vo.getLogFile());
            if (file.exists()) {
                response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                FileUtils.setAttachmentResponseHeader(response, file.getName());
                FileUtils.writeBytes(vo.getLogFile(), response.getOutputStream());
                FileUtils.deleteFile(vo.getLogFile());
                redisCache.deleteObject(key);
            } else {
                return ActionResult.fail(EasyToolsConstant.ERROR_CODE, "文件不存在。", null);
            }
        } catch (Exception e) {
            return ActionResult.fail(EasyToolsConstant.ERROR_CODE, "下载失败。", e.getMessage());
        }
        return ActionResult.isSuccess();
    }

    /**
     * 创建表
     * @return
     */
    @PostMapping("/createTable")
    public ActionResult createTable(@Validated @RequestBody TableRequest request) throws SQLException {
        if(StringUtils.isBlank(request.getName())){
            return ActionResult.fail("表名称不能为空");
        }
        boolean ref = false;
        if(request.getColumnList().size()>0){
            Table table = new Table();
            try {
                BeanUtils.copyProperties(request, table);
                if(null == request.getColumnList()){
                    table.setColumnList(new ArrayList<TableColumn>());
                }
                if(null == table.getIndexList()){
                    table.setIndexList(new ArrayList<TableIndex>());
                    TableIndex tableIndex = new TableIndex();
                    tableIndex.setType(DMIndexTypeEnum.PRIMARY_KEY.getName());
                    tableIndex.setSchemaName(table.getSchemaName());
                    tableIndex.setTableName(table.getName());
                    List<TableIndexColumn> tableIndexColumnList = new ArrayList<>();
                    tableIndex.setColumnList(tableIndexColumnList);
                    //考虑到联合主键
                    for(TableColumn tableColumn:table.getColumnList()){
                        if (tableColumn.getAutoIncrement()) {
                            if (Objects.nonNull(tableColumn.getNullable()) && tableColumn.getNullable().equals(0)) {
                                ref = true;
                                throw new BusinessException("只允许将非空列修改为自增列");
                            }
                        }
                        if(tableColumn.getPrimaryKey()) {
                            tableIndex.setColumn(tableColumn.getName());
                            tableIndex.setName(DMIndexTypeEnum.PRIMARY_KEY.name());
                            TableIndexColumn tableIndexColumn = new TableIndexColumn();
                            tableIndexColumn.setColumnName(tableColumn.getName());
                            tableIndex.getColumnList().add(tableIndexColumn);
                            table.getIndexList().add(tableIndex);
                        }
                     }
                    TableIndex tableIx = new TableIndex();
                    tableIx.setType(DMIndexTypeEnum.UNIQUE.getName());
                    tableIx.setSchemaName(table.getSchemaName());
                    tableIx.setTableName(table.getName());
                    List<TableIndexColumn> tableIxColumnList = new ArrayList<>();
                    tableIx.setColumnList(tableIxColumnList);
                    //考虑到联合唯一键
                    for (TableColumn tableColumn : table.getColumnList()) {
                        if (tableColumn.getUnique()){
                            // 设置唯一键
                            tableIx.setColumn(tableColumn.getName());
                            tableIx.setName("INDEX" + System.currentTimeMillis());
                            tableIx.setType(DMIndexTypeEnum.UNIQUE.getName());
                            TableIndexColumn tableIndexColumn = new TableIndexColumn();
                            tableIndexColumn.setColumnName(tableColumn.getName());
                            tableIx.getColumnList().add(tableIndexColumn);
                            table.getIndexList().add(tableIx);
                        }
                    }
                }
                if (CollUtil.isNotEmpty(table.getIndexList())) {
                    List<TableIndex> collect = table.getIndexList().stream().distinct().collect(Collectors.toList());
                    table.setIndexList(collect);
                }
                String sqlStr = Chat2DBContext.getSqlBuilder().buildCreateTableSql(table);
                String[] sqlArr = sqlStr.split(";");
                for(String sql:sqlArr){
                    if (StringUtils.isNotBlank(sql)){
                        SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(),sql, new DefaultValueHandler());
                    }
                }
                return ActionResult.isSuccess();
            } catch (Exception e) {
                log.warn("创建表失败,异常:{}", e.getMessage());
                if (!e.getMessage().contains("已存在")) {
                    // 创建表失败删除表
                    String sql = Chat2DBContext.getSqlBuilder().deleteTable(table);
                    SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(),sql, new DefaultValueHandler());
                }
                if (ref) {
                    return ActionResult.fail(EasyToolsConstant.ERROR_CODE, e.getMessage() ,e.getMessage());
                } else {
                    return ActionResult.fail(EasyToolsConstant.ERROR_CODE, ExceptionUtil.stacktraceToString(e).split("\\n")[1].replaceAll("\\r", "") ,"创建表失败");
                }
            }
        } else {
            return ActionResult.fail("列信息不能为空");
        }
    }

    /**
     * 删除表
     * @return
     */
    @PostMapping("/dropTable")
    public ActionResult dropTable(@Validated @RequestBody TableRequest request) {
        if (CollectionUtils.isEmpty(request.getTableNames())) {
            return ActionResult.fail("删除的表不能为空");
        }
        try {
            deleteTables(request, request.getTableNames(), null);
            return ActionResult.isSuccess();
        } catch (Exception e) {
            return ActionResult.fail(EasyToolsConstant.ERROR_CODE, "删除表失败.", e.getMessage());
        }
    }

    /**
     * 大批量删除表转为后台任务，避免阻塞页面请求。
     */
    @PostMapping("/dropTableTask")
    public DataResult<Long> dropTableTask(@Validated @RequestBody TableRequest request) {
        if (CollectionUtils.isEmpty(request.getTableNames())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "删除的表不能为空");
        }
        if (request.getTableNames().size() <= BATCH_DELETE_TASK_THRESHOLD) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "仅超过" + BATCH_DELETE_TASK_THRESHOLD + "张表时使用后台删除任务");
        }

        List<String> tableNames = new ArrayList<>(request.getTableNames());
        LoginUser loginUser = ContextUtils.getLoginUser();
        TaskCreateParam task = new TaskCreateParam();
        task.setTaskName("批量删除表（0/" + tableNames.size() + "）");
        task.setTaskType(TaskTypeEnum.DELETE_TABLE.name());
        task.setDataSourceId(request.getDataSourceId());
        task.setDatabaseName(request.getDatabaseName());
        task.setSchemaName(request.getSchemaName());
        task.setUserId(loginUser.getId());
        task.setTaskProgress("0");
        Long taskId = taskService.create(task).getData();

        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();
        CompletableFuture.runAsync(() -> {
            try {
                ContextUtils.setContext(Context.builder().loginUser(loginUser).build());
                Chat2DBContext.putContext(connectInfo);
                updateDeleteTask(taskId, TaskStatusEnum.PROCESSING.name(), 0, tableNames.size());
                deleteTables(request, tableNames, taskId);
                updateDeleteTask(taskId, TaskStatusEnum.FINISH.name(), tableNames.size(), tableNames.size());
            } catch (Exception e) {
                log.error("批量删除表任务失败, taskId={}", taskId, e);
                updateDeleteTask(taskId, TaskStatusEnum.ERROR.name(), 0, tableNames.size(), "删除失败：" + e.getMessage());
            } finally {
                ContextUtils.removeContext();
                Chat2DBContext.removeContext();
            }
        }, singleThreadExecutor);
        return DataResult.of(taskId);
    }

    private void deleteTables(TableRequest request, List<String> tableNames, Long taskId) throws SQLException {
        Table table = new Table();
        BeanUtils.copyProperties(request, table);
        int completed = 0;
        for (String tableName : tableNames) {
            table.setName(tableName);
            String sql = Chat2DBContext.getSqlBuilder().deleteTable(table);
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql, new DefaultValueHandler());
            String cacheKey = CacheConstants.TABLE_INDEX_SQL_ + request.getDataSourceId() + request.getSchemaName() + tableName;
            redisCache.deleteObject(cacheKey);
            completed++;
            if (taskId != null) {
                updateDeleteTask(taskId, TaskStatusEnum.PROCESSING.name(), completed, tableNames.size());
            }
        }
    }

    private void updateDeleteTask(Long taskId, String status, int completed, int total) {
        updateDeleteTask(taskId, status, completed, total, "已删除 " + completed + " / " + total + " 张表");
    }

    private void updateDeleteTask(Long taskId, String status, int completed, int total, String message) {
        TaskUpdateParam update = new TaskUpdateParam();
        update.setId(taskId);
        update.setTaskStatus(status);
        update.setTaskProgress(total == 0 ? "0" : String.valueOf((double) completed / total));
        update.setContent(message.getBytes(StandardCharsets.UTF_8));
        taskService.updateStatus(update);
    }

    @GetMapping("/getTemplateSql")
    public DataResult<String> getTemplateSql(TypeQueryRequest request){
        StringBuilder sb = new StringBuilder();
        sb.append("/* \n");
        sb.append("-- TRUNCATE 语句 \n");
        sb.append("TRUNCATE TABLE \"").append(request.getSchemaName()).append("\".\"").append(request.getTableName()).append("\"\n");
        sb.append("-- SELECT 语句 \n");
        String selectSql = "SELECT * FROM \"" + request.getSchemaName() + "\".\"" + request.getTableName() + "\"\n";
        sb.append(selectSql);
        sb.append("-- DELETE 语句 \n");
        sb.append("DELETE FROM \"").append(request.getSchemaName()).append("\".\"").append(request.getTableName()).append("\"\n");
        sb.append("*/");
        return DataResult.of(sb.toString());
    }


    @GetMapping("/getDebugSql")
    public DataResult<String> getDebugSql(TypeQueryRequest request){
        String selectSql = "I INTEGER; \n";
        String sb = "-- 调试基础 语句 \n" +
                "DECLARE \n" +
                "-- 变量\n" +
                selectSql +
                "BEGIN \n" +
                "-- 调试语句 \n\n" +
                "END";
        return DataResult.of(sb);
    }

}
