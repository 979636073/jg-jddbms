package com.jd.biz.domain.core.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.visitor.VisitorFeature;
import com.google.common.collect.Lists;
import com.jd.biz.controller.ncx.cipher.CommonCipher;
import com.jd.biz.controller.rdb.RdbDmlExportController;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.controller.rdb.request.DatabaseExportRequest;
import com.jd.biz.controller.rdb.request.DatabaseImportRequest;
import com.jd.biz.controller.rdb.request.DmpExportRequest;
import com.jd.biz.controller.rdb.request.DmpImportRequest;
import com.jd.biz.controller.rdb.vo.DatabaseExportVo;
import com.jd.biz.controller.rdb.vo.DatabaseImportVo;
import com.jd.biz.domain.api.enums.TaskStatusEnum;
import com.jd.biz.domain.api.enums.TaskTypeEnum;
import com.jd.biz.domain.api.param.MetaDataQueryParam;
import com.jd.biz.domain.api.param.SchemaOperationParam;
import com.jd.biz.domain.api.param.SchemaQueryParam;
import com.jd.biz.domain.api.param.datasource.DatabaseCreateParam;
import com.jd.biz.domain.api.param.datasource.DatabaseExportParam;
import com.jd.biz.domain.api.param.datasource.DatabaseQueryAllParam;
import com.jd.biz.domain.api.param.TaskCreateParam;
import com.jd.biz.domain.api.param.TaskUpdateParam;
import com.jd.biz.domain.api.service.DatabaseService;
import com.jd.biz.domain.api.service.TaskService;
import com.jd.biz.domain.core.cache.CacheManage;
import com.jd.biz.domain.repository.entity.DataSourceDO;
import com.jd.biz.domain.repository.mapper.DataSourceMapper;
import com.jd.biz.util.OSUtils;
import com.jd.common.config.HzbConfig;
import com.jd.common.constant.CacheConstants;
import com.jd.common.constant.Constants;
import com.jd.common.core.redis.RedisCache;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.common.model.Context;
import com.jd.common.tools.common.model.LoginUser;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import com.jd.common.utils.file.FileUtils;
import com.jd.spi.MetaData;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.*;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.sql.SQLExecutor;
import com.jd.spi.util.JdbcUtils;
import com.jd.spi.util.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static com.jd.biz.domain.core.cache.CacheKey.*;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 2022年09月23日 15:51 moji Exp $
 * @date 2022/09/23
 */
@Slf4j
@Service
@SuppressWarnings("ALL")
public class DatabaseServiceImpl implements DatabaseService {


    /**
     * Format insert statement
     */
    private static final SQLUtils.FormatOption INSERT_FORMAT_OPTION = new SQLUtils.FormatOption(true, false);

    static {
        INSERT_FORMAT_OPTION.config(VisitorFeature.OutputNameQuote, true);
    }

    @Autowired
    private TableServiceImpl tableServiceImpl;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private TaskService taskService;

    @Value("${exportDmp.dm}")
    private String dmDmpExport;

    @Value("${importDmp.dm}")
    private String dmDmpImport;


    @Value("${dmp.dm.user}")
    private String dmpDmUser;

    @Value("${dmp.dm.password}")
    private String dmpDmPassword;

    @Value("${dmp.oracle.user}")
    private String dmpOracleUser;

    @Value("${dmp.oracle.password}")
    private String dmpOraclePassword;

    @Value("${exportDmp.oracle}")
    private String oraDmpExport;

    @Value("${importDmp.oracle}")
    private String oraDmpImport;


    @Autowired
    private DataSourceMapper dataSourceMapper;


    @Override
    public ListResult<Database> queryAll(DatabaseQueryAllParam param) {
        List<Database> databases = CacheManage.getList(getDataBasesKey(param.getDataSourceId()), Database.class,
                (key) -> param.isRefresh(),
                (key) -> getDatabases(param.getDbType(), param.getConnection() == null ? Chat2DBContext.getConnection()
                        : param.getConnection())
        );
        return ListResult.of(databases);
    }

    private List<Database> getDatabases(String dbType, Connection connection) {
        return Chat2DBContext.getMetaData(dbType).databases(connection);
    }

    @Override
    public ListResult<Schema> querySchema(SchemaQueryParam param) {
        List<Schema> schemas = CacheManage.getList(getSchemasKey(param.getDataSourceId(), param.getDataBaseName()),
                Schema.class,
                (key) -> param.getRefresh(), (key) -> {
                    Connection connection = param.getConnection() == null ? Chat2DBContext.getConnection()
                            : param.getConnection();
                    return getSchemaList(param.getDataBaseName(), connection);
                });
        return ListResult.of(schemas);
    }


    private List<Schema> getSchemaList(String databaseName, Connection connection) {
        MetaData metaData = Chat2DBContext.getMetaData();
        List<Schema> schemas = metaData.schemas(connection, databaseName);
//        sortSchema(schemas, connection);
        return schemas;
    }

    private void sortSchema(List<Schema> schemas, Connection connection) {
        if (CollectionUtils.isEmpty(schemas)) {
            return;
        }
        String ulr = null;
        try {
            ulr = connection.getMetaData().getURL();
        } catch (SQLException e) {
            log.error("get url error", e);
        }
        // If the database name contains the name of the current database, the current database is placed in the first place
        int num = -1;
        for (int i = 0; i < schemas.size(); i++) {
            String schema = schemas.get(i).getName();
            if (StringUtils.isNotBlank(ulr) && schema != null && ulr.contains(schema)) {
                num = i;
                break;
            }
        }
        if (num != -1 && num != 0) {
            Collections.swap(schemas, num, 0);
        }
    }

