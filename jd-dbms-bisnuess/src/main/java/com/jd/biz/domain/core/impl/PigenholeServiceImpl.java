package com.jd.biz.domain.core.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Session;
import cn.hutool.db.ds.simple.SimpleDataSource;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.DmlTableRequest;
import com.jd.biz.controller.rdb.request.ExportPigenholeRequest;
import com.jd.biz.controller.rdb.request.ImportPigenholeRequest;
import com.jd.biz.controller.rdb.vo.TableImportVo;
import com.jd.biz.domain.api.enums.TaskStatusEnum;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.PigenholeService;
import com.jd.biz.domain.core.util.NoModelDataAddListener;
import com.jd.biz.domain.core.util.NoModelDataDelListener;
import com.jd.biz.domain.repository.entity.PigenholeDO;
import com.jd.biz.domain.repository.mapper.PigenholeMapper;
import com.jd.common.config.HzbConfig;
import com.jd.common.constant.CacheConstants;
import com.jd.common.constant.Constants;
import com.jd.common.core.redis.RedisCache;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.common.model.LoginUser;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.utils.file.FileUtils;
import com.jd.plugin.dm.type.DMColumnTypeEnum;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Header;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.sql.SQLExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
@Service
public class PigenholeServiceImpl extends ServiceImpl<PigenholeMapper, PigenholeDO> implements PigenholeService {

    @Autowired
    private RedisCache redisCache;

    @Resource
    private RdbWebConverter rdbWebConverter;

    @Autowired
    private DlTemplateService dlTemplateService;

    @Value("${pigenhole.uploadUrl}")
    private String uploadUrl;

