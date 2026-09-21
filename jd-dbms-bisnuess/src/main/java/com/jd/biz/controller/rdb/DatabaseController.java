package com.jd.biz.controller.rdb;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.vo.DatabaseVO;
import com.jd.biz.controller.rdb.converter.DatabaseConverter;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.*;
import com.jd.biz.controller.rdb.vo.DatabaseExportVo;
import com.jd.biz.controller.rdb.vo.DatabaseImportVo;
import com.jd.biz.controller.rdb.vo.MetaSchemaVO;
import com.jd.biz.domain.api.enums.TaskStatusEnum;
import com.jd.biz.domain.api.param.MetaDataQueryParam;
import com.jd.biz.domain.api.param.datasource.DatabaseCreateParam;
import com.jd.biz.domain.api.param.datasource.DatabaseExportParam;
import com.jd.biz.domain.api.param.datasource.DatabaseQueryAllParam;
import com.jd.biz.domain.api.service.DatabaseService;
import com.jd.common.annotation.Log;
import com.jd.common.config.HzbConfig;
import com.jd.common.constant.CacheConstants;
import com.jd.common.core.redis.RedisCache;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.common.model.LoginUser;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.utils.file.FileUtils;
import com.jd.spi.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.File;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Objects;

/**
 * database controller
 */
@ConnectionInfoAspect
@RequestMapping("/api/rdb/database")
@RestController
@Slf4j
public class DatabaseController {
    @Autowired
    private RdbWebConverter rdbWebConverter;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    public DatabaseConverter databaseConverter;
    @Autowired
    private RedisCache redisCache;


    /**
     * 查询数据库里包含的database_schema_list
     *
     * @param request
     * @return
     */
    @GetMapping("/database_schema_list")
    public DataResult<MetaSchemaVO> databaseSchemaList(@Valid DataSourceBaseRequest request) {
        MetaDataQueryParam queryParam = MetaDataQueryParam.builder().dataSourceId(request.getDataSourceId())
                .refresh(
                        request.getRefresh()).build();
        DataResult<MetaSchema> result = databaseService.queryDatabaseSchema(queryParam);
        MetaSchemaVO schemaDto2vo = rdbWebConverter.metaSchemaDto2vo(result.getData());
        return DataResult.of(schemaDto2vo);
    }

    @GetMapping("list")
    public ListResult<DatabaseVO> databaseList(@Valid DataSourceBaseRequest request) {
        DatabaseQueryAllParam queryParam = DatabaseQueryAllParam.builder().dataSourceId(request.getDataSourceId())
                .refresh(
                        request.getRefresh()).build();
        ListResult<Database> result = databaseService.queryAll(queryParam);
        return ListResult.of(rdbWebConverter.databaseDto2vo(result.getData()));
    }

    /**
     * 删除数据库
     *
     * @param request
     * @return
     */
    @PostMapping("/delete_database")
    public ActionResult deleteDatabase(@Valid @RequestBody DataSourceBaseRequest request) {
        DatabaseCreateParam param = DatabaseCreateParam.builder().name(request.getDatabaseName()).build();
        return databaseService.deleteDatabase(param);
    }

    /**
     * 创建database
     *
     * @param request
     * @return
     */
    @PostMapping("/create_database_sql")
    public DataResult<Sql> createDatabase(@Valid @RequestBody DatabaseCreateRequest request) {
        if (StringUtils.isBlank(request.getName())) {
            request.setName(request.getDatabaseName());
        }
        Database database = databaseConverter.request2param(request);
        return databaseService.createDatabase(database);
    }

    /**
     * 修改database
     *
     * @param request
     * @return
     */
    @PostMapping("/modify_database")
    public ActionResult modifyDatabase(@Valid @RequestBody UpdateDatabaseRequest request) {
        DatabaseCreateParam param = DatabaseCreateParam.builder().name(request.getDatabaseName())
                .name(request.getNewDatabaseName()).build();
        return databaseService.modifyDatabase(param);
    }