    @Override
    public DataResult<MetaSchema> queryDatabaseSchema(MetaDataQueryParam param) {
        MetaSchema metaSchema = new MetaSchema();
        MetaData metaData = Chat2DBContext.getMetaData();
        MetaSchema ms = CacheManage.get(getDataSourceKey(param.getDataSourceId()), MetaSchema.class,
                (key) -> param.isRefresh(), (key) -> {
                    Connection connection = Chat2DBContext.getConnection();
                    List<Database> databases = metaData.databases(connection);
                    if (!CollectionUtils.isEmpty(databases)) {
                        CountDownLatch countDownLatch = ThreadUtil.newCountDownLatch(databases.size());
                        for (Database database : databases) {
                            ThreadUtil.execute(() -> {
                                try {
                                    database.setSchemas(metaData.schemas(connection, database.getName()));
                                    countDownLatch.countDown();
                                } catch (Exception e) {
                                    log.error("queryDatabaseSchema error", e);
                                }
                            });
                        }
                        try {
                            countDownLatch.await();
                        } catch (InterruptedException e) {
                            log.error("queryDatabaseSchema error", e);
                        }
                        metaSchema.setDatabases(databases);

                    } else {
                        List<Schema> schemas = metaData.schemas(connection, null);
                        metaSchema.setSchemas(schemas);
                    }
                    return metaSchema;
                });

        return DataResult.of(ms);
    }