    @Override
    public String exportCheckTable(ExportPigenholeRequest exportPigenholeRequest) throws Exception {
        if (StrUtil.isAllBlank(exportPigenholeRequest.getPigenholeId())) {
            throw new BusinessException("参数缺失");
        }
        if (CollUtil.isEmpty(exportPigenholeRequest.getFilterValue())) {
            throw new BusinessException("参数缺失");
        }
        String msg = null;
        LambdaQueryWrapper<PigenholeDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PigenholeDO::getId, exportPigenholeRequest.getPigenholeId());
        PigenholeDO pigenholeDO1 = this.baseMapper.selectOne(queryWrapper);
        String[] split = pigenholeDO1.getTables().split(",");
        String s = pigenholeDO1.getFilter() + " IN ('" + String.join("','", exportPigenholeRequest.getFilterValue()).trim() + "')";
        String fileName = null;
        String fileNameUrl = null;
        ExcelWriter writerBuilder = null;
        try {
            fileName = URLEncoder.encode(
                            pigenholeDO1.getUserName() + "_" + pigenholeDO1.getFilter() + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER),
                            StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
            fileNameUrl = uploadUrl + "/" + fileName;
            ExcelWriterBuilder write = EasyExcel.write(fileNameUrl);
            writerBuilder = write.build();
            long mesSize = Long.parseLong(exportPigenholeRequest.getMaxSize()) * 1024 * 1024;
            buildExcel(pigenholeDO1, split, exportPigenholeRequest, writerBuilder, s);
            writerBuilder.finish();
            File file = new File(fileNameUrl);
            if (file.exists() && file.isFile()) {
                if (file.length() > mesSize) {
                    if (file.exists() && file.isFile()) {
                        file.delete();
                    }
                    msg = "超出文件大小限制";
                    return msg;
                }
            }
            if (file.exists() && file.isFile()) {
                file.delete();
            }
            boolean edit = StrUtil.isNotBlank(exportPigenholeRequest.getUploadUrl());
            if (edit) {
                fileNameUrl = exportPigenholeRequest.getUploadUrl()  + "/" + fileName + ExcelTypeEnum.XLSX.getValue();
                write = EasyExcel.write(fileNameUrl);
                writerBuilder = write.build();
                buildExcel(pigenholeDO1, split, exportPigenholeRequest, writerBuilder, s);
                writerBuilder.finish();
                msg = "导出成功,请在服务器指定路径查看";
            }
        } finally {
            if (Objects.nonNull(writerBuilder)) {
                writerBuilder.finish();
            }
        }
        return msg;
    }

    @Override
    public void exportTable(ExportPigenholeRequest exportPigenholeRequest, HttpServletResponse response) {
        if (StrUtil.isAllBlank(exportPigenholeRequest.getPigenholeId())) {
            throw new BusinessException("参数缺失");
        }
        if (CollUtil.isEmpty(exportPigenholeRequest.getFilterValue())) {
            throw new BusinessException("参数缺失");
        }
        boolean edit = StrUtil.isBlank(exportPigenholeRequest.getUploadUrl());
        LambdaQueryWrapper<PigenholeDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PigenholeDO::getId, exportPigenholeRequest.getPigenholeId());
        PigenholeDO pigenholeDO1 = this.baseMapper.selectOne(queryWrapper);
        String[] split = pigenholeDO1.getTables().split(",");
        String s = pigenholeDO1.getFilter() + " IN ('" + String.join("','", exportPigenholeRequest.getFilterValue()).trim() + "')";
        String fileName = null;
        ExcelWriter writerBuilder = null;
        try {
            fileName =  URLEncoder.encode(
                            pigenholeDO1.getUserName() + "_" + pigenholeDO1.getFilter() + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER),
                            StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
            if (edit){
                response.setCharacterEncoding("utf-8");
                response.setContentType("application/vnd.ms-excel");
                response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
                writerBuilder = EasyExcel.write(response.getOutputStream())
                        .charset(StandardCharsets.UTF_8)
                        .excelType(ExcelTypeEnum.XLSX).build();
                buildExcel(pigenholeDO1, split, exportPigenholeRequest, writerBuilder, s);
            }
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        } finally {
            if (Objects.nonNull(writerBuilder)) {
                writerBuilder.finish();
            }
        }
    }

    public void buildExcel(PigenholeDO pigenholeDO1, String[] split, ExportPigenholeRequest exportPigenholeRequest, ExcelWriter writerBuilder, String s) {
        for (int i = 0; i <= split.length; i++) {
            WriteSheet writeSheet = new WriteSheet();
            if (i==0){
                List<String> filterName = new ArrayList<>();
                filterName.add(pigenholeDO1.getFilter());
                List<List<String>> head = new ArrayList<>(1);
                head.add(filterName);
                List<String> list = new ArrayList<>(1);
                list.add(String.join(",", exportPigenholeRequest.getFilterValue()).trim());
                List<List<String>> datas = new ArrayList<>(1);
                datas.add(list);
                writeSheet = EasyExcel.writerSheet(i).head(head).build();
                writerBuilder.write(datas, writeSheet);
            } else {
                String sql = "select * from " + pigenholeDO1.getUserName() + "." + split[i-1] + " where " + s;
                List<List<String>> writeDataList = new ArrayList<>();
//                List<List<Object>> headers = new ArrayList<>();
                List<List<Header>> headers = new ArrayList<>();
                boolean ref = false;
                try{
                    //                        headers.add(EasyCollectionUtils.toList(headerList, Header::getName));
                    SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql,
                            headers::add,
                            writeDataList::add,
                            false, new DefaultValueHandler());

                }catch (Exception e){
                    log.warn("导出归档执行SQL异常,sql:{},异常:{}", sql, e);
                    ref = true;
                    String[] split1 = e.getMessage().split(":");
                    String s1 = split1[split1.length - 1].replaceAll(" ", "").replace("[","").replace("]","");
                    writeSheet = EasyExcel.writerSheet(i, s1).build();
                    writerBuilder.write(writeDataList, writeSheet);
                }
                if (!ref) {
                    List<List<String>> headerss = new ArrayList<>();
                    headers.forEach(a -> {
                        a.forEach(e -> {
                            List<String> list = new ArrayList<>();
                            list.add(e.getName());
                            headerss.add(list);
                        });
                    });
                    List<Header> headersValues = headers.get(0);
                    List<Integer> index = new ArrayList<>();
                    for (int j = 0; j < headersValues.size(); j++) {
                        if (DMColumnTypeEnum.BLOB.name().equals(headersValues.get(i).getDataType())) {
                            index.add(j-1);
                        }
                    }
                    List<List<Object>> dataList = new ArrayList<>();
                    for (List<String> list : writeDataList) {
                        List<Object> value = new ArrayList<>();
                        for (int j = 0; j < list.size(); j++) {
                            if (index.contains(j)) {
                                value.add(Base64.getDecoder().decode(list.get(j)));
                            } else {
                                value.add(list.get(j));
                            }
                        }
                        dataList.add(value);
                    }
                    writeSheet = EasyExcel.writerSheet(i, pigenholeDO1.getUserName() + "." + split[i-1]).head(headerss).build();
                    writerBuilder.write(dataList, writeSheet);
                }
            }
        }
    }

    @Override
    public DataResult importTable(ExportPigenholeRequest request) {

        LoginUser loginUser = ContextUtils.getLoginUser();
        String key = CacheConstants.IMPORT_TABLE_KEY + loginUser.getId() + ":" + StrUtil.nullToEmpty(request.getId()) + ":" + request.getDataSourceId();
        if (redisCache.hasKey(key)) {
            return DataResult.of(redisCache.getCacheObject(key));
        }
        if (StrUtil.isEmpty(request.getFilePath())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "文件路径为空");
        }

        LambdaQueryWrapper<PigenholeDO> queryWrapper = new LambdaQueryWrapper<>();
