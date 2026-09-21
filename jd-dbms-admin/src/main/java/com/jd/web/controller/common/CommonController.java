package com.jd.web.controller.common;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.config.ExcelCellWriteHandler;
import com.jd.biz.config.ExcelSheetWriteHandler;
import com.jd.biz.controller.rdb.RdbDmlExportController;
import com.jd.biz.controller.rdb.enums.ExcelDataTypeEnum;
import com.jd.biz.controller.rdb.request.ExcelDataRequest;
import com.jd.biz.controller.rdb.request.ExcelImportRequest;
import com.jd.biz.controller.rdb.vo.ExcelDataHighVO;
import com.jd.biz.controller.rdb.vo.ExcelDataVo;
import com.jd.biz.controller.rdb.vo.ExcelShowDataPageVo;
import com.jd.biz.controller.rdb.vo.ExcelShowDataVO;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.service.TableService;
import com.jd.common.annotation.Log;
import com.jd.common.config.HzbConfig;
import com.jd.common.constant.CacheConstants;
import com.jd.common.constant.Constants;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.core.redis.RedisCache;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import com.jd.common.utils.StringUtils;
import com.jd.common.utils.file.FileUploadUtils;
import com.jd.common.utils.file.FileUtils;
import com.jd.framework.config.ServerConfig;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableColumn;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import com.jd.web.config.ViewConstant;
import com.jd.web.config.listener.NoModelDataListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通用请求处理
 *
 * @author ruoyi
 */