    @Override
    public ActionResult deleteDatabase(DatabaseCreateParam param) {
        Chat2DBContext.getDBManage().dropDatabase(Chat2DBContext.getConnection(), param.getName());
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<Sql> createDatabase(Database database) {
        String sql = Chat2DBContext.getSqlBuilder().buildCreateDatabaseSql(database);
        return DataResult.of(Sql.builder().sql(sql).build());
    }

    @Override
    public ActionResult modifyDatabase(DatabaseCreateParam param) {
        Chat2DBContext.getDBManage().modifyDatabase(Chat2DBContext.getConnection(), param.getName(),
                param.getName());
        return ActionResult.isSuccess();
    }

    @Override
    public ActionResult deleteSchema(SchemaOperationParam param) {
        Chat2DBContext.getDBManage().dropSchema(Chat2DBContext.getConnection(), param.getDatabaseName(),
                param.getSchemaName());
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<Sql> createSchema(Schema schema) {
        String sql = Chat2DBContext.getSqlBuilder().buildCreateSchemaSql(schema);
        return DataResult.of(Sql.builder().sql(sql).build());
    }

    @Override
    public ActionResult modifySchema(SchemaOperationParam param) {
        Chat2DBContext.getDBManage().modifySchema(Chat2DBContext.getConnection(), param.getDatabaseName(),
                param.getSchemaName(),
                param.getNewSchemaName());
        return ActionResult.isSuccess();
    }

    @Override
    public String exportDatabase(DatabaseExportParam param) throws SQLException {
        return Chat2DBContext.getDBManage().exportDatabase(Chat2DBContext.getConnection(),
                param.getDatabaseName(),
                param.getSchemaName(),
                param.getContainData());
    }

    @Override
    public DatabaseExportVo export2(DatabaseExportRequest request) throws SQLException {
        LoginUser loginUser = ContextUtils.getLoginUser();
        String key = CacheConstants.EXPORT_KEY + loginUser.getId() + ":" + request.getDataSourceId()
                + ":" + StrUtil.nullToEmpty(request.getSchemaName());
        if (redisCache.hasKey(key)) {
            return redisCache.getCacheObject(key);
        }
        if (CollUtil.isEmpty(request.getTableList())) {
            MetaData metaSchema = Chat2DBContext.getMetaData();
            List<Table> tables = metaSchema.tables(Chat2DBContext.getConnection(), request.getDatabaseName(), request.getSchemaName(), null);
            List<String> tableList = Lists.newArrayList();
            for (Table table : tables) {
                tableList.add(table.getName());
            }
            request.setTableList(tableList);
        }

        DatabaseExportVo vo = new DatabaseExportVo();
        vo.setTotal(request.getTableList().size());
        vo.setExported(0);
        vo.setDataSourceId(request.getDataSourceId());
        vo.setDatabaseName(request.getDatabaseName());
        vo.setSchemaName(request.getSchemaName());
        vo.setUserId(loginUser.getId());
        vo.setStatus(TaskStatusEnum.INIT.name());
        String filePath = HzbConfig.getUploadPath();
        String fileName = "";
        try {
            fileName = URLEncoder.encode("Export_" +
                                    StrUtil.nullToEmpty(request.getDatabaseName()) + "_"
                                    + StrUtil.nullToEmpty(request.getSchemaName()) + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                                    + ".sql",
                            StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            vo.setStatus(TaskStatusEnum.ERROR.name());
            return vo;
        }
        File file = FileUtil.newFile(filePath + "/" + fileName);
        vo.setDownloadUrl(file.getAbsolutePath());
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();
        CompletableFuture.runAsync(() -> {
            try {
                buildContext(loginUser, connectInfo);
                doExport(request, file, vo, key);
            } catch (SQLException e) {
                log.error("export database error", e);
                vo.setStatus(TaskStatusEnum.ERROR.name());
            } catch (Exception e) {
                log.error("export database error", e);
                vo.setStatus(TaskStatusEnum.ERROR.name());
            } finally {
                removeContext();
            }
        }).whenComplete((aVoid, throwable) -> {
            if (throwable != null) {
                log.error("export database async error", throwable);
                vo.setStatus(TaskStatusEnum.ERROR.name());
            } else if (!TaskStatusEnum.ERROR.name().equals(vo.getStatus())) {
                vo.setStatus(TaskStatusEnum.FINISH.name());
            }
            redisCache.setCacheObject(key, vo);
        });
        return vo;
    }

    @Override
    public DatabaseImportVo importDatabase(DatabaseImportRequest request) throws Exception {
        LoginUser loginUser = ContextUtils.getLoginUser();
        String key = CacheConstants.IMPORT_KEY + loginUser.getId() + ":" + request.getDataSourceId()
                + ":" + StrUtil.nullToEmpty(request.getSchemaName());
        if (redisCache.hasKey(key)) {
            return redisCache.getCacheObject(key);
        }
        if (StrUtil.isEmpty(request.getImportUrl())) {
            throw new RuntimeException("文件不存在");
        }
        // 本地资源路径
        String localPath = HzbConfig.getProfile();
        // 数据库资源地址
        String importUrl = localPath + com.jd.common.utils.StringUtils.substringAfter(request.getImportUrl(), Constants.RESOURCE_PREFIX);
        final File file = FileUtil.newFile(importUrl);
        if (!file.exists()) {
            throw new RuntimeException("文件不存在");
        }
        final int totalLines = FileUtil.getTotalLines(file);
        DatabaseImportVo vo = new DatabaseImportVo();
        vo.setTotal(totalLines);
        vo.setExported(0);
        vo.setErrorNum(0);
        vo.setMessage(Lists.newLinkedList());
        vo.setDataSourceId(request.getDataSourceId());
        vo.setDatabaseName(request.getDatabaseName());
        vo.setSchemaName(request.getSchemaName());
        vo.setUserId(loginUser.getId());
        vo.setStatus(TaskStatusEnum.INIT.name());
        String fileName = "";
        try {
            fileName = URLEncoder.encode("Import_" +
                                    StrUtil.nullToEmpty(request.getDatabaseName()) + "_"
                                    + StrUtil.nullToEmpty(request.getSchemaName()) + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                                    + ".log",
                            StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            vo.setStatus(TaskStatusEnum.ERROR.name());
            return vo;
        }
        String filePath = HzbConfig.getUploadPath();
        File logFile = FileUtil.newFile(filePath + "/" + fileName);
        vo.setLogFile(logFile.getAbsolutePath());
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();
        CompletableFuture.runAsync(() -> {
            try {
                buildContext(loginUser, connectInfo);
                doImport(request, file, logFile, vo, key);
            } catch (Exception e) {
                log.error("import database error", e);
                vo.setStatus(TaskStatusEnum.ERROR.name());
            } finally {
                removeContext();
            }
        }).whenComplete((aVoid, throwable) -> {
            DatabaseImportVo vo1 = redisCache.getCacheObject(key);
            DatabaseImportVo resultVo = vo;
            if (throwable != null || StrUtil.equals(TaskStatusEnum.ERROR.name(), vo.getStatus())
                    || (vo1 != null && StrUtil.equals(TaskStatusEnum.ERROR.name(), vo1.getStatus()))) {
                vo.setStatus(TaskStatusEnum.ERROR.name());
                if (vo1 != null) {
                    vo1.setStatus(TaskStatusEnum.ERROR.name());
                    resultVo = vo1;
                }
            } else if (Validator.isEmpty(vo1)) {
                vo.setStatus(TaskStatusEnum.FINISH.name());
            } else {
                vo1.setStatus(TaskStatusEnum.FINISH.name());
                resultVo = vo1;
            }
            redisCache.setCacheObject(key, resultVo);
            FileUtils.deleteFile(importUrl);
        });
        return vo;
    }

    @Override
    public DmpExportVo exportDmp(DmpExportRequest dmpExportRequest) {
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();
        MetaData metaData = Chat2DBContext.getMetaData();

        //导出路径是否存在
//        final File file = FileUtil.newFile(dmpExportRequest.getFilePath());
        final File file = FileUtil.newFile(HzbConfig.getProfile());
        dmpExportRequest.setFilePath(HzbConfig.getProfile());
        if (!file.exists()) {
            boolean mkdirs = file.mkdirs();
            if (!mkdirs) {
                log.error("创建文件夹失败,创建文件路径：{}", HzbConfig.getProfile());
                throw new RuntimeException("创建文件夹失败");
            }
        }
        LoginUser loginUser = null;
        try {
            loginUser = ContextUtils.getLoginUser();
        } catch (Exception e) {
            loginUser = new LoginUser();
        }
        DataSourceDO dataSourceDO = dataSourceMapper.selectById(dmpExportRequest.getDataSourceId());
        if (DBTypeEnum.ORACLE.equals(dataSourceDO.getType()) && StrUtil.isBlank(dataSourceDO.getServiceName())) {
            throw new BusinessException("SID不支持请切换serverName执行导出");
        }
        String dmpExport = "";
        String dmpUser = "";
        String dmpPassword = "";
        if (connectInfo.getDbType().equalsIgnoreCase(DBTypeEnum.DM.name())) {
            dmpExport = resolveDmpTool(DBTypeEnum.DM, true, dmDmpExport);
            dmpUser = connectInfo.getUser();
            dmpPassword = connectInfo.getPassword();
        }
        if (connectInfo.getDbType().equalsIgnoreCase(DBTypeEnum.ORACLE.name())) {
            dmpExport = resolveDmpTool(DBTypeEnum.ORACLE, true, oraDmpExport);
            dmpUser = connectInfo.getUser();
            dmpPassword = connectInfo.getPassword();
        }
        String logFileName = "";
        String dmpFileName = "";
        DmpExportVo dmpExportVo = new DmpExportVo();
        dmpExportVo.setDataSourceId(connectInfo.getDataSourceId());
        dmpExportVo.setDatabaseName(dmpExportRequest.getDatabaseName());
        dmpExportVo.setUserId(loginUser.getId());
        dmpExportVo.setStatus(TaskStatusEnum.INIT.name());
        dmpExportVo.setSchemaName(StringUtils.isNotBlank(dmpExportRequest.getSchemaName()) ? dmpExportRequest.getSchemaName() : "");
        Long taskId = createDmpTask(TaskTypeEnum.DOWNLOAD_TABLE_STRUCTURE, "DMP导出", dmpExportRequest.getDataSourceId(), dmpExportRequest.getDatabaseName(), dmpExportRequest.getSchemaName());
        dmpExportVo.setTaskId(taskId);
        List<String> tableList = CollUtil.isEmpty(dmpExportRequest.getTableList())
                ? Collections.emptyList() : new ArrayList<>(dmpExportRequest.getTableList());
        List<String> schemaList = CollUtil.isEmpty(dmpExportRequest.getSchemaList())
                ? Collections.emptyList() : new ArrayList<>(dmpExportRequest.getSchemaList());
        try {
            logFileName = URLEncoder.encode("dmp_" +
                            connectInfo.getDataSourceId() + "_"
                            + StrUtil.nullToEmpty(connectInfo.getSchemaName()) + "_" + taskId + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                            + ".log",
                    StandardCharsets.UTF_8.toString());
            dmpFileName = URLEncoder.encode("dmp_" +
                            connectInfo.getDataSourceId() + "_"
                            + StrUtil.nullToEmpty(connectInfo.getSchemaName()) + "_" + taskId + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                            + ".dmp",
                    StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            dmpExportVo.setStatus(TaskStatusEnum.ERROR.name());
            dmpExportVo.setMsg("生成导出文件名失败：" + e.getMessage());
            updateDmpTask(taskId, TaskStatusEnum.ERROR.name(), null, "1", dmpExportVo.getMsg());
            return dmpExportVo;
        }

        dmpExportVo.setDmpUrl(HzbConfig.getProfile() + "/" + dmpFileName);
        dmpExportVo.setLogUrl(HzbConfig.getProfile() + "/" + logFileName);
        final File finalLogFile = new File(HzbConfig.getProfile() + "/" + logFileName);
        final File finalDmpFile = new File(HzbConfig.getProfile() + "/" + dmpFileName);
        String schemas = "";
        if (CollUtil.isEmpty(schemaList)) {
            schemas = dmpExportRequest.getSchemaName();
        } else {
            schemas = String.join(",", schemaList);
        }
        String finalDmpExport = dmpExport;
        String finalSchemas = schemas;
        StringBuilder commandOutput = new StringBuilder();

        LoginUser finalLoginUser = loginUser;
        String finalDmpUser = dmpUser;
        String finalDmpPassword = dmpPassword;
        CompletableFuture.supplyAsync(() -> {
            Process start = null;
            try {
                buildContext(finalLoginUser, connectInfo);
                updateDmpTask(taskId, TaskStatusEnum.PROCESSING.name(), null, "0.5");
                Boolean isWin = false;
                if (OSUtils.isWindows()) {
                    isWin = true;
                }
                DmpCommand command = metaData.exportDmp(connectInfo, tableList, finalSchemas, finalLogFile, finalDmpFile, finalDmpExport, isWin, finalDmpUser, finalDmpPassword);
                ProcessBuilder processBuilder = null;
                boolean shouldClose = false;
                try {
                    log.info("执行数据库导出工具：{}", command.getExecutable());
                    processBuilder = new ProcessBuilder(command.toProcessArguments()).inheritIO();
                    if (OSUtils.isKylin() || OSUtils.isLinux()) {
                        processBuilder = processBuilder.directory(new File("/root"));
                    }
                    configureDmpToolEnvironment(processBuilder, connectInfo.getDbType(), finalDmpExport);
                } catch (Exception e) {
                    shouldClose = true;
                    dmpExportVo.setStatus(TaskStatusEnum.ERROR.name());
                    throw new BusinessException("导出失败：" + e.getMessage());
                }
                dmpExportVo.setCmdCommand(command.toSafeString());
                processBuilder.redirectErrorStream();
                StringBuilder stringBuilder = new StringBuilder();
                try {
                    start = processBuilder.start();
                    InputStream inputStream = start.getInputStream();
                    BufferedReader gbk = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                    String line;

                    while ((line = gbk.readLine()) != null) {
                        stringBuilder.append(line).append(System.lineSeparator());
                    }
                    commandOutput.append(stringBuilder);
                    gbk.close();
                    int exitCode = start.waitFor();
                    boolean oracleCompletedWithWarnings = DBTypeEnum.ORACLE.name().equalsIgnoreCase(connectInfo.getDbType())
                            && exitCode == 3;
                    if (exitCode != 0 && !oracleCompletedWithWarnings) {
                        dmpExportVo.setStatus(TaskStatusEnum.ERROR.name());
                        throw new BusinessException("导出失败，进程退出码：" + exitCode);
                    }
                    if (oracleCompletedWithWarnings) {
                        dmpExportVo.setMsg("导出完成，但 Oracle 工具返回警告（例如统计信息未导出）。请查看任务详情中的工具输出。");
                    }
                } catch (Exception e) {
                    shouldClose = true;
                    dmpExportVo.setStatus(TaskStatusEnum.ERROR.name());
                    throw new BusinessException("导出失败：" + e.getMessage());
                }
                if (shouldClose && start != null) {
                    start.destroy();
                }
                return Boolean.TRUE;
            } finally {
                removeContext();
            }
        }).whenComplete((result, throwable) -> {
            if (throwable != null || !Boolean.TRUE.equals(result)) {
                log.error("export dmp async error", throwable);
                dmpExportVo.setStatus(TaskStatusEnum.ERROR.name());
                String message = buildDmpFailureMessage(throwable, commandOutput.toString());
                dmpExportVo.setMsg(message);
                updateDmpTask(taskId, TaskStatusEnum.ERROR.name(), null, "1", message);
                return;
            }
            if (Boolean.TRUE.equals(result)) {
                try {
                    List<String> resultmsg = new ArrayList<>();
                    try {
                        if (OSUtils.isWindows()) {
                            resultmsg = FileUtil.readLines(finalLogFile, "GBK");
                        } else {
                            resultmsg = FileUtil.readLines(finalLogFile, StandardCharsets.UTF_8);
                        }
                    } catch (IORuntimeException e) {
                        resultmsg.add("日志文件生成失败,程序结束");
                        resultmsg.add("但出现警告");
                    }
                    log.info(resultmsg.toString());
                    if (resultmsg.size() > 0 && resultmsg.toString().contains("但出现警告")) {
                        dmpExportVo.setMsg(resultmsg.toString());
                    }
                } catch (Exception e) {
                    dmpExportVo.setMsg(e.getMessage());
                }
                if(TaskStatusEnum.ERROR.name().equals(dmpExportVo.getStatus())){
                    dmpExportVo.setStatus(TaskStatusEnum.ERROR.name());
                }else{
                    dmpExportVo.setStatus(TaskStatusEnum.FINISH.name());
                }
                updateDmpTask(taskId, dmpExportVo.getStatus(), dmpExportVo.getDmpUrl());
            }
        });
        return dmpExportVo;
    }

//
//    /**
//     * 数据读取操作
//     * @param inputStream
//     * @return
//     */
//    private String reder(InputStream inputStream) {
//        StringBuilder stringBuilder = new StringBuilder();
//        try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8;
//             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)){
//            String line;
//        } catch (IOException e) {
//            log.error("cmd命令读取输入流失败,{}", e);
//        }
//        return stringBuilder.toString();
//    }


    private static final double NAVICAT11 = 1.1D;

    private static CommonCipher cipher;

    private final ConcurrentMap<Long, Process> dmpImportProcesses = new ConcurrentHashMap<>();
    private final Set<Long> cancelledDmpImportTasks = ConcurrentHashMap.newKeySet();

    @Override
    public DmpImportVo importDmp(DmpImportRequest dmpImportRequest) {
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();
        LoginUser loginUser = null;
        Long userId = null;
        try {
            loginUser = ContextUtils.getLoginUser();
            userId = loginUser.getId();
        } catch (Exception e) {
            loginUser = new LoginUser();
        }
        MetaData metaData = Chat2DBContext.getMetaData();
        String key = CacheConstants.IMPORT_DMP_KEY + userId + ":" + dmpImportRequest.getDataSourceId()
                + ":" + StrUtil.nullToEmpty(dmpImportRequest.getFromName());
        log.debug("KRY_NAME==>{}", key);
        if (redisCache.hasKey(key)) {
            DmpImportVo dmpImportVo = redisCache.getCacheObject(key);
            if (TaskStatusEnum.FINISH.name().equals(dmpImportVo.getStatus())
                || TaskStatusEnum.ERROR.name().equals(dmpImportVo.getStatus())) {
                redisCache.deleteObject(key);
            } else {
                redisCache.expire(key, 300);
                return dmpImportVo;
            }
        }
        String filePath = dmpImportRequest.getFilePath();
        int i = filePath == null ? -1 : filePath.indexOf("/upload");
        if (i < 0) {
            throw new BusinessException("导入文件路径无效");
        }
        File sourceFile = new File(HzbConfig.getProfile() + "/" + filePath.substring(i));
        if (!sourceFile.isFile()) {
            throw new BusinessException("导入文件不存在：" + sourceFile.getAbsolutePath());
        }
        String dmpImport = "";
        String dmpUser = "";
        String dmpPassword = "";
        if (connectInfo.getDbType().equalsIgnoreCase(DBTypeEnum.DM.name())) {
            dmpImport = resolveDmpTool(DBTypeEnum.DM, false, dmDmpImport);
            dmpUser = connectInfo.getUser();
            dmpPassword = connectInfo.getPassword();
        } else {
            dmpImport = resolveDmpTool(DBTypeEnum.ORACLE, false, oraDmpImport);
            dmpUser = connectInfo.getUser();
            dmpPassword = connectInfo.getPassword();
        }
        String logFileName = "";
        DmpImportVo dmpImportVo = new DmpImportVo();
        dmpImportVo.setDataSourceId(connectInfo.getDataSourceId());
        dmpImportVo.setDatabaseName(dmpImportRequest.getDatabaseName());
        dmpImportVo.setUserId(loginUser.getId());
        dmpImportVo.setStatus(TaskStatusEnum.INIT.name());
        Long taskId = createDmpTask(TaskTypeEnum.UPLOAD_TABLE_STRUCTURE, "DMP导入", dmpImportRequest.getDataSourceId(), dmpImportRequest.getDatabaseName(), dmpImportRequest.getSchemaName());
        File file = sourceFile;
        if (DBTypeEnum.ORACLE.name().equalsIgnoreCase(connectInfo.getDbType()) && OSUtils.isWindows()) {
            try {
                Path tempDirectory = Paths.get(HzbConfig.getProfile(), "dmp-import");
                Files.createDirectories(tempDirectory);
                Path tempFile = tempDirectory.resolve("task-" + taskId + ".dmp");
                Files.copy(sourceFile.toPath(), tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                file = tempFile.toFile();
            } catch (IOException e) {
                String message = "导入文件准备失败：" + e.getMessage();
                dmpImportVo.setStatus(TaskStatusEnum.ERROR.name());
                dmpImportVo.setMsg(message);
                updateDmpTask(taskId, TaskStatusEnum.ERROR.name(), null, "1", message);
                return dmpImportVo;
            }
        }
        try {
            logFileName = URLEncoder.encode("DmpImport_" + UUID.randomUUID() +
                                    StrUtil.nullToEmpty(dmpImportRequest.getDatabaseName()) + "_"
                                    + StrUtil.nullToEmpty(dmpImportRequest.getToName()) + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                                    + ".log",
                            StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            dmpImportVo.setStatus(TaskStatusEnum.ERROR.name());
            return dmpImportVo;
        }
        final File file1 = new File(HzbConfig.getProfile() + "/" + logFileName);
        dmpImportVo.setLogUrl(file1.getAbsolutePath());

        // init状态
        redisCache.setCacheObject(key, dmpImportVo);
        redisCache.expire(key, 300);
        String finalDmpImport = dmpImport;
        File finalImportFile = file;
        AtomicReference<String> msg = new AtomicReference<>("");
        LoginUser finalLoginUser = loginUser;
        String finalDmpUser = dmpUser;
        String finalDmpPassword = dmpPassword;
        CompletableFuture.supplyAsync(() -> {
            Process start = null;
            try {
                buildContext(finalLoginUser, connectInfo);
                if (cancelledDmpImportTasks.contains(taskId)) {
                    msg.set("任务已取消");
                    return Boolean.FALSE;
                }
                updateDmpTask(taskId, TaskStatusEnum.PROCESSING.name(), null, "0.5");
                Boolean isWin = false;
                if (OSUtils.isWindows()) {
                    isWin = true;
                }
                DmpCommand command = metaData.importDmp(connectInfo, dmpImportRequest.getTableList(), dmpImportRequest.getFromName(), dmpImportRequest.getToName(), dmpImportRequest.getSchemaName(), finalImportFile, file1, finalDmpImport, dmpImportRequest.getToUser(), isWin, finalDmpUser, finalDmpPassword);
                log.info("执行数据库导入工具：{}", command.getExecutable());
                ProcessBuilder processBuilder = new ProcessBuilder(command.toProcessArguments()).inheritIO();
                if (OSUtils.isKylin() || OSUtils.isLinux()) {
                    processBuilder = processBuilder.directory(new File("/root"));
                }
                configureDmpToolEnvironment(processBuilder, connectInfo.getDbType(), finalDmpImport);
                processBuilder.redirectErrorStream();

                start = processBuilder.start();
                dmpImportProcesses.put(taskId, start);

                int exitCode = start.waitFor();
                if (exitCode != 0) {
                    msg.set(cancelledDmpImportTasks.contains(taskId) ? "任务已取消" : "导入失败，进程退出码：" + exitCode);
                    if (start != null) {
                        start.destroy();
                    }
                    return Boolean.FALSE;
                }
            } catch (Exception e) {
                msg.set("导入失败：" + e.getMessage());
                if (start != null) {
                    start.destroy();
                }
                return Boolean.FALSE;
            } finally {
                dmpImportProcesses.remove(taskId);
                cancelledDmpImportTasks.remove(taskId);
                removeContext();
            }
            return Boolean.TRUE;
        }).thenAccept(result -> {
            if (Boolean.TRUE.equals(result)) {
//                if(StringUtils.isNotBlank(msg.get())){
//                    dmpImportVo.setMsg(msg.get());
//                }
                List<String> resultmsg = new ArrayList<>();
                try {
                    if (OSUtils.isWindows()) {
                        resultmsg = FileUtil.readLines(file1, "GBK");
                    } else {
                        resultmsg = FileUtil.readLines(file1, StandardCharsets.UTF_8);
                    }
                } catch (IORuntimeException e) {
                    resultmsg.add("日志文件生成失败,程序结束");
                    resultmsg.add("但出现警告");
                }
                log.info(resultmsg.toString());
                if (resultmsg.size() > 0 && resultmsg.toString().contains("但出现警告")) {
                    dmpImportVo.setMsg(resultmsg.toString());
                }
                dmpImportVo.setStatus(TaskStatusEnum.FINISH.name());
                updateDmpTask(taskId, TaskStatusEnum.FINISH.name(), dmpImportVo.getLogUrl());
//                FileUtils.deleteFile(file1.getPath());
                try {
                    // redis比导入流快,因此设置
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error("导入延迟睡眠失败：{}", e.getMessage());
                }
                redisCache.setCacheObject(key, dmpImportVo);
                redisCache.expire(key, 300);
            } else {
                dmpImportVo.setStatus(TaskStatusEnum.ERROR.name());
                if (StringUtils.isNotBlank(msg.get())) {
                    dmpImportVo.setMsg(msg.get());
                }
                updateDmpTask(taskId, TaskStatusEnum.ERROR.name(), null, "1", dmpImportVo.getMsg());
                redisCache.setCacheObject(key, dmpImportVo);
                redisCache.expire(key, 300);
            }

        });

        return dmpImportVo;
    }

    @Override
    public ActionResult cancelDmpImport(Long taskId) {
        cancelledDmpImportTasks.add(taskId);
        Process process = dmpImportProcesses.get(taskId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
        updateDmpTask(taskId, TaskStatusEnum.ERROR.name(), null, "1", "任务已取消");
        return ActionResult.isSuccess();
    }

    @Override
    public DmpImportVo importDmpList(DmpImportRequest dmpImportRequest) throws InterruptedException {
        List<String> fromUser = new CopyOnWriteArrayList<>(dmpImportRequest.getFromUser());
        List<String> toUser = new CopyOnWriteArrayList<>(dmpImportRequest.getToUser());
        DmpImportVo dmpImportVo = new DmpImportVo();
        for (int i = 0; i < fromUser.size(); i++) {
            dmpImportRequest.setFromName(fromUser.get(i));
            dmpImportRequest.setToName(toUser.get(i));
            dmpImportVo = this.importDmp(dmpImportRequest);
//            do {
//                dmpImportVo = this.importDmp(dmpImportRequest);
//                Thread.sleep(5000);
//            } while (!TaskStatusEnum.FINISH.name().equals(dmpImportVo.getStatus()));
        }
        return dmpImportVo;
    }

    private String resolveDmpTool(DBTypeEnum dbType, boolean export, String configuredPath) {
        boolean windows = OSUtils.isWindows();
        String toolName = dbType == DBTypeEnum.DM
                ? (export ? "dexp" : "dimp")
                : (export ? "exp" : "imp");
        if (windows) {
            toolName += ".exe";
        }
        String toolDirectory = dbType == DBTypeEnum.DM ? "dm_tools" : "orcale_tools";
        String platform = windows ? "windows" : "linux";
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path binDirectory = dbType == DBTypeEnum.DM && !windows
                    ? current.resolve(toolDirectory).resolve(platform).resolve("x86").resolve("bin")
                    : current.resolve(toolDirectory).resolve(platform).resolve("bin");
            Path bundled = binDirectory.resolve(toolName);
            if (Files.isRegularFile(bundled) && (windows || Files.isExecutable(bundled))) {
                return bundled.toString();
            }
            current = current.getParent();
        }
        log.warn("未找到项目内{}工具，回退到配置路径", toolName);
        return configuredPath;
    }

    private void configureDmpToolEnvironment(ProcessBuilder processBuilder, String dbType, String dmpTool) {
        if (!DBTypeEnum.ORACLE.name().equalsIgnoreCase(dbType)) {
            return;
        }
        Map<String, String> environment = processBuilder.environment();
        Path toolDirectory = Paths.get(dmpTool).toAbsolutePath().getParent();
        String pathKey = environment.containsKey("Path") ? "Path" : "PATH";
        String originalPath = StrUtil.nullToEmpty(environment.get(pathKey));
        environment.put(pathKey, toolDirectory + File.pathSeparator + originalPath);
        if (hasOracleMessageFiles(environment.get("ORACLE_HOME"))) {
            return;
        }
        for (String entry : originalPath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (StrUtil.isBlank(entry)) {
                continue;
            }
            Path oracleHome = Paths.get(entry.trim().replace("\"", "")).toAbsolutePath().getParent();
            if (oracleHome != null && hasOracleMessageFiles(oracleHome.toString())) {
                environment.put("ORACLE_HOME", oracleHome.toString());
                return;
            }
        }
        log.warn("项目内 Oracle 导入导出工具缺少 RDBMS/mesg 资源，未找到可用 ORACLE_HOME");
    }

    private boolean hasOracleMessageFiles(String oracleHome) {
        if (StrUtil.isBlank(oracleHome)) {
            return false;
        }
        Path home = Paths.get(oracleHome);
        return Files.isDirectory(home.resolve("RDBMS").resolve("mesg"))
                || Files.isDirectory(home.resolve("rdbms").resolve("mesg"));
    }

    private Long createDmpTask(TaskTypeEnum type, String name, Long dataSourceId, String databaseName, String schemaName) {
        TaskCreateParam param = new TaskCreateParam();
        param.setTaskName(name);
        param.setTaskType(type.name());
        param.setDataSourceId(dataSourceId);
        param.setDatabaseName(databaseName);
        param.setSchemaName(schemaName);
        param.setUserId(ContextUtils.getUserId());
        param.setTaskProgress("0.1");
        DataResult<Long> result = taskService.create(param);
        return result.getData();
    }

    private void updateDmpTask(Long taskId, String status, String downloadUrl) {
        updateDmpTask(taskId, status, downloadUrl, TaskStatusEnum.PROCESSING.name().equals(status) ? "0.5" : "1");
    }

    private void updateDmpTask(Long taskId, String status, String downloadUrl, String progress) {
        updateDmpTask(taskId, status, downloadUrl, progress, null);
    }

    private void updateDmpTask(Long taskId, String status, String downloadUrl, String progress, String message) {
        if (taskId == null) {
            return;
        }
        TaskUpdateParam param = new TaskUpdateParam();
        param.setId(taskId);
        param.setTaskStatus(status);
        param.setTaskProgress(progress);
        param.setDownloadUrl(downloadUrl);
        if (StrUtil.isNotBlank(message)) {
            param.setContent(message.getBytes(StandardCharsets.UTF_8));
        }
        taskService.updateStatus(param);
    }

    private String buildDmpFailureMessage(Throwable throwable, String commandOutput) {
        Throwable cause = throwable;
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause == null ? "未知错误" : StrUtil.blankToDefault(cause.getMessage(), cause.getClass().getSimpleName());
        if (StrUtil.isNotBlank(commandOutput)) {
            message += System.lineSeparator() + "工具输出：" + commandOutput.trim();
        }
        return message.length() > 4000 ? message.substring(0, 4000) + "…" : message;
    }


    private void doImport(DatabaseImportRequest request, File importFile, File logFile, DatabaseImportVo vo, String key) {
        vo.setStatus(TaskStatusEnum.PROCESSING.name());
        final Connection connection = Chat2DBContext.getConnection();
        Statement statement = null;
        try {
            statement = connection.createStatement();
            String logFormat = "%s|%s|%s";
            int exported = 1;
            int errorNum = 0;
            int validLine = 0;
            try (PrintWriter printWriter = new PrintWriter(logFile, StandardCharsets.UTF_8.name());
                 BufferedReader reader = new BufferedReader(new FileReader(importFile))) {
                String line;
                StringBuilder sql = new StringBuilder();
                boolean insideBlockComment = false;
                while ((line = reader.readLine()) != null) {
                    exported++;
                    vo.setExported(exported);
                    line = line.trim();
                    // 忽略空行
                    if (line.isEmpty()) {
                        continue;
                    }

                    // 处理块注释
                    if (line.startsWith("/*")) {
                        insideBlockComment = true;
                        continue;
                    }
                    if (insideBlockComment && line.endsWith("*/")) {
                        insideBlockComment = false;
                        continue;
                    }
                    if (insideBlockComment) {
                        continue;
                    }

                    // 处理单行注释
                    if (line.startsWith("--")) {
                        continue;
                    }
                    // 如果不在注释或字符串中，则追加到SQL语句
                    sql.append(line);
                    sql.append("\n");
                    // 检查是否结束了一个完整的SQL语句（以分号结尾）
                    if (line.endsWith(";")) {
                        String sqlStatement = sql.toString().trim();
                        //校验SQL是否正确，若不正确则继续读下一行，若连续1000（这个数据按实际情况改）行都不正确，则往后走，不继续校验。
                        final boolean sqlValid = SqlUtils.isSQLValid(sqlStatement);
                        if (!sqlValid && validLine < 1000) {
                            validLine++;
                            continue;
                        }
                        validLine = 0;
                        // 执行SQL语句
                        if (StrUtil.startWithIgnoreCase(sqlStatement, "SELECT")) {
                            // 处理查询结果
                        } else {
                            if (vo.getMessage().size() > 50) {
                                vo.getMessage().remove(0);
                            }
                            errorNum = executeUpdate(vo, statement, logFormat, errorNum, printWriter, sqlStatement);
                            redisCache.setCacheObject(key, vo);
                        }

                        sql.setLength(0); // 清空StringBuilder以便存储下一个SQL语句
                    }
                    if (sql.length() > 0) {
                        String sqlStatement = sql.toString().trim();
                        errorNum = executeUpdate(vo, statement, logFormat, errorNum, printWriter, sqlStatement);
                    }

                }
            } catch (Exception e) {
                vo.setStatus(TaskStatusEnum.ERROR.name());
                vo.setMessage(Arrays.asList(e.getMessage()));
                redisCache.setCacheObject(key, vo);
                return;
            }
        } catch (SQLException e) {
            vo.setStatus(TaskStatusEnum.ERROR.name());
            vo.setMessage(Arrays.asList(e.getMessage()));
            redisCache.setCacheObject(key, vo);
            return;
        } finally {
            try {
                if (statement != null) {
                    statement.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                log.error("close connection error:{}", e);
            }
        }
    }

    private int executeUpdate(DatabaseImportVo vo, Statement statement, String logFormat, int errorNum, PrintWriter
            printWriter, String sqlStatement) {
        try {
            statement.executeUpdate(sqlStatement);
            final String format = String.format(logFormat,
                    LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                    , sqlStatement, "执行成功");
            vo.getMessage().add(format);
            printWriter.println(format);
        } catch (SQLException e) {
            errorNum++;
            final String format = String.format(logFormat,
                    LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                    , sqlStatement, "执行失败：" + e.getMessage());
            vo.getMessage().add(format);
            vo.setErrorNum(errorNum);
            printWriter.println(format);
        }
        return errorNum;
    }

    private void doExport(DatabaseExportRequest request, File file, DatabaseExportVo vo, String key) throws
            SQLException {
        int i = 0;
        vo.setStatus(TaskStatusEnum.PROCESSING.name());
        try (PrintWriter printWriter = new PrintWriter(file, StandardCharsets.UTF_8.name())) {
            for (String table : request.getTableList()) {
                //导出表结构
                final String s = Chat2DBContext.getDBManage().exportTable(Chat2DBContext.getConnection(), request.getDatabaseName(), request.getSchemaName(), table);
                i++;
                vo.setExported(i);
                printWriter.println(s);
                //导出数据
                if (request.getContainData()) {
                    doExportTableData(request, printWriter, table);
                }
                redisCache.setCacheObject(key, vo);
            }
        } catch (Exception e) {
            e.printStackTrace();
            vo.setStatus(TaskStatusEnum.ERROR.name());
        }
        redisCache.setCacheObject(key, vo);
    }

    private void doExportTableData(DatabaseExportRequest request, PrintWriter printWriter, String table) {
        int pageNo = 1;
        int pageSize = EasyToolsConstant.MAX_EXPORT_SIZE;
        int offset = (pageNo - 1) * pageSize;
        MetaData metaData = Chat2DBContext.getMetaData();
        String tableName = metaData.getMetaDataName(request.getDatabaseName(), request.getSchemaName(),
                table);
        String originalSql = "select * from " + tableName;
        String pageLimit = Chat2DBContext.getSqlBuilder().pageLimit(originalSql, offset, pageNo, pageSize);
        DbType dbType = JdbcUtils.parse2DruidDbType(Chat2DBContext.getConnectInfo().getDbType());
        boolean hasNext = true;
        while (true) {
            if (hasNext) {
                RdbDmlExportController.InsertWrapper insertWrapper = new RdbDmlExportController.InsertWrapper();
                hasNext = SQLExecutor.getInstance().executeTable(Chat2DBContext.getConnection(), pageLimit,
                        headerList -> insertWrapper.setHeaderList(
                                EasyCollectionUtils.toList(headerList, header -> new SQLIdentifierExpr(header.getName())))
                        , dataList -> {
                            SQLInsertStatement sqlInsertStatement = new SQLInsertStatement();
                            sqlInsertStatement.setDbType(dbType);
                            sqlInsertStatement.setTableSource(new SQLExprTableSource(tableName));
                            sqlInsertStatement.getColumns().addAll(insertWrapper.getHeaderList());
                            SQLInsertStatement.ValuesClause valuesClause = new SQLInsertStatement.ValuesClause();
                            for (String s : dataList) {
                                valuesClause.addValue(s);
                            }
                            sqlInsertStatement.setValues(valuesClause);

                            printWriter.println(SQLUtils.toSQLString(sqlInsertStatement, dbType, INSERT_FORMAT_OPTION) + ";");
                        }, false, new DefaultValueHandler());
                pageNo++;
                offset = (pageNo - 1) * pageSize;
                pageLimit = Chat2DBContext.getSqlBuilder().pageLimit(originalSql, offset, pageNo, pageSize);
            } else {
                break;
            }
        }
    }

    private void buildContext(LoginUser loginUser, ConnectInfo connectInfo) {
        ContextUtils.setContext(Context.builder()
                .loginUser(loginUser)
                .build());
        Chat2DBContext.putContext(connectInfo);
    }

    private void removeContext() {
        ContextUtils.removeContext();
        Chat2DBContext.removeContext();
    }

}