//        queryWrapper.eq(PigenholeDO::getId,request.getPigengoleId());
        PigenholeDO pigenholeDO = this.baseMapper.selectOne(queryWrapper);
        // 本地资源路径
        String localPath = HzbConfig.getProfile();
        // 数据库资源地址
        String importUrl = localPath + com.jd.common.utils.StringUtils.substringAfter(request.getFilePath(), Constants.RESOURCE_PREFIX);
        final File file = FileUtil.newFile(importUrl);
        if (!file.exists()) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "文件不存在");
        }
        DataResult dataResult = new DataResult();

        ExcelReader reader = ExcelUtil.getReader(importUrl);
        List<Sheet> sheets = reader.getSheets();
        reader.close();
        TableImportVo vo = new TableImportVo();
        vo.setExported(0);
        vo.setErrorNum(0);
        vo.setMessage(Lists.newLinkedList());
        vo.setDataSourceId(request.getDataSourceId());
        vo.setDatabaseName(request.getDatabaseName());
        vo.setSchemaName(request.getSchemaName());
        vo.setUserId(loginUser.getId());
        vo.setStatus(TaskStatusEnum.INIT.name());
        redisCache.setCacheObject(key, vo);
        Connection connection = Chat2DBContext.getConnection();
        int sheetNum = 0;
        for (Sheet sheet : sheets) {
            if (sheetNum==0){
                String numericCellValue = sheet.getRow(0).getCell(0).getStringCellValue();
                request.setId(numericCellValue);
                sheetNum++;
                continue;
            }
            if (sheet.getLastRowNum() < 1) {
                continue;
            }
            String sql = "create table " + sheet.getSheetName() + "_bak as " + "select * from " + sheet.getSheetName() + " where " + pigenholeDO.getFilter() + " ='" + request.getId().toString() + "';";
            ExecuteResult execute = new ExecuteResult();
            try {
                execute = SQLExecutor.getInstance().execute(connection, "select * from " + sheet.getSheetName(), new DefaultValueHandler());
            } catch (SQLException e) {
                e.printStackTrace();
            }
            List<String> collect1 = execute.getHeaderList().stream().map(Header::getName).collect(Collectors.toList());
            String collect2 = collect1.stream().collect(Collectors.joining("\",\""));
            StringBuilder insertSql = new StringBuilder();
            insertSql.append("insert into ").append(sheet.getSheetName()).append("(\"").append(collect2).append("\")").append(" values");
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row datas = sheet.getRow(i);
                insertSql.append("('");
                for (int j = 0; j < collect1.size(); j++) {
                    if (Validator.isNotEmpty(datas.getCell(j))){
                        if (datas.getCell(j).getCellType().name().equals("NUMERIC")){
                            insertSql.append((int)datas.getCell(j).getNumericCellValue());
                        }
                        if (datas.getCell(j).getCellType().name().equals("STRING")){
                            insertSql.append(datas.getCell(j).getStringCellValue().replaceAll("'", "''"));
                        }
                    }
                    insertSql.append("','");
//                    insertSql.append(Validator.isNotEmpty(datas.getCell(j)) ? (datas.getCell(j).getCellType().name().equals("NUMERIC") ? (int)datas.getCell(j).getNumericCellValue() : datas.getCell(j).getStringCellValue().replaceAll("'", "''")) : "").append("','");
                }
                insertSql.delete(insertSql.length() - 2, insertSql.length());
                insertSql.append("),");
            }
            insertSql.deleteCharAt(insertSql.length() - 1);
            insertSql.append(";");
            String deleteSql = "delete from " + sheet.getSheetName() + " where " + pigenholeDO.getFilter() + " ='" + request.getId() + "'";
            String dropSql = "drop table " + sheet.getSheetName() + "_bak;";
