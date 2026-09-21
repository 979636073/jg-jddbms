package com.jd.biz.domain.core.impl;


import cn.hutool.core.date.DatePattern;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.visitor.VisitorFeature;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.controller.rdb.request.DmpImportRequest;
import com.jd.biz.domain.api.enums.TaskStatusEnum;
import com.jd.biz.util.OSUtils;
import com.jd.common.config.HzbConfig;
import com.jd.common.constant.CacheConstants;
import com.jd.common.core.redis.RedisCache;
import com.jd.common.tools.common.model.Context;
import com.jd.common.tools.common.model.LoginUser;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.spi.MetaData;
import com.jd.spi.model.DmpCommand;
import com.jd.spi.model.DmpImportVo;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class TestServiceTWOImpl {

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
        String filePath = dmpImportRequest.getFilePath();
        final File file = new File(filePath);
        String dmpImport = "";
        if (connectInfo.getDbType().equalsIgnoreCase(DBTypeEnum.DM.name())) {
            dmpImport = dmDmpImport;
        }
        String logFileName = "";
        DmpImportVo dmpImportVo = new DmpImportVo();
        dmpImportVo.setDataSourceId(connectInfo.getDataSourceId());
        dmpImportVo.setDatabaseName(dmpImportRequest.getDatabaseName());
        dmpImportVo.setUserId(loginUser.getId());
        dmpImportVo.setStatus(TaskStatusEnum.INIT.name());
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
        dmpImportVo.setLogUrl(file.getParent() + "/" + logFileName);
        final File file1 = new File(HzbConfig.getProfile() + "/" + logFileName);
        // init状态
        redisCache.setCacheObject(key, dmpImportVo);
        redisCache.expire(key, 300);
        String finalDmpImport = dmpImport;
        AtomicReference<String> msg = new AtomicReference<>("");
        LoginUser finalLoginUser = loginUser;
        CompletableFuture.supplyAsync(() -> {
            buildContext(finalLoginUser, connectInfo);
            boolean isWin = false;
            if (OSUtils.isWindows()) {
            isWin = true;
            }
        DmpCommand command = metaData.importDmp(connectInfo, dmpImportRequest.getTableList(), dmpImportRequest.getFromName(), dmpImportRequest.getToName(), dmpImportRequest.getSchemaName(), file, file1, finalDmpImport, dmpImportRequest.getToUser(), isWin, dmpDmUser, dmpDmPassword);
            ProcessBuilder processBuilder = null;
        Process start = null;
            log.info("执行数据库导入工具：{}", command.getExecutable());
            processBuilder = new ProcessBuilder(command.toProcessArguments()).inheritIO();
//        }
        processBuilder.redirectErrorStream();
        try {
            start = processBuilder.start();
            start.waitFor();
        } catch (Exception e) {
            msg.set("导入失败：" + e.getMessage());
        }
        return Boolean.TRUE;
        }).thenAccept(result -> {
        });
        return dmpImportVo;
    }
    private void buildContext(LoginUser loginUser, ConnectInfo connectInfo) {
        ContextUtils.setContext(Context.builder()
                .loginUser(loginUser)
                .build());
        Chat2DBContext.putContext(connectInfo);
    }
}