@ConnectionInfoAspect
@RestController
@RequestMapping("/common")
public class CommonController {
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);

    @Resource
    private ServerConfig serverConfig;

    private static final String FILE_DELIMETER = ",";

    @Resource
    private TableService tableService;

    @Resource
    private RedisCache redisCache;

    /**
     * 通用下载请求
     *
     * @param fileName 文件名称
     * @param delete   是否删除
     */
    @RequestMapping("/download")
    public void fileDownload(String fileName, Boolean delete, HttpServletResponse response, HttpServletRequest request) {
        try {
            if (!FileUtils.checkAllowDownload(fileName)) {
                throw new Exception(StringUtils.format("文件名称({})非法，不允许下载。 ", fileName));
            }
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            String filePath = HzbConfig.getDownloadPath() + fileName;

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, realFileName);
            FileUtils.writeBytes(filePath, response.getOutputStream());
            if (delete) {
                FileUtils.deleteFile(filePath);
            }
        } catch (Exception e) {
            log.error("下载文件失败", e);
        }
    }

    @RequestMapping("/downloadFilePath")
    public void downloadFilePath(String filePath, HttpServletResponse response, HttpServletRequest request) {
        try {
            String realFilePath = HzbConfig.getUploadPath() + filePath;

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, FileUtil.getName(realFilePath));
            FileUtils.writeBytes(realFilePath, response.getOutputStream());
        } catch (Exception e) {
            log.error("下载文件失败", e);
        }
    }

    @GetMapping(value = "previewImage")
    public void previewImage(String filePath, HttpServletRequest request, HttpServletResponse response) {
        // 其余处理略
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            response.setContentType("image/jpeg;charset=utf-8");
            inputStream = new FileInputStream(new File(HzbConfig.getUploadPath() + "/" + filePath));
            if (inputStream == null) {
                return; //
            }
            outputStream = response.getOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, len);
            }
            response.flushBuffer();
        } catch (IOException e) {
            log.info("预览图片失败" + e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.info(e.getMessage());
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.info(e.getMessage());
                }
            }
        }
    }
    /**
     * 预览数据
     */
    @PostMapping("/previewDataAllPage")
    public AjaxResult previewDataAllPage(ExcelImportRequest request) {
        try {
            Map<String, Object> mapValue = redisCache.getCacheMap(CacheConstants.PREVIEW_DATA_ALL_KEY + request.getSchemaName() + request.getName());
            ExcelShowDataPageVo excelShowDataPageVo = new ExcelShowDataPageVo();
            buildPreviewDataPage(excelShowDataPageVo, request, mapValue, null);
            return AjaxResult.success(excelShowDataPageVo);
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 预览SQL数据
     */
    @PostMapping("/previewSQL")
    public DataResult<String> previewSQL(ExcelImportRequest request) {
        if (StrUtil.isEmpty(request.getFile())) {
            throw new BusinessException("文件名称不能为空");
        }
        String localPath = HzbConfig.getProfile();
        String importUrl = localPath + com.jd.common.utils.StringUtils.substringAfter(request.getFile(), Constants.RESOURCE_PREFIX);
        String extName = FileUtil.extName(importUrl);
        if (!"sql".equals(extName.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("文件格式非法");
        }
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(importUrl))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new BusinessException(e.getMessage());
        }
        return DataResult.of(stringBuilder.toString());
    }


    /**
     * 预览数据
     */
    @PostMapping("/previewData")
    public AjaxResult previewData(ExcelImportRequest request) {
        if (StrUtil.isEmpty(request.getFile())) {
            throw new BusinessException("文件名称不能为空");
        }
        ExcelShowDataPageVo excelShowDataPageVo = new ExcelShowDataPageVo();
        String localPath = HzbConfig.getProfile();
        String importUrl = localPath + StringUtils.substringAfter(request.getFile(), Constants.RESOURCE_PREFIX);
        List<LinkedHashMap<String, String>> objectList = new ArrayList<>();
        EasyExcel.read(importUrl, new NoModelDataListener(objectList)).sheet().headRowNumber(0).doRead();
        if (CollUtil.isEmpty(objectList)) {
            throw new BusinessException("文件数据为空");
        }
        if (objectList.size() <= 1) {
            throw new BusinessException("只存在表头数据,程序终止");
        }
        if (objectList.size() > 20000) {
            throw new BusinessException("数据大于2万条终止程序");
        }
        try {
            List<TableColumn> columnList = new ArrayList<>();
            LinkedHashMap<String, String> linkedHashMap = objectList.get(0);
            for (Map.Entry<String, String> entry : linkedHashMap.entrySet()) {
                String value = entry.getValue();
                //这里是识别列的信息
                TableColumn tableColumn = new TableColumn();
                tableColumn.setName(value);
                columnList.add(tableColumn);
            }
            excelShowDataPageVo.setHeardList(columnList);
            List<String> columns = columnList.stream().map(TableColumn::getName).collect(Collectors.toList());
            for (int i = 1; i < objectList.size(); i++) {
                List<String> list = new ArrayList<>(objectList.get(i).values());
                LinkedHashMap<String, String> map = new LinkedHashMap<>();
                for (int j = 0; j < columns.size(); j++) {
                    if ( j > list.size() -1) {
                        map.put(columns.get(j), null);
                    } else {
                        map.put(columns.get(j), StrUtil.isNotBlank(list.get(j)) ? list.get(j) : null);
                    }
                }
                redisCache.setCacheMapValue(CacheConstants.PREVIEW_DATA_ALL_KEY + request.getSchemaName() + request.getName(), i + "", JSON.toJSONString(map));
            }
            Map<String, Object> mapValue = redisCache.getCacheMap(CacheConstants.PREVIEW_DATA_ALL_KEY + request.getSchemaName() + request.getName());
            buildPreviewDataPage(excelShowDataPageVo, request, mapValue, null);
            return AjaxResult.success(excelShowDataPageVo);
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 预览数据
     */
    @PostMapping("/exportErrorData")
    public void exportErrorData(@RequestBody ExcelImportRequest request, HttpServletResponse response) {
        RdbDmlExportController.ExcelWrapper excelWrapper = new RdbDmlExportController.ExcelWrapper();
        try {
            String errorKey;
            if (ExcelDataTypeEnum.ADD.name().equals(request.getExecuteType())) {
                errorKey = CacheConstants.PREVIEW_DATA_ERROR_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.ADD.name();
            } else {
                errorKey = CacheConstants.PREVIEW_DATA_ERROR_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.UPDATE.name();
            }
            Map<String, Object> errorValue = redisCache.getCacheMap(errorKey);
            if (CollUtil.isEmpty(errorValue)) {
                throw new BusinessException("无数据");
            }
            List<Map> dataList = errorValue.values().stream().map(o -> {
                return JSON.parseObject(o.toString(), Map.class);
            }).collect(Collectors.toList());
            response.setContentType("application/vnd.ms-excel");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(System.currentTimeMillis() + ".xlsx", CharsetUtil.UTF_8));

            ExcelWriterBuilder excelWriterBuilder = new ExcelWriterBuilder();
            excelWriterBuilder = EasyExcel.write(response.getOutputStream())
                    .charset(StandardCharsets.UTF_8)
                    .excelType(ExcelTypeEnum.XLSX)
                    .useDefaultStyle(Boolean.TRUE)
                    .registerWriteHandler(new ExcelCellWriteHandler());
            ExcelWriterBuilder WriterBuilder = excelWriterBuilder;
            excelWrapper.setExcelWriterBuilder(WriterBuilder);
            Set<String> headerList = new HashSet<>();
            List<List<Object>> dataValue = new ArrayList<>();
            for (Map<String, Object> map : dataList) {
                headerList = map.keySet();
                List<Object> list = new ArrayList<>(map.values());
                List<Object> value = new ArrayList<>();
                for (Object o : list) {
                    Map object = JSON.parseObject(o.toString(), Map.class);
                    value.add(Objects.isNull(object.get("newValue")) ? null : object.get("newValue"));
                }
                dataValue.add(value);
            }
            WriterBuilder.head(EasyCollectionUtils.toList(headerList, Lists::newArrayList));
            WriterBuilder.registerWriteHandler(new ExcelSheetWriteHandler(headerList.size()));
            excelWrapper.setExcelWriter(WriterBuilder.build());
            excelWrapper.setWriteSheet(EasyExcel.writerSheet(0).build());
            List<List<Object>> writeDataList = Lists.newArrayList();
            writeDataList.addAll(dataValue);
            excelWrapper.getExcelWriter().write(writeDataList, excelWrapper.getWriteSheet());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (excelWrapper.getExcelWriter() != null) {
                excelWrapper.getExcelWriter().finish();
            }
        }
    }


    /**
     * 预览数据
     */
    @PostMapping("/executePreview")
    public AjaxResult executePreview(@RequestBody ExcelImportRequest request) {
        if (CollUtil.isEmpty(request.getFromHeardList()) || CollUtil.isEmpty(request.getToHeardList())) {
            throw new BusinessException("映射字段集缺失");
        }
        ExcelShowDataVO excelShowDataVO = new ExcelShowDataVO();
        List<TableColumn> fromColumnList = request.getFromHeardList();
        List<TableColumn> toColumnList = request.getToHeardList();
        List<String> fromCollect = fromColumnList.stream().map(TableColumn::getName).collect(Collectors.toList());
        List<String> toCollect = toColumnList.stream().map(TableColumn::getName).collect(Collectors.toList());
        Map<String, Object> mapValue = redisCache.getCacheMap(CacheConstants.PREVIEW_DATA_ALL_KEY + request.getSchemaName() + request.getName());
        List<List<Object>> dataAllList = new ArrayList<>();
        List<Object> objects = new ArrayList<>(mapValue.values());
        for (Object object : objects) {
            Map map = JSON.parseObject(object.toString(), Map.class);
            List<Object> list = new ArrayList<>();
            for (String column : fromCollect) {
                Object o = map.get(column);
                list.add(o);
            }
            dataAllList.add(list);
        }
        try {
            Table table = new Table();
            table.setSchemaName(request.getSchemaName());
            table.setName(request.getName());
            Table tableDataInfo = getTableDataInfo(request.getDataSourceId(), request.getName(), request.getSchemaName(), Boolean.TRUE);
            if (CollUtil.isEmpty(tableDataInfo.getColumnList())) {
                throw new BusinessException("目的表字段获取失败");
            }
            List<String> primaryKeyColumnList = tableDataInfo.getColumnList().stream().filter(TableColumn::getPrimaryKey).map(TableColumn::getName).collect(Collectors.toList());
            int size = 0;
            if (CollUtil.isNotEmpty(primaryKeyColumnList)) {
                for (String primaryKeyColumn : primaryKeyColumnList) {
                    if (toCollect.contains(primaryKeyColumn)) {
                        size++;
                    }
                }
                if (size < primaryKeyColumnList.size()) {
                    throw new BusinessException("目的表存在主键集,映射字段不全");
                }
            }
            table.setColumnList(tableDataInfo.getColumnList());
            if (CollUtil.isEmpty(primaryKeyColumnList)) {
                excelShowDataVO = getResultData(dataAllList, fromCollect, toCollect);
            } else {
                List<List<String>> returnDataList = new ArrayList<>();
                List<List<List<Object>>> listList = CollUtil.split(dataAllList, 100);
                for (List<List<Object>> lists : listList) {
                    String sql = Chat2DBContext.getSqlBuilder().selectTable(table, lists, fromCollect, toCollect);
                    ExecuteResult executeResult = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql, new DefaultValueHandler());
                    if (executeResult.getSuccess()) {
                        returnDataList.addAll(executeResult.getDataList());
                    }
                }
                excelShowDataVO = getStringResultData(request.getName(), request.getSchemaName(), returnDataList, fromCollect, primaryKeyColumnList, toCollect);
            }
            if (CollUtil.isNotEmpty(excelShowDataVO.getInsertDataList())) {
                for (int i = 0; i < excelShowDataVO.getInsertDataList().size(); i++) {
                    redisCache.setCacheMapValue(CacheConstants.PREVIEW_DATA_ADD_KEY + request.getSchemaName() + request.getName(), i + "", JSON.toJSONString(excelShowDataVO.getInsertDataList().get(i)));
                }
            }
            if (CollUtil.isNotEmpty(excelShowDataVO.getUpdateDataList())) {
                for (int i = 0; i < excelShowDataVO.getUpdateDataList().size(); i++) {
                    redisCache.setCacheMapValue(CacheConstants.PREVIEW_DATA_UPDATE_KEY + request.getSchemaName() + request.getName(), i + "", JSON.toJSONString(excelShowDataVO.getUpdateDataList().get(i)));
                }
            }
            return AjaxResult.success(true);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error(e.getMessage());
        }
    }





    @PostMapping("/delPreviewData")
    public ActionResult delPreviewData(@RequestBody ExcelImportRequest request) {
        redisCache.deleteObject(CacheConstants.PREVIEW_DATA_ALL_KEY + request.getSchemaName() + request.getName());
        redisCache.deleteObject(CacheConstants.PREVIEW_DATA_ADD_KEY + request.getSchemaName() + request.getName());
        redisCache.deleteObject(CacheConstants.PREVIEW_DATA_UPDATE_KEY + request.getSchemaName() + request.getName());
        redisCache.deleteObject(CacheConstants.PREVIEW_DATA_SUCCESS_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.ADD.name());
        redisCache.deleteObject(CacheConstants.PREVIEW_DATA_SUCCESS_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.UPDATE.name());
        redisCache.deleteObject(CacheConstants.PREVIEW_DATA_ERROR_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.ADD.name());
        redisCache.deleteObject(CacheConstants.PREVIEW_DATA_ERROR_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.UPDATE.name());
        return ActionResult.isSuccess();
    }


    public DataResult<ExcelShowDataPageVo> previewDataAll(ExcelImportRequest request) {
        Map<String, Object> map = new HashMap<>();
        ExcelShowDataPageVo excelShowDataPageVo = new ExcelShowDataPageVo();
        switch (Objects.requireNonNull(ExcelDataTypeEnum.getByType(request.getDataType()))) {
            case ADD:
                if (!redisCache.hasKey(CacheConstants.PREVIEW_DATA_ADD_KEY + request.getSchemaName() + request.getName())) {
                    return DataResult.of(null);
                }
                map = redisCache.getCacheMap(CacheConstants.PREVIEW_DATA_ADD_KEY + request.getSchemaName() + request.getName());
                buildPreviewDataAll(excelShowDataPageVo, request, map);
                return DataResult.of(excelShowDataPageVo);
            case UPDATE:
                if (!redisCache.hasKey(CacheConstants.PREVIEW_DATA_UPDATE_KEY + request.getSchemaName() + request.getName())) {
                    return DataResult.of(null);
                }
                map = redisCache.getCacheMap(CacheConstants.PREVIEW_DATA_UPDATE_KEY + request.getSchemaName() + request.getName());
                buildPreviewDataAll(excelShowDataPageVo, request, map);
                return DataResult.of(excelShowDataPageVo);
            default:
                return DataResult.of(null);
        }
    }

    /**
     * 预览数据分页
     */
    @PostMapping("/previewDataPage")
    public DataResult<ExcelShowDataPageVo> previewDataPage(ExcelImportRequest request) {
        Map<String, Object> map = new HashMap<>();
        Map<String, ExcelDataHighVO> updateMap = new HashMap<>();
        ExcelShowDataPageVo excelShowDataPageVo = new ExcelShowDataPageVo();
        switch (Objects.requireNonNull(ExcelDataTypeEnum.getByType(request.getDataType()))) {
            case ADD:
                if (!redisCache.hasKey(CacheConstants.PREVIEW_DATA_ADD_KEY + request.getSchemaName() + request.getName())) {
                    return DataResult.of(null);
                }
                map = redisCache.getCacheMap(CacheConstants.PREVIEW_DATA_ADD_KEY + request.getSchemaName() + request.getName());
                buildPreviewDataPage(excelShowDataPageVo, request, map, null);
                return DataResult.of(excelShowDataPageVo);
            case SUCCESS:
                String key;
                if (ExcelDataTypeEnum.ADD.name().equals(request.getExecuteType())) {
                    key = CacheConstants.PREVIEW_DATA_SUCCESS_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.ADD.name();
                } else {
                    key = CacheConstants.PREVIEW_DATA_SUCCESS_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.UPDATE.name();
                }
                map = redisCache.getCacheMap(key);
                if (!redisCache.hasKey(key)) {
                    return DataResult.of(null);
                }
                buildPreviewDataPage(excelShowDataPageVo, request, map, null);
                return DataResult.of(excelShowDataPageVo);
            case ERROR:
                String errorKey;
                if (ExcelDataTypeEnum.ADD.name().equals(request.getExecuteType())) {
                    errorKey = CacheConstants.PREVIEW_DATA_ERROR_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.ADD.name();
                } else {
                    errorKey = CacheConstants.PREVIEW_DATA_ERROR_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.UPDATE.name();
                }
                if (!redisCache.hasKey(errorKey)) {
                    return DataResult.of(null);
                }
                map = redisCache.getCacheMap(errorKey);
                buildPreviewDataPage(excelShowDataPageVo, request, map, null);
                return DataResult.of(excelShowDataPageVo);
            case UPDATE:
                if (!redisCache.hasKey(CacheConstants.PREVIEW_DATA_UPDATE_KEY + request.getSchemaName() + request.getName())) {
                    return DataResult.of(null);
                }
                updateMap = redisCache.getCacheMap(CacheConstants.PREVIEW_DATA_UPDATE_KEY + request.getSchemaName() + request.getName());
                buildPreviewDataPage(excelShowDataPageVo, request, null, updateMap);
                return DataResult.of(excelShowDataPageVo);
            default:
                return DataResult.of(null);
        }
    }

    private void buildPreviewDataAll(ExcelShowDataPageVo excelShowDataPageVo, ExcelImportRequest request, Map<String, Object> mapValue) {
        List<Map<String, Object>> collect = mapValue.entrySet().stream().map(entry -> {
                Map<String, Object> newMap = new LinkedHashMap<>();
                newMap.put(entry.getKey(), entry.getValue());
                return newMap;
        }).collect(Collectors.toList());
        List<String> indexList = new ArrayList<>();
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Map<String, Object> map : collect) {
            for (String s : map.keySet()) {
                indexList.add(s);
                Map<String, Object> object = JSON.parseObject(map.get(s).toString().replaceAll("null", ""), LinkedHashMap.class);
                Map<String, Object> linkedHashMap = new LinkedHashMap<>();
                for (String key : object.keySet()) {
                    ExcelDataHighVO excelDataHighVO = JSON.parseObject(object.get(key).toString(), ExcelDataHighVO.class);
                    linkedHashMap.put(key, excelDataHighVO.getNewValue());
                }
                dataList.add(linkedHashMap);
            }
        }
        excelShowDataPageVo.setDataIndex(indexList);
        excelShowDataPageVo.setDataList(dataList);
        excelShowDataPageVo.setDataType(request.getDataType());
        excelShowDataPageVo.setTotal(collect.size());
        excelShowDataPageVo.setSchemaName(request.getSchemaName());
        excelShowDataPageVo.setTableName(request.getName());
    }

    private void buildPreviewDataPage(ExcelShowDataPageVo excelShowDataPageVo, ExcelImportRequest request, Map<String, Object> mapValue, Map<String, ExcelDataHighVO> updateMap) {
        excelShowDataPageVo.setSize(request.getSize());
        excelShowDataPageVo.setPage(request.getPage());
        if (CollUtil.isEmpty(mapValue)) {
            List<Map<String, Object>> collect = updateMap.entrySet().stream().map(entry -> {
                Map<String, Object> newMap = new LinkedHashMap<>();
                newMap.put(entry.getKey(), entry.getValue());
                return newMap;
            }).collect(Collectors.toList());
            List<Map<String, Object>> maps = collect.stream().skip((long) (request.getPage() - 1) * excelShowDataPageVo.getSize()).limit(request.getSize()).collect(Collectors.toList());
            List<String> indexList = new ArrayList<>();
            List<Map<String, ExcelDataHighVO>> dataList = new ArrayList<>();
            for (Map<String, Object> map : maps) {
                for (String s : map.keySet()) {
                    indexList.add(s);
                    Map<String, Object> object = JSON.parseObject(map.get(s).toString().replaceAll("null", ""), LinkedHashMap.class);
                    Map<String, ExcelDataHighVO> linkedHashMap = new LinkedHashMap<>();
                    for (String key : object.keySet()) {
                        ExcelDataHighVO excelDataHighVO = JSON.parseObject(object.get(key).toString(), ExcelDataHighVO.class);
                        linkedHashMap.put(key, excelDataHighVO);
                    }
                    dataList.add(linkedHashMap);
                }
            }
            excelShowDataPageVo.setDataIndex(indexList);
            excelShowDataPageVo.setUpdateDataList(dataList);
            excelShowDataPageVo.setDataType(request.getDataType());
            excelShowDataPageVo.setTotal(collect.size());
        } else {
            List<Map<String, Object>> collect = mapValue.entrySet().stream().map(entry -> {
                Map<String, Object> newMap = new LinkedHashMap<>();
                newMap.put(entry.getKey(), entry.getValue());
                return newMap;
            }).collect(Collectors.toList());
            List<Map<String, Object>> maps = collect.stream().skip((long) (request.getPage() - 1) * excelShowDataPageVo.getSize()).limit(request.getSize()).collect(Collectors.toList());
            List<String> indexList = new ArrayList<>();
            List<Map<String, Object>> dataList = new ArrayList<>();
            for (Map<String, Object> map : maps) {
                for (String s : map.keySet()) {
                    indexList.add(s);
                    Map<String, Object> object = JSON.parseObject(map.get(s).toString().replaceAll("null", ""), LinkedHashMap.class);
                    dataList.add(object);
                }
            }
            excelShowDataPageVo.setDataIndex(indexList);
            excelShowDataPageVo.setDataList(dataList);
            excelShowDataPageVo.setDataType(request.getDataType());
            excelShowDataPageVo.setTotal(collect.size());
        }
        excelShowDataPageVo.setSchemaName(request.getSchemaName());
        excelShowDataPageVo.setTableName(request.getName());
    }

    /**
     * 获取导入表的主键信息
     * @param dataSourceId
     * @param name
     * @param SchemaName
     * @return
     */
    private Table getTableDataInfo(Long dataSourceId, String name, String SchemaName, Boolean isView) {
        TableQueryParam param = new TableQueryParam();
        param.setDataSourceId(dataSourceId);
        param.setTableName(name);
        param.setSchemaName(SchemaName);
        param.setIsView(isView);
        DataResult<Table> query = tableService.query(param, null);
        Table data = new Table();
        if (query.getSuccess() && Objects.nonNull(query.getData())) {
            data = query.getData();
        }
        return data;
    }

    private ExcelShowDataVO getResultData(List<List<Object>> returnDataList, List<String> fromCollect, List<String> toCollect) {
        ExcelShowDataVO excelShowDataVO = new ExcelShowDataVO();
        List<Map<String, ExcelDataHighVO>> insertDataList = new ArrayList<>();
        for (List<Object> list : returnDataList) {
            Map<String, ExcelDataHighVO> map = new LinkedHashMap<>();
            for (int i = 0; i < fromCollect.size(); i++) {
                ExcelDataHighVO excelDataHighVO;
                if (i > list.size() - 1) {
                    excelDataHighVO = buildExcelDataHighVo(fromCollect.get(i), null, null);
                } else {
                    excelDataHighVO = buildExcelDataHighVo(fromCollect.get(i), list.get(i), list.get(i));
                }
                map.put(fromCollect.get(i), excelDataHighVO);
            }
            insertDataList.add(map);
        }
        excelShowDataVO.setInsertDataList(insertDataList);
        return excelShowDataVO;
    }

    /**
     * 新增数据打高亮标签
     * @param column
     * @param newValue
     * @param oldValue
     * @return
     */
    private ExcelDataHighVO buildExcelDataHighVo(String column, Object newValue, Object oldValue) {
        String oValue = Objects.isNull(oldValue) ? "" :oldValue.toString();
        ExcelDataHighVO excelDataHighVO = new ExcelDataHighVO();
        excelDataHighVO.setOldValue(oValue);
        excelDataHighVO.setViewType(ViewConstant.DEFAULT);
        if (Objects.isNull(newValue) || StrUtil.isBlank(newValue.toString()) || "NUll".equalsIgnoreCase(newValue.toString())) {
            excelDataHighVO.setNewValue("");
        } else {
            String nValue = newValue.toString();
            excelDataHighVO.setNewValue(nValue);
            if (StrUtil.isNotEmpty(nValue)) {
                if (!nValue.equals(oValue)) {
                    excelDataHighVO.setViewType(ViewConstant.CHANGE);
                }
            } else {
                if (!oValue.equals(nValue)) {
                    excelDataHighVO.setViewType(ViewConstant.CHANGE);
                }
            }
        }
        return excelDataHighVO;
    }

    private ExcelShowDataVO getStringResultData(String tableName, String schemaName, List<List<String>> returnDataList, List<String> fromCollect, List<String> primaryKeyColumnList, List<String> toCollect) throws SQLException {
        ExcelShowDataVO excelShowDataVO = new ExcelShowDataVO();
        List<Map<String, ExcelDataHighVO>> insertDataList = new ArrayList<>();
        List<Map<String, ExcelDataHighVO>> updateDataList = new ArrayList<>();
        for (List<String> list : returnDataList) {
            Map<String, ExcelDataHighVO> map = new LinkedHashMap<>();
            boolean dataFlag = true;
            for (int i = 0; i < toCollect.size(); i++) {
                ExcelDataHighVO excelDataHighVO = buildExcelDataHighVo(toCollect.get(i), list.get(i), list.get(i));
                map.put(toCollect.get(i), excelDataHighVO);
            }
            //新增数据
            if (list.get(list.size() - 1).equals("0")) {
                dataFlag = false;
            }
            if (dataFlag) {
                insertDataList.add(map);
            } else {
                Map<String, Object> maps = new HashMap<>();
                for (String s : map.keySet()) {
                    maps.put(s, map.get(s).getNewValue());
                }
                String indexWhere = Chat2DBContext.getSqlBuilder().getIndexWhere(tableName, schemaName, toCollect, primaryKeyColumnList, maps);
                ExecuteResult executeResult = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), indexWhere, new DefaultValueHandler());
                Map<String, ExcelDataHighVO> linkedHashMap = new LinkedHashMap<>();
                if (executeResult.getSuccess()) {
                    List<List<String>> dataList = executeResult.getDataList();
                    if (CollUtil.isNotEmpty(dataList)) {
                        List<String> keys = new ArrayList<>(map.keySet());
                        List<String> list1 = dataList.get(0);
                        List<Object> values = map.values().stream().map(ExcelDataHighVO::getNewValue).collect(Collectors.toList());
                        for (int i = 0; i < list1.size(); i++) {
                            ExcelDataHighVO excelDataHighVO = new ExcelDataHighVO();
                            if (StringUtils.isEmpty(list1.get(i)) && Objects.isNull(values.get(i))) {
                                excelDataHighVO =  buildExcelDataHighVo(keys.get(i), list1.get(i), list1.get(i));
                            } else if (StringUtils.isEmpty(list1.get(i)) && values.get(i) != null) {
                                if (values.get(i).toString().toUpperCase(Locale.ROOT).contains("NULL") || StringUtils.isEmpty(values.get(i).toString())) {
                                    excelDataHighVO =  buildExcelDataHighVo(keys.get(i), null, null);
                                } else {
                                    excelDataHighVO =  buildExcelDataHighVo(keys.get(i), values.get(i), null);
                                }
                            } else {
                                excelDataHighVO.setNewValue(values.get(i).toString());
                                excelDataHighVO.setOldValue(list1.get(i));
                                excelDataHighVO =  buildExcelDataHighVo(keys.get(i), values.get(i), list1.get(i));
                            }
                            linkedHashMap.put(keys.get(i), excelDataHighVO);
                        }
                    }
                }
                List<ExcelDataHighVO> objectList = new ArrayList<>(linkedHashMap.values());
                List<ExcelDataHighVO> excelDataHighVOS = objectList.stream().filter(t -> !ViewConstant.DEFAULT.equals(t.getViewType())).collect(Collectors.toList());
                if (CollUtil.isNotEmpty(excelDataHighVOS)) {
                    updateDataList.add(linkedHashMap);
                }
            }
        }
        excelShowDataVO.setInsertDataList(insertDataList);
        excelShowDataVO.setUpdateDataList(updateDataList);
        return excelShowDataVO;
    }


    /**
     * Excel预览数据保存
     */
    @Log(title = "导入管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importSQL")
    public DataResult<Boolean> importSQL(@RequestBody ExcelDataRequest request) {
        try {
            String fileName = request.getFileName();
            if (StrUtil.isBlank(fileName)) {
                throw new BusinessException("文件名称不能为空");
            }
            String localPath = HzbConfig.getProfile();
            String importUrl = localPath + StringUtils.substringAfter(fileName, Constants.RESOURCE_PREFIX);
            File file = new File(importUrl);
            if (!file.exists()) {
                throw new BusinessException("文件不存在");
            }
            String s = FileUtil.readUtf8String(importUrl);
            if (request.getIsSql()) {
                dropOrAddIdentityInsert(request.getSchemaName(), request.getName(), Boolean.FALSE, request.getDataSourceId());
            } else {
                log.info("SQL信息:{}", s);
                s = s.replaceAll("\t", " ").replaceAll("\n", " ").replaceAll("\r"," ");
            }
            String[] split = s.split(";");
            for (String sql : split) {
                if (StrUtil.isNotBlank(sql)) {
                    SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql.trim(), new DefaultValueHandler());
                }
            }
            // 设置commit 提交
            return DataResult.of(Boolean.TRUE);
        } catch (Exception e) {
            String[] split = e.getMessage().split("\n");
            List<String> list = Arrays.asList(split);
            String errorMas = "执行失败";
            if (CollUtil.isNotEmpty(list)) {
                errorMas = list.get(0);
            }
            throw new BusinessException(errorMas);
        }
        finally {
            if (request.getIsSql()) {
                dropOrAddIdentityInsert(request.getSchemaName(), request.getName(), Boolean.TRUE, request.getDataSourceId());
            }
            String key = CacheConstants.DROP_OR_ADD_IDENTITY_INSERT_KEY + request.getSchemaName() + request.getName();
            redisCache.deleteObject(key);
        }
    }


    /**
     * Excel预览数据保存
     */
    @Log(title = "导入视图", businessType = BusinessType.IMPORT)
    @PostMapping("/importViewSQL")
    public DataResult<String> importViewSQL(@RequestBody ExcelDataRequest request) {
        String fileName = request.getFileName();
        if (StrUtil.isBlank(fileName)) {
            throw new BusinessException("文件名称不能为空");
        }
        String localPath = HzbConfig.getProfile();
        String importUrl = localPath + StringUtils.substringAfter(fileName, Constants.RESOURCE_PREFIX);
        File file = new File(importUrl);
        if (!file.exists()) {
            throw new BusinessException("文件不存在");
        }
        String s = FileUtil.readUtf8String(importUrl);
        log.info("SQL信息:{}", s);
        s = s.replaceAll("\t", " ").replaceAll("\n", " ").replaceAll("\r"," ").trim();
        return DataResult.of(s);
    }




    /**
     * Excel预览数据保存
     */
    @Log(title = "导入管理", businessType = BusinessType.IMPORT)
    @PostMapping("/dataSave")
    public DataResult<String> dataSave(@Valid @RequestBody ExcelDataRequest request) {
        try {
            if (CollUtil.isEmpty(request.getColumns())) {
                throw new BusinessException("映射字段集为空");
            }
            if (Objects.isNull(request.getType())) {
                throw new BusinessException("执行类型缺失");
            }
            List<String> columns = new ArrayList<>();
            for (String column : request.getColumns()) {
                if (StrUtil.isNotBlank(column)) {
                    columns.add(column);
                }
            }
            int size = columns.size();
            Set<String> strings = new HashSet<>(columns);
            if (size != strings.size()) {
                throw new BusinessException("映射字段集字段有重复");
            }
            request.setColumns(columns);
            ExcelImportRequest excelImport = new ExcelImportRequest();
            BeanUtils.copyProperties(request, excelImport);
            if (request.getType().equals(1)) {
                excelImport.setDataType(ExcelDataTypeEnum.ADD.name());
                excelImport.setExecuteType(ExcelDataTypeEnum.ADD.name());
            } else {
                excelImport.setDataType(ExcelDataTypeEnum.UPDATE.name());
                excelImport.setExecuteType(ExcelDataTypeEnum.UPDATE.name());
            }
            DataResult<ExcelShowDataPageVo> excelShowDataPageVoDataResult = previewDataAll(excelImport);
            if (null == excelShowDataPageVoDataResult.getData() || CollUtil.isEmpty(excelShowDataPageVoDataResult.getData().getDataList())) {
                throw new BusinessException("暂无数据");
            }
            ExcelShowDataPageVo data = excelShowDataPageVoDataResult.getData();
            List<Map<String, Object>> dataList = data.getDataList();
            ExcelDataVo excelDataVo = new ExcelDataVo();
            Table table = new Table();
            table.setSchemaName(request.getSchemaName());
            table.setName(request.getName());
            ExcelImportRequest excelImportRequest = new ExcelImportRequest();
            excelImportRequest.setDataSourceId(request.getDataSourceId());
            excelImportRequest.setSchemaName(request.getSchemaName());
            excelImportRequest.setName(request.getName());
            excelDataVo.setSchema(request.getSchemaName());
            excelDataVo.setName(request.getName());
            Table tableDataInfo = getTableDataInfo(request.getDataSourceId(), request.getName(), request.getSchemaName(), Boolean.TRUE);
            if (CollUtil.isEmpty(tableDataInfo.getColumnList())) {
                throw new BusinessException("目的表字段获取失败");
            }
            table.setColumnList(tableDataInfo.getColumnList());
            List<Map<String, Object>> insertDataList = new ArrayList<>();
            if(ExcelDataTypeEnum.ADD.name().equals(excelImport.getExecuteType())) {
                insertDataList = dataList;
                request.setIndexList(data.getDataIndex());
            }
            List<Map<String, Object>> updateDataList = new ArrayList<>();
            if(ExcelDataTypeEnum.UPDATE.name().equals(excelImport.getExecuteType())) {
                updateDataList = dataList;
                request.setIndexList(data.getDataIndex());
                for (Map<String, Object> stringObjectMap : updateDataList) {
                    stringObjectMap.remove("index");
                }
            }
            dropOrAddIdentityInsert(request.getSchemaName(), request.getName(), Boolean.FALSE, request.getDataSourceId());
            if (CollUtil.isNotEmpty(insertDataList)) {
                redisCache.deleteObject(CacheConstants.PREVIEW_DATA_ERROR_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.ADD.name());
                List<String> sqlList = Chat2DBContext.getSqlBuilder().excelDataIns(table, insertDataList, request.getColumns());
                buildExcelVo(sqlList, insertDataList, request.getIndexList(), request.getSchemaName(), request.getName(), ExcelDataTypeEnum.ADD.name(), excelDataVo, Boolean.FALSE);
            }
            if (CollUtil.isNotEmpty(updateDataList)) {
                redisCache.deleteObject(CacheConstants.PREVIEW_DATA_ERROR_KEY + request.getSchemaName() + request.getName() + ExcelDataTypeEnum.UPDATE.name());
                List<String> sqlList = Chat2DBContext.getSqlBuilder().excelDataForUp(table, updateDataList, request.getColumns());
                buildExcelVo(sqlList, updateDataList, request.getIndexList(), request.getSchemaName(), request.getName(), ExcelDataTypeEnum.UPDATE.name(), excelDataVo, request.getIsErrorExecute());
            }
            dropOrAddIdentityInsert(request.getSchemaName(), request.getName(), Boolean.TRUE,  request.getDataSourceId());
            String key = CacheConstants.DROP_OR_ADD_IDENTITY_INSERT_KEY + request.getSchemaName() + request.getName();
            redisCache.deleteObject(key);
            String addSuccess = excelDataVo.getAddSuccess() == null ? "0" : excelDataVo.getAddSuccess() + "";
            String addFail = excelDataVo.getAddFail() == null ? "0" : excelDataVo.getAddFail() + "";
            String updateSuccess = excelDataVo.getUpdateSuccess() == null ? "0" : excelDataVo.getUpdateSuccess() + "";
            String updateFail = excelDataVo.getUpdateFail() == null ? "0" : excelDataVo.getUpdateFail() + "";
            String resultMessage = "新增成功" + addSuccess + "条, "
                    + "新增失败" + addFail + "条, "
                    + "修改成功" + updateSuccess + "条, "
                    + "修改失败" + updateFail + "条";
            return DataResult.of(resultMessage);
        } finally {
            dropOrAddIdentityInsert(request.getSchemaName(), request.getName(), Boolean.TRUE,  request.getDataSourceId());
        }
    }

    public void dropOrAddIdentityInsert(String schemaName, String tableName, Boolean ref, Long dataSourceId) {
        String key = CacheConstants.DROP_OR_ADD_IDENTITY_INSERT_KEY + schemaName + tableName;
        try {
            if (ref) {
                List<String> columnList = new ArrayList<>();
                if (redisCache.hasKey(key)) {
                    columnList = redisCache.getCacheList(key);
                }
                if (CollUtil.isEmpty(columnList)) {
                    return;
                }
                List<String> sqlList = Chat2DBContext.getMetaData().getSqlBuilder().dropOrAddIdentityInsert(schemaName, tableName, columnList, ref);
                for (String s : sqlList) {
                    SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), s, new DefaultValueHandler());
                }
            } else {
                TableQueryParam param = new TableQueryParam();
                param.setDataSourceId(dataSourceId);
                param.setTableName(tableName);
                param.setSchemaName(schemaName);
                DataResult<Table> query = tableService.query(param, null);
                List<TableColumn> collect = new ArrayList<>();
                if (query.getSuccess() && Objects.nonNull(query.getData())) {
                    Table data = query.getData();
                    collect = data.getColumnList().stream().filter(TableColumn::getAutoIncrement).collect(Collectors.toList());
                }
                if (CollUtil.isEmpty(collect)) {
                    return;
                }
//                List<TableColumn> columns = Chat2DBContext.getMetaData().columns(Chat2DBContext.getConnection(), null, schemaName, tableName);
//                List<TableColumn> collect = columns.stream().filter(TableColumn::getAutoIncrement).collect(Collectors.toList());
                List<String> columnList = collect.stream().map(TableColumn::getName).collect(Collectors.toList());
                if (CollUtil.isNotEmpty(columnList)) {
                    List<String> sqlList = Chat2DBContext.getMetaData().getSqlBuilder().dropOrAddIdentityInsert(schemaName, tableName, null, ref);
                    for (String s : sqlList) {
                        SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), s, new DefaultValueHandler());
                    }
                    redisCache.setCacheList(key, columnList);
                }
            }
        } catch (SQLException e) {
            log.error(e.getMessage());
            redisCache.deleteObject(key);
            throw new BusinessException("去除自增异常");
        }
    }


    public void buildExcelVo(List<String> sqlList, List<Map<String, Object>> dataList, List<String> indexList, String schemaName, String tableName, String dataType, ExcelDataVo excelDataVo, Boolean ref) {
        int success = 0;
        int fail = 0;
        for (int i = 0; i < sqlList.size(); i++) {
            Map<String, Object> stringObjectMap = new LinkedHashMap<>();
            try {
                Map<String, Object> objectMap = dataList.get(i);
                stringObjectMap.putAll(objectMap);
                ExecuteResult executeResult = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sqlList.get(i), new DefaultValueHandler());
                if (executeResult.getSuccess()) {
                    success++;
                    redisCache.setCacheMapValue(CacheConstants.PREVIEW_DATA_SUCCESS_KEY + schemaName + tableName + dataType, i + "", JSON.toJSONString(stringObjectMap));
                    String key;
                    if (ExcelDataTypeEnum.ADD.name().equals(dataType)) {
                        key = CacheConstants.PREVIEW_DATA_ADD_KEY + schemaName + tableName;
                    } else {
                        key = CacheConstants.PREVIEW_DATA_UPDATE_KEY + schemaName + tableName;
                    }
                    redisCache.deleteCacheMapValue(key, indexList.get(i));
                } else {
                    fail++;
                    log.debug("sql执行失败,error:{}", executeResult.getMessage());
                    stringObjectMap.put("errorMessage", executeResult.getMessage());
                    redisCache.setCacheMapValue(CacheConstants.PREVIEW_DATA_ERROR_KEY + schemaName + tableName + dataType, i + "", JSON.toJSONString(stringObjectMap));
                }
            } catch (SQLException e) {
                fail++;
                log.debug("sql执行失败,error:{}", e.getMessage());
                stringObjectMap.put("errorMessage", e.getMessage());
                redisCache.setCacheMapValue(CacheConstants.PREVIEW_DATA_ERROR_KEY + schemaName + tableName + dataType, i + "", JSON.toJSONString(stringObjectMap));
                // 失败后不执行
                if (ref) {
                    break;
                }
            }
        }
        if (ExcelDataTypeEnum.ADD.name().equals(dataType)) {
            excelDataVo.setAddSuccess(success);
            excelDataVo.setAddFail(fail);
        } else {
            excelDataVo.setUpdateSuccess(success);
            excelDataVo.setUpdateFail(fail);
        }
    }

    /**
     * 通用上传请求（单个）
     */
    @PostMapping("/upload")
    public AjaxResult uploadFile(MultipartFile file) throws Exception {
        ;
        try {
            // 上传文件路径
            String filePath = HzbConfig.getUploadPath();
            // 上传并返回新文件名称
            String fileName = FileUploadUtils.upload(filePath, file);
            String url = serverConfig.getUrl() + fileName;
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", fileName);
            ajax.put("newFileName", FileUtils.getName(fileName));
            ajax.put("originalFilename", file.getOriginalFilename());
            String fileSize = "";
            DecimalFormat decimalFormat = new DecimalFormat("0.00");
            if (file.getSize() / 1024 / 1024 > 1L) {
                fileSize = decimalFormat.format((float) file.getSize() / 1024 / 1024) + "MB";
            } else {
                fileSize = decimalFormat.format((float) file.getSize() / 1024) + "KB";
            }
            ajax.put("fileSize", fileSize);
            return ajax;
        } catch (Exception e) {
            log.error("上传失败信息:{}", e.getMessage());
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/bizUploadFile")
    public AjaxResult bizUploadFile(MultipartFile file) throws Exception {
        try {
            // 上传文件路径
            String filePath = HzbConfig.getUploadPath();
            // 上传并返回新文件名称
            String fileName = FileUploadUtils.upload(filePath, file).replaceFirst("/profile/upload", "");
            String url = serverConfig.getUrl() + fileName;
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", fileName);
            ajax.put("newFileName", FileUtils.getName(fileName));
            ajax.put("originalFilename", file.getOriginalFilename());
            return ajax;
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 根据文件路径删除已经上传的文件
     */
    @GetMapping("/deleteFile")
    public AjaxResult deleteUploadFile(String filePath) throws Exception {
        try {
            FileUtils.deleteFile(HzbConfig.getUploadPath() + filePath);
            return AjaxResult.success("删除成功");
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }


    /**
     * 通用上传请求（多个）
     */
    @PostMapping("/uploads")
    public AjaxResult uploadFiles(List<MultipartFile> files) throws Exception {
        try {
            // 上传文件路径
            String filePath = HzbConfig.getUploadPath();
            List<String> urls = new ArrayList<String>();
            List<String> fileNames = new ArrayList<String>();
            List<String> newFileNames = new ArrayList<String>();
            List<String> originalFilenames = new ArrayList<String>();
            for (MultipartFile file : files) {
                // 上传并返回新文件名称
                String fileName = FileUploadUtils.upload(filePath, file);
                String url = serverConfig.getUrl() + fileName;
                urls.add(url);
                fileNames.add(fileName);
                newFileNames.add(FileUtils.getName(fileName));
                originalFilenames.add(file.getOriginalFilename());
            }
            AjaxResult ajax = AjaxResult.success();
            ajax.put("urls", StringUtils.join(urls, FILE_DELIMETER));
            ajax.put("fileNames", StringUtils.join(fileNames, FILE_DELIMETER));
            ajax.put("newFileNames", StringUtils.join(newFileNames, FILE_DELIMETER));
            ajax.put("originalFilenames", StringUtils.join(originalFilenames, FILE_DELIMETER));
            return ajax;
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 本地资源通用下载
     */
    @GetMapping("/download/resource")
    public void resourceDownload(String resource, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try {
            if (!FileUtils.checkAllowDownload(resource)) {
                throw new Exception(StringUtils.format("资源文件({})非法，不允许下载。 ", resource));
            }
            // 本地资源路径
            String localPath = HzbConfig.getProfile();
            // 数据库资源地址
            String downloadPath = localPath + StringUtils.substringAfter(resource, Constants.RESOURCE_PREFIX);
            // 下载名称
            String downloadName = StringUtils.substringAfterLast(downloadPath, "/");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            FileUtils.writeBytes(downloadPath, response.getOutputStream());
        } catch (Exception e) {
            log.error("下载文件失败", e);
        }
    }
}