    @PostMapping("/export")
    public void exportDatabase(@Valid @RequestBody DatabaseExportRequest request, HttpServletResponse response) {
        String fileName = Objects.isNull(request.getSchemaName()) ? request.getDatabaseName() : request.getSchemaName();
        response.setContentType("text/sql");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".sql");
        response.setCharacterEncoding("utf-8");
        DatabaseExportParam param = databaseConverter.request2param(request);
        try (PrintWriter printWriter = response.getWriter()) {
            String sql = databaseService.exportDatabase(param);
            printWriter.println(sql);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 导出数据库表
     *
     * @param request
     * @return
     */
    @PostMapping("/export2")
    public DataResult<DatabaseExportVo> export2(@Valid @RequestBody DatabaseExportRequest request) {
        DatabaseExportVo exportVo;
        try {
            if (StrUtil.isAllEmpty(request.getDatabaseName(), request.getSchemaName())) {
                throw new RuntimeException("参数异常");
            }
            exportVo = databaseService.export2(request);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return DataResult.of(exportVo);
    }

    /**
     * 下载导出的文件
     *
     * @param request
     * @param response
     */
    @PostMapping("/export2/download")
    public void download(@Valid @RequestBody DatabaseExportRequest request, HttpServletResponse response) {
        try {
            if (StrUtil.isAllEmpty(request.getDatabaseName(), request.getSchemaName())) {
                throw new RuntimeException("参数异常");
            }
            LoginUser loginUser = ContextUtils.getLoginUser();
            String key = CacheConstants.EXPORT_KEY + loginUser.getId() + ":" + StrUtil.nullToEmpty(request.getDatabaseName())
                    + ":" + StrUtil.nullToEmpty(request.getSchemaName());
            DatabaseExportVo vo = null;
            if (redisCache.hasKey(key)) {
                vo = redisCache.getCacheObject(key);
            }
            if (Validator.isEmpty(vo)) {
                return;
            }
            if (!StrUtil.equals(vo.getStatus(), TaskStatusEnum.FINISH.name())) {
                throw new RuntimeException("数据库导出未完成。");
            }
            File file = FileUtil.newFile(vo.getDownloadUrl());
            if (file.exists()) {
                response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                FileUtils.setAttachmentResponseHeader(response, file.getName());
                FileUtils.writeBytes(vo.getDownloadUrl(), response.getOutputStream());
                if (request.getDelete()) {
                    redisCache.deleteObject(key);
                    FileUtils.deleteFile(vo.getDownloadUrl());
                }
            } else {
                throw new RuntimeException("文件不存在。");
            }


        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

    }

    /**
     * 导出数据库表
     *
     * @param request
     * @return
     */
    @Log(title = "导入管理", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public DataResult<DatabaseImportVo> importDatabase(@Valid @RequestBody DatabaseImportRequest request) {
        DatabaseImportVo importVo;
        try {
            if (StrUtil.isAllEmpty(request.getDatabaseName(), request.getSchemaName())) {
                throw new RuntimeException("参数异常");
            }
            importVo = databaseService.importDatabase(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return DataResult.of(importVo);
    }

    /**
     * 下载导如的日志文件
     *
     * @param request
     * @param response
     */
    @PostMapping("/import/download")
    public void importDownload(@Valid @RequestBody DatabaseImportRequest request, HttpServletResponse response) {
        try {
            if (StrUtil.isAllEmpty(request.getDatabaseName(), request.getSchemaName())) {
                throw new RuntimeException("参数异常");
            }
            LoginUser loginUser = ContextUtils.getLoginUser();
            String key = CacheConstants.IMPORT_KEY + loginUser.getId() + ":" + StrUtil.nullToEmpty(request.getDatabaseName())
                    + ":" + StrUtil.nullToEmpty(request.getSchemaName());
            DatabaseImportVo vo = null;
            if (redisCache.hasKey(key)) {
                vo = redisCache.getCacheObject(key);
            }
            if (Validator.isEmpty(vo)) {
                return;
            }
            if (!StrUtil.equals(vo.getStatus(), TaskStatusEnum.FINISH.name()) &&
                    !StrUtil.equals(vo.getStatus(), TaskStatusEnum.ERROR.name())) {
                throw new RuntimeException("数据库导出未完成。");
            }
            File file = FileUtil.newFile(vo.getLogFile());
            if (file.exists()) {
                response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                FileUtils.setAttachmentResponseHeader(response, file.getName());
                FileUtils.writeBytes(vo.getLogFile(), response.getOutputStream());
                FileUtils.deleteFile(vo.getLogFile());
                redisCache.deleteObject(key);
            } else {
                throw new RuntimeException("文件不存在。");
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

    }

    @Log(title = "导出管理", businessType = BusinessType.EXPORT)
    @PostMapping("/exportDmp")
    public DataResult<DmpExportVo> exportDmp(@Valid @RequestBody DmpExportRequest dmpExportRequest) {
        DmpExportVo dmpExportVo = databaseService.exportDmp(dmpExportRequest);
        return DataResult.of(dmpExportVo);
    }

    @PostMapping("/helpDocument")
    public void downloadExportDmp(HttpServletResponse response) {
        try {
            String fileUrl = HzbConfig.getProfile() + "/网页版数据库帮助文档.docx";
            File file = FileUtil.newFile(fileUrl);
            if (file.exists()) {
                response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                FileUtils.setAttachmentResponseHeader(response, file.getName());
                FileUtils.writeBytes(fileUrl, response.getOutputStream());
            } else {
                throw new RuntimeException("文件不存在。");
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

    }



    @PostMapping("/downloadExportDmp")
    public void downloadExportDmp(@Valid @RequestBody DmpImportRequest request, HttpServletResponse response) {
        try {

            LoginUser loginUser = ContextUtils.getLoginUser();
            String key = CacheConstants.EXPORT_DMP_KEY + loginUser.getId() + ":" + request.getDataSourceId()
                    + ":" + StrUtil.nullToEmpty(request.getSchemaName());
            log.debug("KRY_NAME==>{}", key);
            DmpExportVo vo = null;
            if (redisCache.hasKey(key)) {
                vo = redisCache.getCacheObject(key);
            }
            log.debug("vo==>{}", JSON.toJSONString(vo));
            if (Validator.isEmpty(vo)) {
                throw new RuntimeException("文件不存在。");
            }
            if (!StrUtil.equals(vo.getStatus(), TaskStatusEnum.FINISH.name()) &&
                    !StrUtil.equals(vo.getStatus(), TaskStatusEnum.ERROR.name())) {
                throw new RuntimeException("dmp导出未完成。");
            }
            redisCache.deleteObject(key);
            File file = FileUtil.newFile(vo.getDmpUrl());
            if (file.exists()) {
                response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                FileUtils.setAttachmentResponseHeader(response, file.getName());
                FileUtils.writeBytes(vo.getDmpUrl(), response.getOutputStream());
                FileUtils.deleteFile(vo.getDmpUrl());
                FileUtils.deleteFile(vo.getLogUrl());
            } else {
                throw new RuntimeException("文件不存在。");
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

    }

    @Log(title = "导入管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importDmp")
    public DataResult<DmpImportVo> importDmp(@Valid @RequestBody DmpImportRequest dmpImportRequest) {
        if (Objects.isNull(dmpImportRequest.getFileName())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "file文件不能为空");
        }
        DmpImportVo dmpImportVo = databaseService.importDmp(dmpImportRequest);
        return DataResult.of(dmpImportVo);
    }

    @Log(title = "导入管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importDmpList")
    public DataResult<DmpImportVo> importDmpList(@Valid @RequestBody DmpImportRequest dmpImportRequest) throws InterruptedException {
        if (CollUtil.isEmpty(dmpImportRequest.getFromUser()) || CollUtil.isEmpty(dmpImportRequest.getToUser())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "参数异常");
        }
        if (Objects.isNull(dmpImportRequest.getFileName())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "file文件不能为空");
        }
        DmpImportVo dmpImportVo = databaseService.importDmpList(dmpImportRequest);
        return DataResult.of(dmpImportVo);
    }

    @PostMapping("/downloadImportLog")
    public void downloadImportLog(@Valid @RequestBody DmpImportRequest request, HttpServletResponse response) {
        try {
//            if (StrUtil.isEmpty(request.getDatabaseName())) {
//                throw new RuntimeException("参数异常");
//            }
            LoginUser loginUser = ContextUtils.getLoginUser();
            String key = CacheConstants.IMPORT_DMP_KEY + loginUser.getId() + ":" + StrUtil.nullToEmpty(request.getDatabaseName())
                    + ":" + StrUtil.nullToEmpty(request.getSchemaName());
            DmpImportVo vo = null;
            if (redisCache.hasKey(key)) {
                vo = redisCache.getCacheObject(key);
            }
            if (Validator.isEmpty(vo)) {
                throw new RuntimeException("文件不存在。");
            }
            if (!StrUtil.equals(vo.getStatus(), TaskStatusEnum.FINISH.name()) &&
                    !StrUtil.equals(vo.getStatus(), TaskStatusEnum.ERROR.name())) {
                throw new RuntimeException("dmp导入未完成。");
            }
            redisCache.deleteObject(key);
            File file = FileUtil.newFile(vo.getLogUrl());
            if (file.exists()) {
                response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                FileUtils.setAttachmentResponseHeader(response, file.getName());
                FileUtils.writeBytes(vo.getLogUrl(), response.getOutputStream());
                FileUtils.deleteFile(vo.getLogUrl());
            } else {
                throw new RuntimeException("文件不存在。");
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

    }
}