//            String copyAndDeleteSql = sql + collect + "\n" + deleteSql + "\n" + insertSql.toString() + "\n" + dropSql;
            try {
                SQLExecutor.getInstance().execute(connection, "SET IDENTITY_INSERT " + sheet.getSheetName() + " ON;", new DefaultValueHandler());
            } catch (SQLException e) {
                e.printStackTrace();
            }
            try {
                connection.setAutoCommit(false);
                SQLExecutor.getInstance().execute(connection, sql, new DefaultValueHandler());
                SQLExecutor.getInstance().execute(connection, deleteSql, new DefaultValueHandler());
                SQLExecutor.getInstance().execute(connection, insertSql.toString(), new DefaultValueHandler());
            } catch (SQLException e) {
                vo.setStatus(TaskStatusEnum.ERROR.name());
                vo.setMessage(Arrays.asList("文件导入失败"));
                dataResult.setErrorMessage(StrUtil.isNotBlank(dataResult.getErrorMessage()) ? dataResult.getErrorMessage() : "" + sheet.getSheetName() + "导入失败!" + e.getMessage());
                e.printStackTrace();
            }
            try {
                SQLExecutor.getInstance().execute(connection, dropSql, new DefaultValueHandler());
            } catch (SQLException e) {
                e.printStackTrace();
            }
            try {
                if (StrUtil.equals(vo.getStatus(), TaskStatusEnum.ERROR.name())) {
                    connection.rollback();
                    redisCache.deleteObject(key);
                } else {
                    connection.commit();
                    vo.setStatus(TaskStatusEnum.FINISH.name());
                    redisCache.setCacheObject(key, vo, 10, TimeUnit.SECONDS);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        try{
            if(connection!=null){
                connection.close();
            }
        }catch (SQLException e) {
            log.error("close connection error:{}",e);
        }
        ContextUtils.removeContext();
        Chat2DBContext.removeContext();
        FileUtils.deleteFile(importUrl);
        if (StrUtil.equals(vo.getStatus(), TaskStatusEnum.ERROR.name())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, dataResult.errorMessage());
        }
        return DataResult.of(vo);
    }

    @Override
    public DataResult testImport(ImportPigenholeRequest request) {
        // 数据库资源地址
        String localPath = HzbConfig.getProfile();
        String importUrl = localPath + com.jd.common.utils.StringUtils.substringAfter(request.getFilePath(), Constants.RESOURCE_PREFIX);
        final File file = FileUtil.newFile(importUrl);
        String extName = FileUtil.extName(file);
        if (!Constants.xlsx.equals(extName) && !Constants.xls.equals(extName)) {
            throw new BusinessException("文件格式错误");
        }
        Session session = null;
        com.alibaba.excel.ExcelReader reader = null;
        LoginUser loginUser = ContextUtils.getLoginUser();
        String key = CacheConstants.IMPORT_TABLE_KEY + loginUser.getId() + ":" + StrUtil.nullToEmpty(request.getId()) + ":" + request.getDataSourceId();
        try{
            reader = EasyExcel.read(importUrl).build();
            if (redisCache.hasKey(key)) {
                return DataResult.of(redisCache.getCacheObject(key));
            }
            if (StrUtil.isEmpty(request.getFilePath())) {
                return DataResult.error(EasyToolsConstant.ERROR_CODE, "文件路径为空");
            }
            LambdaQueryWrapper<PigenholeDO> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PigenholeDO::getId,request.getPigenholeId());
            PigenholeDO pigenholeDO = this.baseMapper.selectOne(queryWrapper);
            TableImportVo vo = new TableImportVo(request.getDataSourceId(),request.getDatabaseName(),request.getSchemaName(),TaskStatusEnum.INIT.name(),
                    0,0,loginUser.getId(),Lists.newLinkedList());
            redisCache.setCacheObject(key, vo, 60, TimeUnit.SECONDS);
            if (!file.exists()) {
                return DataResult.error(EasyToolsConstant.ERROR_CODE, "文件不存在");
            }
            List<ReadSheet> readSheets = reader.excelExecutor().sheetList();
            List<Object> objects = EasyExcel.read(importUrl).sheet(0).headRowNumber(0).doReadSync();
            if (objects.size() > 2 || readSheets.size() < 2) {
                throw new BusinessException("当前excel筛选条件异常");
            }
            Map<Integer,String> o = (Map<Integer,String>) objects.get(1);
            String s = o.get(0);
            // 封装DB
            ConnectInfo connectInfo = Chat2DBContext.getConnectInfo();
//            String jdbcDriver = Chat2DBContext.PLUGIN_MAP.get(connectInfo.getDbType()).getDBConfig().getDriverConfigList().get(0).getJdbcDriverClass();
            SimpleDataSource dataSource = new SimpleDataSource(connectInfo.getUrl(),connectInfo.getUser(),connectInfo.getPassword(),connectInfo.getDriver());
            session = Session.create(dataSource);
            for (int i = readSheets.size() -1;  0 < i; i--) {
                DmlTableRequest dmlTableRequest = new DmlTableRequest();
                String s1 = readSheets.get(i).getSheetName().substring(readSheets.get(i).getSheetName().indexOf(".")+1);
                dmlTableRequest.setTableName(s1);
                dmlTableRequest.setSchemaName(pigenholeDO.getUserName());
                dmlTableRequest.setDataSourceId(request.getDataSourceId());
                DlExecuteParam param = rdbWebConverter.request2param(dmlTableRequest);
                List<Header> headerList = dlTemplateService.executeSelectTable(param).getData().get(0).getHeaderList();
                List<String> isAutoColumn = new ArrayList<>();

                for (Header column : headerList) {
                    if (Validator.isNotEmpty(column.getAutoIncrement())&&column.getAutoIncrement()==1){
                        isAutoColumn.add(column.getName());
                    }
                }
                EasyExcel.read(importUrl,new NoModelDataDelListener(Db.use(dataSource),pigenholeDO.getFilter(),s,readSheets.get(i).getSheetName(),isAutoColumn,session,vo)).sheet(i).headRowNumber(0).doRead();
            }
            for (int i = 1; i < readSheets.size(); i++) {
                DmlTableRequest dmlTableRequest = new DmlTableRequest();
                String s1 = readSheets.get(i).getSheetName().substring(readSheets.get(i).getSheetName().indexOf(".")+1);
                dmlTableRequest.setTableName(s1);
                dmlTableRequest.setSchemaName(pigenholeDO.getUserName());
                dmlTableRequest.setDataSourceId(request.getDataSourceId());
                DlExecuteParam param = rdbWebConverter.request2param(dmlTableRequest);
                List<Header> headerList = dlTemplateService.executeSelectTable(param).getData().get(0).getHeaderList();
                List<String> isAutoColumn = new ArrayList<>();

                for (Header column : headerList){
                    if (Validator.isNotEmpty(column.getAutoIncrement())&&column.getAutoIncrement()==1){
                        isAutoColumn.add(column.getName());
                    }
                }
                EasyExcel.read(importUrl,new NoModelDataAddListener(Db.use(dataSource),pigenholeDO.getFilter(),s,readSheets.get(i).getSheetName(),isAutoColumn,session,vo)).sheet(i).headRowNumber(0).doRead();
            }
            vo.setStatus(TaskStatusEnum.FINISH.name());
            redisCache.setCacheObject(key, vo, 10, TimeUnit.SECONDS);
            if (vo.getErrorNum()>0){
                String collect = String.join(" ", vo.getMessage());
                return DataResult.error(EasyToolsConstant.ERROR_CODE,collect);
            }else{
                return DataResult.of(vo);
            }
        } catch (Exception e) {
            redisCache.deleteObject(key);
            log.error("归档导入异常:{}", e.getMessage());
            throw new BusinessException("当前excel数据异常");
        } finally {
            if (Validator.isNotEmpty(session)){
                session.close();
            }
            if (reader != null) {
                reader.close();
            }
            file.delete();
        }


    }

    @Override
    public List<PigenholeDO> getPigengole() {
        List<PigenholeDO> pigenholeDO = this.baseMapper.selectList(new LambdaQueryWrapper<>());
        return pigenholeDO;
    }

    @PostConstruct
    public void initDictUrl() {
        final File file = FileUtil.newFile(uploadUrl);
        if (!file.exists()) {
            boolean mkdirs = file.mkdirs();
            if (!mkdirs) {
                log.error("初始化文件夹失败,创建文件路径:" + uploadUrl);
                throw new RuntimeException("创建文件夹失败");
            }
        }
    }

    @Override
    public DataResult<List<String>> getDictUrl() {
        List<String> list = new ArrayList<>();
        final File file = FileUtil.newFile(uploadUrl);
        if (file.exists()) {
            File[] files = file.listFiles();
            assert files != null;
            for (File file1 : files) {
                if (file1.isDirectory()) {
                    list.add(file1.getAbsolutePath().replace("\\", "/"));
                }
            }
        }
        return DataResult.of(list);
    }


}
