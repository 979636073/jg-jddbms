package com.jd.biz.domain.core.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.visitor.VisitorFeature;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.controller.rdb.request.DmpExportRequest;
import com.jd.biz.domain.api.enums.TaskStatusEnum;
import com.jd.biz.domain.repository.entity.DataSourceDO;
import com.jd.biz.domain.repository.mapper.DataSourceMapper;
import com.jd.biz.util.OSUtils;
import com.jd.common.config.HzbConfig;
import com.jd.common.constant.CacheConstants;
import com.jd.common.core.redis.RedisCache;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.common.model.Context;
import com.jd.common.tools.common.model.LoginUser;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.spi.MetaData;
import com.jd.spi.model.DmpCommand;
import com.jd.spi.model.DmpExportVo;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class TestServiceImpl {

    /**
     * Format insert statement
     */
    private static final SQLUtils.FormatOption INSERT_FORMAT_OPTION = new SQLUtils.FormatOption(true, false);

    static {
        INSERT_FORMAT_OPTION.config(VisitorFeature.OutputNameQuote, true);
    }

    @Autowired
    private RedisCache redisCache;

    @Value("${exportDmp.dm}")
    private String dmDmpExport;

    @Value("${importDmp.dm}")
    private String dmDmpImport;

    @Value("${exportDmp.oracle}")
    private String oraDmpExport;

    @Value("${importDmp.oracle}")
    private String oraDmpImport;

    @Value("${dmp.dm.user}")
    private String dmpDmUser;

    @Value("${dmp.dm.password}")
    private String dmpDmPassword;

    @Value("${dmp.oracle.user}")
    private String dmpOracleUser;

    @Value("${dmp.oracle.password}")
    private String dmpOraclePassword;


    @Autowired
    private DataSourceMapper dataSourceMapper;



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
        Long userId = null;
        try {
            loginUser = ContextUtils.getLoginUser();
            userId = loginUser.getId();
        } catch (Exception e) {
            loginUser = new LoginUser();
        }
        String key = CacheConstants.EXPORT_DMP_KEY + userId + ":" + dmpExportRequest.getDataSourceId()
                + ":" + StrUtil.nullToEmpty(dmpExportRequest.getSchemaName());
        log.debug("KRY_NAME==>{}", key);
        if (redisCache.hasKey(key)) {
            DmpExportVo vo = redisCache.getCacheObject(key);
            return redisCache.getCacheObject(key);
        }
        DataSourceDO dataSourceDO = dataSourceMapper.selectById(dmpExportRequest.getDataSourceId());
        if (DBTypeEnum.ORACLE.equals(dataSourceDO.getType()) && StrUtil.isBlank(dataSourceDO.getServiceName())) {
            throw new BusinessException("SID不支持请切换serverName执行导出");
        }
        String dmpExport = "";
        if (connectInfo.getDbType().equalsIgnoreCase(DBTypeEnum.DM.name())) {
            dmpExport = dmDmpExport;
        }
        if (connectInfo.getDbType().equalsIgnoreCase(DBTypeEnum.ORACLE.name())) {
            dmpExport = oraDmpExport;
        }
        String logFileName = "";
        String dmpFileName = "";
        DmpExportVo dmpExportVo = new DmpExportVo();
        dmpExportVo.setDataSourceId(connectInfo.getDataSourceId());
        dmpExportVo.setDatabaseName(dmpExportRequest.getDatabaseName());
        dmpExportVo.setUserId(loginUser.getId());
        dmpExportVo.setStatus(TaskStatusEnum.INIT.name());
        dmpExportVo.setSchemaName(StringUtils.isNotBlank(dmpExportRequest.getSchemaName()) ? dmpExportRequest.getSchemaName() : "");
        try {
            logFileName = URLEncoder.encode("dmp_" +
                            connectInfo.getDataSourceId() + "_"
                            + StrUtil.nullToEmpty(connectInfo.getSchemaName()) + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                            + ".log",
                    StandardCharsets.UTF_8.toString());
            dmpFileName = URLEncoder.encode("dmp_" +
                            connectInfo.getDataSourceId() + "_"
                            + StrUtil.nullToEmpty(connectInfo.getSchemaName()) + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                            + ".dmp",
                    StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            dmpExportVo.setStatus(TaskStatusEnum.ERROR.name());
            return dmpExportVo;
        }

        dmpExportVo.setDmpUrl(HzbConfig.getProfile() + "/" + dmpFileName);
        dmpExportVo.setLogUrl(HzbConfig.getProfile() + "/" + logFileName);
        // init状态
        redisCache.setCacheObject(key, dmpExportVo);
        redisCache.expire(key, 300);
        final File finalLogFile = new File(HzbConfig.getProfile() + "/" + logFileName);
        final File finalDmpFile = new File(HzbConfig.getProfile() + "/" + dmpFileName);
        String schemas = "";
        if (CollUtil.isEmpty(dmpExportRequest.getSchemaList())) {
            schemas = dmpExportRequest.getSchemaName();
        } else {
            schemas = String.join(",", dmpExportRequest.getSchemaList());
        }
        String finalDmpExport = dmpExport;
        String finalSchemas = schemas;

        LoginUser finalLoginUser = loginUser;
        CompletableFuture.supplyAsync(() -> {

            buildContext(finalLoginUser, connectInfo);
            Boolean isWin = false;
            if (OSUtils.isWindows()) {
                isWin = true;
            }
            String dmpUser = DBTypeEnum.DM.name().equalsIgnoreCase(connectInfo.getDbType()) ? dmpDmUser : dmpOracleUser;
            String dmpPassword = DBTypeEnum.DM.name().equalsIgnoreCase(connectInfo.getDbType()) ? dmpDmPassword : dmpOraclePassword;
            DmpCommand command = metaData.exportDmp(connectInfo, dmpExportRequest.getTableList(), finalSchemas, finalLogFile, finalDmpFile, finalDmpExport, isWin, dmpUser, dmpPassword);
            ProcessBuilder processBuilder = new ProcessBuilder(command.toProcessArguments()).inheritIO();
            boolean shouldClose = false;
            if (OSUtils.isKylin() || OSUtils.isLinux()) {
                processBuilder = processBuilder.directory(new File("/root"));
            }
            log.info("执行数据库导出工具：{}", command.getExecutable());
            dmpExportVo.setCmdCommand(command.toSafeString());
            processBuilder.redirectErrorStream();
            StringBuilder stringBuilder = new StringBuilder();
            Process start = null;
            try {
                start = processBuilder.start();
                InputStream inputStream = start.getInputStream();
                BufferedReader gbk = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String line;
                while ((line = gbk.readLine()) != null) {
                    stringBuilder.append(line).append("/n");
                }
                gbk.close();
                start.waitFor();
            } catch (Exception e) {
                shouldClose = true;
                throw new BusinessException("导出失败：" + e.getMessage());
            }
            return Boolean.TRUE;
        }).thenAccept(result -> {
//            if (Boolean.TRUE.equals(result)) {
//                try {
//                    List<String> resultmsg = new ArrayList<>();
//                    try {
//                        if (OSUtils.isWindows()) {
//                            resultmsg = FileUtil.readLines(finalLogFile, "GBK");
//                        } else {
//                            resultmsg = FileUtil.readLines(finalLogFile, StandardCharsets.UTF_8);
//                        }
//                    } catch (IORuntimeException e) {
//                        resultmsg.add("日志文件生成失败,程序结束");
//                        resultmsg.add("但出现警告");
//                    }
//                    log.info(resultmsg.toString());
//                    if (resultmsg.size() > 0 && resultmsg.toString().contains("但出现警告")) {
//                        dmpExportVo.setMsg(resultmsg.toString());
//                    }
//                } catch (Exception e) {
//                    dmpExportVo.setMsg(e.getMessage());
//                }
//                dmpExportVo.setStatus(TaskStatusEnum.FINISH.name());
//                removeContext();
//                redisCache.setCacheObject(key, dmpExportVo);
//            } else {
//                redisCache.deleteObject(key);
//            }
        });
        return dmpExportVo;
    }


    private void buildContext(LoginUser loginUser, ConnectInfo connectInfo) {
        ContextUtils.setContext(Context.builder()
                .loginUser(loginUser)
                .build());
        Chat2DBContext.putContext(connectInfo);
    }

}
