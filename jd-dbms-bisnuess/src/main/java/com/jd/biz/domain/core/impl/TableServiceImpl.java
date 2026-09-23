package com.jd.biz.domain.core.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.ConstraintInfoRequest;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.controller.rdb.enums.DMIndexTypeEnum;
import com.jd.biz.controller.rdb.enums.ResultType;
import com.jd.biz.controller.rdb.request.DmlTableRequest;
import com.jd.biz.controller.rdb.request.TableColumnMappingRequest;
import com.jd.biz.controller.rdb.request.TableImportRequest;
import com.jd.biz.controller.rdb.request.TypeQueryRequest;
import com.jd.biz.controller.rdb.vo.ColumnWHVO;
import com.jd.biz.controller.rdb.vo.TableImportVo;
import com.jd.biz.domain.api.enums.TableVectorEnum;
import com.jd.biz.domain.api.enums.TaskStatusEnum;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.api.param.DmlSqlCopyParam;
import com.jd.biz.domain.api.param.DropParam;
import com.jd.biz.domain.api.param.PinTableParam;
import com.jd.biz.domain.api.param.ShowCreateTableParam;
import com.jd.biz.domain.api.param.TablePageQueryParam;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.param.TableSelector;
import com.jd.biz.domain.api.param.TableVectorParam;
import com.jd.biz.domain.api.param.TypeQueryParam;
import com.jd.biz.domain.api.service.DlTemplateService;
import com.jd.biz.domain.api.service.PinService;
import com.jd.biz.domain.api.service.TableService;
import com.jd.biz.domain.core.cache.CacheManage;
import com.jd.biz.domain.core.converter.PinTableConverter;
import com.jd.biz.domain.core.converter.TableConverter;
import com.jd.biz.domain.repository.entity.TableCacheDO;
import com.jd.biz.domain.repository.entity.TableCacheVersionDO;
import com.jd.biz.domain.repository.entity.TableVectorMappingDO;
import com.jd.biz.domain.repository.mapper.TableCacheMapper;
import com.jd.biz.domain.repository.mapper.TableCacheVersionMapper;
import com.jd.biz.domain.repository.mapper.TableVectorMappingMapper;
import com.jd.common.config.HzbConfig;
import com.jd.common.constant.CacheConstants;
import com.jd.common.constant.Constants;
import com.jd.common.constant.GenConstants;
import com.jd.common.core.redis.RedisCache;
import com.jd.common.exception.ServiceException;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.common.model.Context;
import com.jd.common.tools.common.model.LoginUser;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.utils.file.FileUtils;
import com.jd.spi.DBManage;
import com.jd.spi.MetaData;
import com.jd.spi.SqlBuilder;
import com.jd.spi.enums.ConstraintTypeEnum;
import com.jd.spi.enums.EditStatus;
import com.jd.spi.model.*;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.sql.SQLExecutor;
import com.jd.spi.util.ExceptionUtils;
import com.jd.spi.util.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.jd.biz.domain.core.cache.CacheKey.getColumnKey;
import static com.jd.biz.domain.core.cache.CacheKey.getTableKey;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 2022年09月23日 15:51 moji Exp $
 * @date 2022/09/23
 */
@Service
@Slf4j
@SuppressWarnings("ALL")
public class TableServiceImpl implements TableService {

    @Autowired
    private PinService pinService;

    @Autowired
    private PinTableConverter pinTableConverter;

    @Resource
    private TableCacheMapper tableCacheMapper;

    @Autowired
    private TableConverter tableConverter;

    @Resource
    private TableCacheVersionMapper tableCacheVersionMapper;

    @Resource
    private TableVectorMappingMapper tableVectorMappingMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private RdbWebConverter rdbWebConverter;

    @Autowired
    private DlTemplateService dlTemplateService;

    @Override
    public DataResult<String> showCreateTable(ShowCreateTableParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        String ddl = metaSchema.tableDDL(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
        return DataResult.of(ddl);
    }

    @Override
    public ActionResult drop(DropParam param) {
        DBManage metaSchema = Chat2DBContext.getDBManage();
        metaSchema.dropTable(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getTableSchema(), param.getTableName());
        String cacheKey = CacheConstants.TABLE_INDEX_SQL_+ param.getDataSourceId() + param.getTableSchema() + param.getTableName();
        redisCache.deleteObject(cacheKey);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<String> createTableExample(String dbType) {
        String sql = Chat2DBContext.getDBConfig().getSimpleCreateTable();
        return DataResult.of(sql);
    }

    @Override
    public DataResult<String> alterTableExample(String dbType) {
        String sql = Chat2DBContext.getDBConfig().getSimpleAlterTable();
        return DataResult.of(sql);
    }

    public void buildOraclePrimaryKey(List<TableColumn> columnList, List<TableIndex> list) {
        if (CollUtil.isNotEmpty(list)) {
            List<String> collect = list.stream().filter(c -> "Primary".equals(c.getType())).map(TableIndex::getColumn).collect(Collectors.toList());
            if (CollUtil.isNotEmpty(collect)) {
                for (TableColumn tableColumn : columnList) {
                    if (collect.contains(tableColumn.getName())) {
                        tableColumn.setPrimaryKey(true);
                    }
                }
            }
        }
    }

    @Override
    public DataResult<Table> query(TableQueryParam param, TableSelector selector) {
        String dbType = Chat2DBContext.getConnectInfo().getDbType();
        if (param.getIsView()) {
            Table table = new Table();
            MetaData metaSchema = Chat2DBContext.getMetaData();
            table.setColumnList(
                    metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName()));
            if (DBTypeEnum.ORACLE.name().equals(dbType)) {
                List<TableIndex> constraintsData = metaSchema.getConstraintsData(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
                buildOraclePrimaryKey(table.getColumnList(), constraintsData);
            }
            return DataResult.of(table);
        }
        String cacheKey = CacheConstants.TABLE_INDEX_SQL_+ param.getDataSourceId() + param.getSchemaName() + param.getTableName();
        if (!param.getIsRefreshCache() && redisCache.hasKey(cacheKey)) {
            return DataResult.of(redisCache.getCacheObject(cacheKey));
        }
        MetaData metaSchema = Chat2DBContext.getMetaData();
        List<Table> tables = metaSchema.tables(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
        if (!CollectionUtils.isEmpty(tables)) {
            Table table = tables.get(0);
            List<TableIndex> indexes = metaSchema.indexes(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
            if(indexes.size() > 0) {
                indexes = indexes.stream().filter(t -> !"Normal".equals(t.getType())).collect(Collectors.toList());
            }
            table.setColumnList(
                    metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName()));
            if (DBTypeEnum.ORACLE.name().equals(dbType)) {
                List<TableIndex> constraintsData = metaSchema.getConstraintsData(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
                indexes.addAll(constraintsData);
                buildOraclePrimaryKey(table.getColumnList(), constraintsData);
            }
            table.setIndexList(indexes);
            // 主键、外键
            setPrimaryKey(table);
            List<TableIndex> list = new ArrayList<>();
            List<String> cons = new ArrayList<>();
            for (TableIndex index : indexes) {
                if (StrUtil.isNotBlank(index.getType()) && !cons.contains(index.getName())) {
                    cons.add(index.getName());
                    list.add(index);
                }
            }
            indexes = new ArrayList<>();
            indexes.addAll(list);
            table.setIndexList(indexes);
            List<TableIndex> constraints = new ArrayList<>();
            List<TableIndex> indices = indexes.stream()
                    .filter(c -> DMIndexTypeEnum.PRIMARY_KEY.getName().equals(c.getType()) ||
                            DMIndexTypeEnum.UNIQUE.getName().equals(c.getType()) ||
                            DMIndexTypeEnum.VIRTUAL.getName().equals(c.getType()) ||
                            DMIndexTypeEnum.CHECK.getName().equals(c.getType())).collect(Collectors.toList());
            for (TableIndex index : indices) {
                index.setColumn(index.getColumnList().stream().map(TableIndexColumn::getColumnName).collect(Collectors.joining(",")));
                if (DMIndexTypeEnum.VIRTUAL.getName().equals(index.getType())) {
                    index.setType(DMIndexTypeEnum.FOREIGN_KEY.getName());
                    if (CollUtil.isNotEmpty(index.getForeignList())) {
                        List<ForeignData> dataList = new ArrayList<>();
                        for (ForeignData data : index.getForeignList()) {
                            if (StrUtil.isNotBlank(index.getKeyName()) && index.getKeyName().equals(data.getConstraintName())) {
                                dataList.add(data);
                            }
                        }
                        if (CollUtil.isNotEmpty(dataList)) {
                            index.setForeignList(dataList);
                        }
                    }
                }
            }
            constraints.addAll(indices);
            List<String> collect = table.getColumnList().stream().filter(c -> c.getPrimaryKey()).map(TableColumn::getName).collect(Collectors.toList());
            if (!DBTypeEnum.ORACLE.name().equals(dbType)) {
                // 非空约束
                table.getColumnList().forEach(c -> {
                    if (c.getNullable() == 1 && !collect.contains(c.getName())) {
                        TableIndex tableIndex = new TableIndex();
                        tableIndex.setName(c.getName());
                        tableIndex.setSchemaName(param.getSchemaName());
                        tableIndex.setTableName(param.getTableName());
                        tableIndex.setStatus("VALID");
                        tableIndex.setType("CHECK");
                        tableIndex.setConstraintsDesc(c.getName().toUpperCase() + "\n IS NOT NULL");
                        tableIndex.setColumn(c.getName());
                        tableIndex.setIsN(Boolean.TRUE);
                        constraints.add(tableIndex);
                    }
                });
            }
            Set<TableIndex> checkSQL = metaSchema.getCheckSQl(Chat2DBContext.getConnection(), param.getSchemaName(), param.getTableName());
            buildCheckIndex(checkSQL, constraints);
            table.setConstraints(constraints);
            // ddl 建表语句
            String dropSql = Chat2DBContext.getSqlBuilder().dropTableSql(param.getSchemaName(), param.getTableName());
            String ddl = metaSchema.tableDDL(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
            table.setQuerySql(dropSql + ";\n" +ddl);
            if (CollUtil.isNotEmpty(table.getIndexList())) {
                table.setIndexList(table.getIndexList().stream()
                        .filter(c -> DMIndexTypeEnum.PRIMARY_KEY.getName().equals(c.getType()) ||
                                DMIndexTypeEnum.UNIQUE.getName().equals(c.getType()))
                                .collect(Collectors.toList()));
            }
            redisCache.setCacheObject(cacheKey, table);
            return DataResult.of(table);
        }
        return DataResult.of(null);
    }


    private void buildCheckIndex(Set<TableIndex> checkSQL, List<TableIndex> constraints) {
        if (CollUtil.isNotEmpty(checkSQL)) {
            for (TableIndex index : checkSQL) {
                String s = index.getConstraintsDesc().toUpperCase(Locale.ROOT).trim();
                if (StrUtil.isNotBlank(s) && s.contains("IN")) {
                    index.setKeyName(index.getName());
                    index.setType("CHECK");
                    index.setCheckData(paresValue(s));
                    constraints.add(index);
                } else if (StrUtil.isNotBlank(s) && s.contains("AND")) {
                    if(s.startsWith(" (")){
                        s = s.replaceAll("^\\s+|\\s+$", "");
                        s = s.replaceAll("^\\(|\\)$", "");
                        s = s.replaceAll("^\\s+|\\s+$", "");
                    }
                    String[] strs = s.split("AND");
                    for (String s1: strs) {
                        String[] s2 = s1.split(" ");
                        String s3 = s2[s2.length - 1].replace("(", "").replace(")", "");
                        buildCheckValue(index, s1, s3, index.getColumn());
                    }
                    index.setKeyName(index.getName());
                    constraints.add(index);
                } else {
                    String[] s1 = s.split(" ");
                    String s2 = s1[s1.length - 1].replace("(", "").replace(")", "");
                    buildCheckValue(index, s, s2, index.getColumn());
                    index.setKeyName(index.getName());
                    constraints.add(index);
                }
            }
        }
    }

    public void buildCheckValue(TableIndex index, String desc, String value, String column) {
        index.setType("CHECK");
        if (desc.contains(GenConstants.MAX_EQUAL) && desc.contains(column)) {
            index.setMinEqual(value);
        } else if(desc.contains(GenConstants.MAX) && desc.contains(column)){
            index.setMin(value);
        } else if (desc.contains(GenConstants.MIN_EQUAL) && desc.contains(column)) {
            index.setMaxEqual(value);
        } else if (desc.contains(GenConstants.MIN) && desc.contains(column)) {
            index.setMax(value);
        }
    }


    public List<String> paresValue(String constraintsDesc) {
        String regex = "'([^']*)'";
        Pattern compile = Pattern.compile(regex);
        Matcher matcher = compile.matcher(constraintsDesc);
        Set<String> set = new HashSet<>();
        while (matcher.find()) {
            String group = matcher.group(1);
            set.add(group);
        }
        return new ArrayList<>(set);
    }

    private void setPrimaryKey(Table table) {
        if (table == null) {
            return;
        }
        List<TableIndex> tableIndices = table.getIndexList();
        if (CollectionUtils.isEmpty(tableIndices)) {
            return;
        }
        List<TableColumn> columns = table.getColumnList();
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        Map<String, TableColumn> columnMap = columns.stream()
                .collect(Collectors.toMap(TableColumn::getName, Function.identity()));
        boolean ref = false;
        List<TableIndex> tableIndexList = new ArrayList<>();
        for (TableIndex tableIndex : tableIndices) {
            if ("Primary".equalsIgnoreCase(tableIndex.getType())) {
                List<TableIndexColumn> indexColumns = tableIndex.getColumnList();
                ref = indexColumns.size() > 1;
                if (CollectionUtils.isNotEmpty(indexColumns)) {
                    for (TableIndexColumn indexColumn : indexColumns) {
                        TableColumn column = columnMap.get(indexColumn.getColumnName());
                        if (column == null) {
                            continue;
                        }
                        TableIndex tableIndex1 = new TableIndex();
                        BeanUtils.copyProperties(tableIndex, tableIndex1);
                        tableIndex1.setType(null);
                        tableIndex1.setColumn(column.getName());
                        tableIndexList.add(tableIndex1);
                        column.setPrimaryKey(true);
                        column.setPrimaryKeyOrder(null == indexColumn.getOrdinalPosition() ? 1 : indexColumn.getOrdinalPosition());
                        column.setPrimaryKeyName(tableIndex.getName());
                    }
                }
            }
        }
        tableIndices.addAll(tableIndexList);
        // 设置外键
        TableIndex tableIndex = new TableIndex();
        tableIndex.setTableName(table.getName());
        tableIndex.setSchemaName(table.getSchemaName());
        foreignKeyList(tableIndex);
        if (!Objects.isNull(tableIndex.getForeign()) || CollUtil.isNotEmpty(tableIndex.getForeignList())) {
            for (TableIndex data : tableIndices) {
                if (StrUtil.isEmpty(data.getColumn())) {
                    continue;
                }
                if (!"Primary".equalsIgnoreCase(data.getType())) {
                    if (!Objects.isNull(tableIndex.getForeign()) && StrUtil.isNotBlank(tableIndex.getForeign().getColumn())) {
                        ForeignData foreign = tableIndex.getForeign();
                        if (data.getColumn().equals(foreign.getColumn())) {
                            data.setForeign(foreign);
                            data.setType(DMIndexTypeEnum.VIRTUAL.getName());
                            data.setStatus(StrUtil.isNotBlank(tableIndex.getStatus()) ? tableIndex.getStatus() : "");
                            data.setKeyName(StrUtil.isNotBlank(foreign.getKName()) ? foreign.getKName() : "");
                            data.setName(StrUtil.isNotBlank(foreign.getConstraintName()) ? foreign.getConstraintName() : "");
                            data.setColumnList(data.getColumnList().stream().filter(t -> t.getColumnName().equals(data.getColumn())).collect(Collectors.toList()));
                        }
                    } else {
                        if(CollUtil.isNotEmpty(tableIndex.getForeignList())) {
                            List<ForeignData> list = new ArrayList<>();
                            String status = null;
                            String kName = null;
                            String constraintName = null;
                            for (ForeignData foreignData : tableIndex.getForeignList()) {
                                if (data.getColumn().contains(foreignData.getColumn())) {
                                    list.add(foreignData);
                                    status = StrUtil.isNotBlank(foreignData.getStatus()) ? foreignData.getStatus() : "";
                                    kName = StrUtil.isNotBlank(foreignData.getKName()) ? foreignData.getKName() : "";
                                    constraintName = StrUtil.isNotBlank(foreignData.getConstraintName()) ? foreignData.getConstraintName() : "";
                                }
                            }
                            if (CollUtil.isNotEmpty(list)) {
                                data.setKeyName(kName);
                                data.setName(constraintName);
                                data.setForeignList(list);
                                data.setType(DMIndexTypeEnum.VIRTUAL.getName());
                                data.setStatus(status);
                                data.setColumnList(data.getColumnList().stream().filter(t -> t.getColumnName().equals(data.getColumn())).collect(Collectors.toList()));
                            }
                        }
                    }
                }
            }
        }
    }

    private void foreignKeyList(TableIndex tableIndex) {
        List<ForeignData> foreignKeys = Chat2DBContext.getMetaData().getForeignKey(Chat2DBContext.getConnection(), tableIndex.getTableName(), tableIndex.getSchemaName());
        if (CollUtil.isEmpty(foreignKeys)) {
            return;
        }
        if (foreignKeys.size() == 1) {
            ForeignData foreignData = new ForeignData();
            foreignData.setForeignColumnName(foreignKeys.get(0).getForeignColumnName());
            foreignData.setForeignTableName(foreignKeys.get(0).getForeignTableName());
            foreignData.setForeignSchemaName(foreignKeys.get(0).getForeignSchemaName());
            foreignData.setSchemaName(foreignKeys.get(0).getSchemaName());
            foreignData.setColumn(foreignKeys.get(0).getColumn());
            foreignData.setTableName(foreignKeys.get(0).getTableName());
            foreignData.setConstraintName(foreignKeys.get(0).getConstraintName());
            foreignData.setKName(foreignKeys.get(0).getConstraintName());
            foreignData.setStatus(foreignKeys.get(0).getStatus());
            List<ForeignData> list = new ArrayList<>();
            list.add(foreignData);
            tableIndex.setForeignList(list);
            tableIndex.setStatus(foreignKeys.get(0).getStatus());
        } else {
            List<ForeignData> foreignDataList = new ArrayList<>();
            for (ForeignData foreignKey : foreignKeys) {
                ForeignData foreignData = new ForeignData();
                foreignData.setForeignTableName(foreignKey.getForeignTableName());
                foreignData.setForeignColumnName(foreignKey.getForeignColumnName());
                foreignData.setForeignSchemaName(foreignKey.getForeignSchemaName());
                foreignData.setSchemaName(foreignKey.getSchemaName());
                foreignData.setColumn(foreignKey.getColumn());
                foreignData.setTableName(foreignKey.getTableName());
                foreignData.setConstraintName(foreignKey.getConstraintName());
                foreignData.setKName(foreignKey.getConstraintName());
                foreignDataList.add(foreignData);
            }
            tableIndex.setStatus(foreignKeys.get(0).getStatus());
            tableIndex.setForeignList(foreignDataList);
        }
    }

    @Override
    public ListResult<Sql> buildSql(Table oldTable, Table newTable) {
        initOldTable(oldTable, newTable);
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        List<Sql> sqls = new ArrayList<>();
        if (oldTable == null) {
            initPrimaryKey(newTable);
            sqls.add(Sql.builder().sql(sqlBuilder.buildCreateTableSql(newTable)).build());
        } else {
            initUpdatePrimaryKey(oldTable, newTable);
            final String constraintName = Chat2DBContext.getMetaData().getConstraintName(Chat2DBContext.getConnection(), newTable.getDatabaseName(), newTable.getSchemaName(), newTable.getName());
            newTable.setConstraintName(constraintName);
            sqls.add(Sql.builder().sql(sqlBuilder.buildModifyTaleSql(oldTable, newTable).trim()).build());
        }
        return ListResult.of(sqls);
    }

    private void initUpdatePrimaryKey(Table oldTable, Table newTable) {
        if (newTable == null || oldTable == null) {
            return;
        }
        List<TableColumn> newColumns = getPrimaryKeyColumn(newTable);
        List<TableColumn> oldColumns = getPrimaryKeyColumn(oldTable);
        if (CollectionUtils.isEmpty(newColumns) && CollectionUtils.isEmpty(oldColumns)) {
            return;
        }
        if (!CollectionUtils.isEmpty(newColumns) && CollectionUtils.isEmpty(oldColumns)) {
            initPrimaryKey(newTable);
            return;
        }
        if (CollectionUtils.isEmpty(newColumns) && CollectionUtils.isNotEmpty(oldColumns)) {
            addPrimaryKey(newTable, oldColumns.get(0), EditStatus.DELETE.name());
            return;
        }
        if (newColumns.size() != oldColumns.size()) {
            for (TableColumn column : newColumns) {
                if (column.getPrimaryKey() != null && column.getPrimaryKey()) {
                    addPrimaryKey(newTable, column, EditStatus.MODIFY.name());
                }
            }
            return;
        }
        boolean flag = false;
        Map<String, TableColumn> oldColumnMap = oldColumns.stream().collect(Collectors.toMap(TableColumn::getName, Function.identity()));
        for (TableColumn column : newColumns) {
            TableColumn oldColumn = oldColumnMap.get(column.getName());
            if (oldColumn == null) {
                flag = true;
            }
        }
        if (flag) {
            for (TableColumn column : newColumns) {
                if (column.getPrimaryKey() != null && column.getPrimaryKey()) {
                    addPrimaryKey(newTable, column, EditStatus.MODIFY.name());
                }
            }
        }
    }

    private List<TableColumn> getPrimaryKeyColumn(Table table) {
        if (table == null || CollectionUtils.isEmpty(table.getColumnList())) {
            return null;
        }
        return table.getColumnList().stream().filter(tableColumn ->
                        tableColumn.getPrimaryKey() != null && tableColumn.getPrimaryKey())
                .collect(Collectors.toList());
    }

    private void initPrimaryKey(Table newTable) {
        if (newTable == null) {
            return;
        }
        List<TableColumn> columns = newTable.getColumnList();
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        int i = 0;
        for (TableColumn column : columns) {
            if (i == 0 && column.getPrimaryKey() != null && column.getPrimaryKey()) {
                addPrimaryKey(newTable, column, EditStatus.ADD.name());
                i++;
            }
        }
    }

    private void addPrimaryKey(Table newTable, TableColumn column, String status) {
        List<TableIndex> indexes = newTable.getIndexList();
        if (indexes == null) {
            indexes = new ArrayList<>();
        }
        List<String> collect = newTable.getColumnList().stream().filter(TableColumn::getPrimaryKey).map(TableColumn::getName).collect(Collectors.toList());
        TableIndex keyIndex = indexes.stream().filter(index -> "Primary".equalsIgnoreCase(index.getType())).findFirst().orElse(null);
        if (keyIndex == null) {
            keyIndex = new TableIndex();
            keyIndex.setType("Primary");
            keyIndex.setName(StringUtils.isBlank(column.getPrimaryKeyName()) ? "PRIMARY_KEY" : column.getPrimaryKeyName());
            keyIndex.setTableName(newTable.getName());
            keyIndex.setSchemaName(newTable.getSchemaName());
            keyIndex.setDatabaseName(newTable.getDatabaseName());
            keyIndex.setEditStatus(status);
            keyIndex.setColumn(String.join(",", collect));
            if (!EditStatus.ADD.name().equals(status)) {
                keyIndex.setOldName(keyIndex.getName());
            }
            List<TableIndexColumn> list = new ArrayList<>();
            for (String s : collect) {
                TableIndexColumn tableIndexColumn = new TableIndexColumn();
                tableIndexColumn.setColumnName(s);
                tableIndexColumn.setTableName(newTable.getName());
                tableIndexColumn.setSchemaName(newTable.getSchemaName());
                tableIndexColumn.setOrdinalPosition(Short.valueOf(column.getPrimaryKeyOrder() + ""));
                tableIndexColumn.setEditStatus(status);
                list.add(tableIndexColumn);
            }
            keyIndex.setColumnList(list);
            indexes.add(keyIndex);
        }
        List<TableIndexColumn> tableIndexColumns = keyIndex.getColumnList();
        if (tableIndexColumns == null) {
            tableIndexColumns = new ArrayList<>();
        } else {
            List<String> refColumn = new ArrayList<>();
            List<TableIndexColumn> tableIndexList = new ArrayList<>();
            for (TableIndexColumn indexColumn : tableIndexColumns) {
                if (collect.contains(indexColumn.getColumnName())) {
                    tableIndexList.add(indexColumn);
                    refColumn.add(indexColumn.getColumnName());
                }
            }
            if (collect.size() != tableIndexColumns.size()) {
                for (String s : collect) {
                    if (!refColumn.contains(s)) {
                        TableIndexColumn tableIndexColumn = tableIndexColumns.get(0);
                        tableIndexColumn.setColumnName(s);
                        tableIndexList.add(tableIndexColumn);
                    }
                }
            }
            tableIndexColumns = new ArrayList<>();
            tableIndexColumns = tableIndexList;
        }
        tableIndexColumns = new ArrayList<>(tableIndexColumns.stream().collect(Collectors.toMap(TableIndexColumn::getColumnName, Function.identity(), (a, b) -> a)).values());
        List<TableColumn> columnList = newTable.getColumnList().stream().filter(c -> c.getPrimaryKey()!=null).collect(Collectors.toList());
        if (columnList.size() != 0 && columnList.size() < tableIndexColumns.size()) {
            for (TableColumn tableColumn : columnList) {
                if (CollUtil.isNotEmpty(tableIndexColumns)) {
                    Iterator<TableIndexColumn> iterator = tableIndexColumns.iterator();
                    while (iterator.hasNext()) {
                        TableIndexColumn next = iterator.next();
                        if (StringUtils.isNotBlank(next.getColumnName()) && !next.getColumnName().equals(tableColumn.getName())) {
                            iterator.remove();
                        }
                    }
                }
            }
        }
        TableIndexColumn indexColumn = new TableIndexColumn();
        indexColumn.setColumnName(column.getName());
        indexColumn.setTableName(newTable.getName());
        indexColumn.setSchemaName(newTable.getSchemaName());
        indexColumn.setDatabaseName(newTable.getDatabaseName());
        indexColumn.setOrdinalPosition(Short.valueOf(column.getPrimaryKeyOrder() + ""));
        indexColumn.setEditStatus(status);
        tableIndexColumns.add(indexColumn);
        List<TableIndexColumn> sortTableIndexColumns = tableIndexColumns.stream().sorted(Comparator.comparing(TableIndexColumn::getOrdinalPosition)).collect(Collectors.toList());
        Set<String> statusList = sortTableIndexColumns.stream().filter(c -> StringUtils.isNotBlank(c.getEditStatus())).map(TableIndexColumn::getEditStatus).collect(Collectors.toSet());
        if (statusList.size() == 1) {
            //only one status ,set index status
            keyIndex.setEditStatus(statusList.iterator().next());
        } else {
            //more status ,set index status modify
            keyIndex.setEditStatus(EditStatus.MODIFY.name());
        }
        keyIndex.setColumnList(sortTableIndexColumns);
        newTable.setIndexList(indexes);
    }


    private void initOldTable(Table oldTable, Table newTable) {
        if (oldTable == null || newTable == null) {
            return;
        }
        Map<String, TableColumn> columnMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(oldTable.getColumnList())) {
            for (TableColumn column : oldTable.getColumnList()) {
                columnMap.put(column.getName(), column);
            }
        }
        if (CollectionUtils.isNotEmpty(newTable.getColumnList())) {
            for (TableColumn newColumn : newTable.getColumnList()) {
                if (StrUtil.isEmpty(newColumn.getColumnType()) || StrUtil.isEmpty(newColumn.getName())) {
//                    throw new BusinessException("禁止空数据");
                    throw new ServiceException("禁止空数据");
                }
                if (EditStatus.ADD.name().equals(newColumn.getEditStatus())) {
                    continue;
                }
                String name = newColumn.getOldName() == null ? newColumn.getName() : newColumn.getOldName();
                TableColumn oldColumn = columnMap.get(name);
                if (oldColumn != null) {
                    if (oldColumn.equals(newColumn) && EditStatus.MODIFY.name().equals(newColumn.getEditStatus())) {
                        newColumn.setEditStatus(null);
                    } else {
                        newColumn.setOldColumn(oldColumn);
                    }
                }
            }
        }
    }

    @Override
    public String columnDefault(TypeQueryRequest request) {
        String defaultValue = Chat2DBContext.getMetaData().columnDefault(Chat2DBContext.getConnection(), request.getName());
        return defaultValue;
    }

    /**
     * 武汉 贯标
     *
     * @param param
     * @return
     */
    public List<ColumnWHVO> queryColumnsWH(TableQueryParam param) {
        List<ColumnWHVO> list = new ArrayList<>();
        param.setRefresh(Boolean.TRUE);
        List<TableColumn> columnList = queryColumns(param);
        List<TableIndex> tableIndices = queryIndexes(param);
        int i = 1;
        for (TableColumn tableColumn : columnList) {
            ColumnWHVO columnWHVO = new ColumnWHVO();
            columnWHVO.setName(tableColumn.getName());
            columnWHVO.setColumnType(tableColumn.getColumnType());
            columnWHVO.setComment(StringUtils.isBlank(tableColumn.getComment()) ? null : tableColumn.getComment());
            tableIndices = tableIndices.stream().filter(t -> DMIndexTypeEnum.PRIMARY_KEY.getName().equals(t.getType())).collect(Collectors.toList());
            for (TableIndex tableIndex : tableIndices) {
                List<TableIndexColumn> columnList1 = tableIndex.getColumnList();
                for (TableIndexColumn tableIndexColumn : columnList1) {
                    if (StringUtils.isNotBlank(tableIndexColumn.getColumnName()) && tableIndexColumn.getColumnName().equals(columnWHVO.getName())) {
                        columnWHVO.setIndexType(tableIndex.getType());
                    }
                }
            }
            columnWHVO.setNumber(i);
            i++;
            list.add(columnWHVO);
        }
        return list;
    }


    @Override
    public List<ColumnWHVO> columnMapping(TableColumnMappingRequest request) {
        if (CollUtil.isEmpty(request.getColumnWHVOS())) {
            throw new BusinessException("入参缺失");
        }
        Set<ColumnWHVO> columnWHVOS = new LinkedHashSet<>();
        TableQueryParam param = new TableQueryParam();
        param.setTableName(request.getToTableName());
        param.setSchemaName(request.getSchemaName());
        param.setDataSourceId(request.getDataSourceId());
        List<ColumnWHVO> list = queryColumnsWH(param);
        if (CollUtil.isEmpty(list)) {
            return null;
        }
        for (ColumnWHVO column : list) {
            for (ColumnWHVO whvo : request.getColumnWHVOS()) {
                if ((Objects.nonNull(whvo.getName()) && whvo.getName().equals(column.getName())) ||
                        (Objects.nonNull(whvo.getName()) && whvo.getName().equals(column.getComment())) ||
                        (Objects.nonNull(whvo.getComment()) && whvo.getComment().equals(column.getName())) ||
                        (Objects.nonNull(whvo.getComment()) && whvo.getComment().equals(column.getComment()))) {
                    ColumnWHVO columnWHVO = new ColumnWHVO();
                    columnWHVO.setName(column.getName());
                    columnWHVO.setComment(column.getComment());
                    columnWHVO.setColumnType(column.getColumnType());
                    columnWHVO.setNumber(whvo.getNumber());
                    if (Objects.nonNull(whvo.getIndexType()) && whvo.getIndexType().equals(column.getIndexType())) {
                        columnWHVO.setIndexType(column.getIndexType());
                    } else {
                        if (StringUtils.isNotBlank(column.getIndexType())) {
                            columnWHVO.setIndexType(column.getIndexType());
                        }
                    }
                    columnWHVOS.add(columnWHVO);
                    break;
                }
            }
        }
        List<Integer> collect = columnWHVOS.stream().map(ColumnWHVO::getNumber).collect(Collectors.toList());
        List<Integer> reCollect = request.getColumnWHVOS().stream().filter(r -> r.getNumber() != null).map(ColumnWHVO::getNumber).collect(Collectors.toList());
        for (Integer integer : reCollect) {
            if (!collect.contains(integer)) {
                ColumnWHVO columnWHVO = new ColumnWHVO();
                columnWHVO.setNumber(integer);
                columnWHVOS.add(columnWHVO);
            }
        }
        return new ArrayList<>(columnWHVOS);
    }

    @Override
    public PageResult<Table> pageQuery(TablePageQueryParam param, TableSelector selector) {
        if (Objects.isNull(param.getSchemaName())) {
            throw new BusinessException("参数缺失,模式名没有值");
        }
        List<Table> tables = new ArrayList<>();
        if (ResultType.TWO.getType().equals(param.getRequestType())) {
            String cacheKey = CacheConstants.SELECT_TABLE_DETAILS_DATA_SQL + param.getDataSourceId()+"_"+param.getSchemaName();
            if (!param.getIsRefreshCache() && redisCache.hasKey(cacheKey)) {
                tables = redisCache.getCacheObject(cacheKey);
                return pageTables(tables, param);
            }
            Map<String, TableDetails> tableDetailsMap = new HashMap<>();
            List<TableDetails> tableDetails = Chat2DBContext.getMetaData().tableDetailsData(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName());
            tableDetailsMap = tableDetails.stream().collect(Collectors.toMap(TableDetails::getName, Function.identity(), (one, two) -> two));
//            Map<String, Table> tableMap = new HashMap<>();
            List<Table> tableList = Chat2DBContext.getMetaData().tableDetails(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName());
            Map<String, Table> tableMap = tableList.stream().collect(Collectors.toMap(Table::getName, Function.identity(), (one, two) -> two, LinkedHashMap::new));
            for (String key : tableMap.keySet()) {
                if (tableDetailsMap.containsKey(key)) {
                    Table table = tableMap.get(key);
                    TableDetails tableDetails1 = table.getTableDetails();
                    TableDetails details = tableDetailsMap.get(key);
                    if (null != details) {
                        tableDetails1.setCreated(StrUtil.isNotBlank(details.getCreated()) ? details.getCreated(): "");
                        tableDetails1.setComment(StrUtil.isNotBlank(details.getComment()) ? details.getComment(): "");
                        tableDetails1.setLastDDL(StrUtil.isNotBlank(details.getLastDDL()) ? details.getLastDDL(): "");
                    }
                }
            }
            tables = new ArrayList<>(tableMap.values());
            String dbType = Chat2DBContext.getConnectInfo().getDbType();
            Map<String, String> collect = null;
            List<TableDetails> tableDetailsList = Chat2DBContext.getMetaData().rowCount(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName());
            if (CollUtil.isNotEmpty(tableDetailsList)) {
                collect = tableDetailsList.stream().collect(Collectors.toMap(TableDetails::getName, TableDetails::getNumRows));
            }
            for (Table t : tables) {
                if (null != t.getTableDetails()) {
                    TableDetails tableDetails1 = t.getTableDetails();
                    BeanUtil.copyProperties(tableDetails1, t);
                    t.setTableDetails(tableDetails1);
                    tableDetails1.setNumRows(StringUtils.isNotBlank(tableDetails1.getNumRows()) ? tableDetails1.getNumRows() : "0");
                    if ("0".equals(tableDetails1.getNumRows()) && CollUtil.isNotEmpty(collect)) {
                        tableDetails1.setNumRows(StringUtils.isNotBlank(collect.get(t.getName())) ? collect.get(t.getName()) : "0");
                    }
                    // "key2SELECT_TABLE_DETAILS_SQL:177_HJDB"
                    // key1SELECT_TABLE_DETAILS_DATA_SQL:177_HJDB
                }
            }
            tables = new ArrayList<>(tableMap.values());
            redisCache.setCacheObject(cacheKey, tables);
            redisCache.expire(cacheKey, 1, TimeUnit.DAYS);
        } else {
            String cacheKey = CacheConstants.SELECT_TABLE_DETAILS_SQL_ + param.getDataSourceId()+"_"+param.getSchemaName();
            if (!param.getIsRefreshCache() && redisCache.hasKey(cacheKey)) {
                tables = redisCache.getCacheObject(cacheKey);
                return pageTables(tables, param);
            }
            List<Table> tableList = Chat2DBContext.getMetaData().tableDetails(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName());
            if (CollUtil.isEmpty(tableList)) {
                return null;
            }
            Map<String, Table> tableMap = tableList.stream().collect(Collectors.toMap(Table::getName, Function.identity(), (one, two) -> two,LinkedHashMap::new));
            tables = new ArrayList<>(tableMap.values());
            redisCache.setCacheObject(cacheKey, tables);
            redisCache.expire(cacheKey, 1, TimeUnit.DAYS);
        }
        return pageTables(tables, param);
    }

    /**
     * 对已缓存的表元数据做内存分页，避免默认表列表一次返回并渲染整个模式。
     */
    PageResult<Table> pageTables(List<Table> tables, TablePageQueryParam param) {
        List<Table> source = tables == null ? Collections.emptyList() : tables;
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            String searchKey = param.getSearchKey().trim();
            source = source.stream()
                    .filter(table -> StringUtils.containsIgnoreCase(table.getName(), searchKey)
                            || StringUtils.containsIgnoreCase(table.getComment(), searchKey)
                            || (table.getTableDetails() != null
                            && StringUtils.containsIgnoreCase(table.getTableDetails().getComment(), searchKey)))
                    .collect(Collectors.toList());
        }
        int pageNo = param.getPageNo() == null || param.getPageNo() < 1 ? 1 : param.getPageNo();
        int pageSize = param.getPageSize() == null || param.getPageSize() < 1 ? 50 : param.getPageSize();
        int from = (int) Math.min((long) (pageNo - 1) * pageSize, source.size());
        int to = Math.min(from + pageSize, source.size());
        return PageResult.of(source.subList(from, to), (long) source.size(), pageNo, pageSize);
    }


    private void buildTableDetails(Table table, Map<String, String> collect) {
        TableDetails tableDetails = new TableDetails();
        if (CollUtil.isNotEmpty(collect)) {
            tableDetails.setNumRows(collect.get(table.getName()));
        }
        tableDetails.setNumRows(StringUtils.isNotBlank(tableDetails.getNumRows()) ? tableDetails.getNumRows() : "0");
        tableDetails.setSchema(table.getSchemaName());
        tableDetails.setName(table.getName());
        tableDetails.setComment(table.getComment());
        table.setTableDetails(tableDetails);

    }


    private long addCache(TablePageQueryParam param, TableCacheVersionDO versionDO) {
        LambdaQueryWrapper<TableCacheVersionDO> queryWrapper = new LambdaQueryWrapper<>();
        String key = getTableKey(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName());
        queryWrapper.eq(TableCacheVersionDO::getKey, key);
        long total = 0;
        long version = getLock(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(), versionDO);
        if (version == -1) {
            int n = 0;
            while (n < 100) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                }
                versionDO = tableCacheVersionMapper.selectOne(queryWrapper);
                if (versionDO != null && "1".equals(versionDO.getStatus())) {
                    version = versionDO.getVersion();
                    total = versionDO.getTableCount();
                    break;
                }
                n++;
            }
        } else {
            total = addDBCache(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(), version);
            TableCacheVersionDO versionDO1 = new TableCacheVersionDO();
            versionDO1.setStatus("1");
            versionDO1.setTableCount(total);
            tableCacheVersionMapper.update(versionDO1, queryWrapper);
        }
        return total;
    }

    @Override
    public ListResult<SimpleTable> queryTables(TablePageQueryParam param) {
        LambdaQueryWrapper<TableCacheVersionDO> queryWrapper = new LambdaQueryWrapper<>();
        String key = getTableKey(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName());
        queryWrapper.eq(TableCacheVersionDO::getKey, key);
        TableCacheVersionDO versionDO = tableCacheVersionMapper.selectOne(queryWrapper);
        if (versionDO == null) {
            addCache(param, versionDO);
            versionDO = tableCacheVersionMapper.selectOne(queryWrapper);
        }
        long version = "2".equals(versionDO.getStatus()) ? versionDO.getVersion() - 1 : versionDO.getVersion();

        LambdaQueryWrapper<TableCacheDO> query = new LambdaQueryWrapper<>();
        query.eq(TableCacheDO::getVersion, version);
        query.eq(TableCacheDO::getDataSourceId, param.getDataSourceId());
        if (StringUtils.isNotBlank(param.getDatabaseName())) {
            query.eq(TableCacheDO::getDatabaseName, param.getDatabaseName());
        }
        if (StringUtils.isNotBlank(param.getSchemaName())) {
            query.eq(TableCacheDO::getSchemaName, param.getSchemaName());
        }
        List<SimpleTable> tables = new ArrayList<>();

        for (int i = 0; i < versionDO.getTableCount() / 500 + 1; i++) {
            Page<TableCacheDO> page = new Page<>(i + 1, 500);
            IPage<TableCacheDO> iPage = tableCacheMapper.selectPage(page, query);
            if (CollectionUtils.isNotEmpty(iPage.getRecords())) {
                for (TableCacheDO tableCacheDO : iPage.getRecords()) {
                    SimpleTable t = new SimpleTable();
                    t.setName(tableCacheDO.getTableName());
                    t.setComment(tableCacheDO.getExtendInfo());
                    tables.add(t);
                }
            }
        }
        return ListResult.of(tables);
    }

    private long addDBCache(Long dataSourceId, String databaseName, String schemaName, long version) {
        String key = getTableKey(dataSourceId, databaseName, schemaName);

        Connection connection = Chat2DBContext.getConnection();
        long n = 0;
        try (ResultSet resultSet = connection.getMetaData().getTables(databaseName, schemaName, null,
                new String[]{"TABLE", "SYSTEM TABLE"})) {
            List<TableCacheDO> cacheDOS = new ArrayList<>();
            while (resultSet.next()) {
                TableCacheDO tableCacheDO = new TableCacheDO();
                tableCacheDO.setDatabaseName(databaseName);
                tableCacheDO.setSchemaName(schemaName);
                tableCacheDO.setTableName(resultSet.getString("TABLE_NAME"));
                tableCacheDO.setExtendInfo(resultSet.getString("REMARKS"));
                tableCacheDO.setDataSourceId(dataSourceId);
                tableCacheDO.setVersion(version);
                tableCacheDO.setKey(key);
                cacheDOS.add(tableCacheDO);
                if (cacheDOS.size() >= 500) {
                    tableCacheMapper.batchInsert(cacheDOS);
                    cacheDOS = new ArrayList<>();
                }
                n++;
            }
            if (!CollectionUtils.isEmpty(cacheDOS)) {
                tableCacheMapper.batchInsert(cacheDOS);
            }
            LambdaQueryWrapper<TableCacheDO> q = new LambdaQueryWrapper();
            q.eq(TableCacheDO::getDataSourceId, dataSourceId);
            q.lt(TableCacheDO::getVersion, version);
            if (StringUtils.isNotBlank(databaseName)) {
                q.eq(TableCacheDO::getDatabaseName, databaseName);
            }
            if (StringUtils.isNotBlank(schemaName)) {
                q.eq(TableCacheDO::getSchemaName, schemaName);
            }
            tableCacheMapper.delete(q);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return n;
    }

    private Long getLock(Long dataSourceId, String databaseName, String schemaName, TableCacheVersionDO versionDO) {
        String key = getTableKey(dataSourceId, databaseName, schemaName);
        if (versionDO == null) {
            versionDO = new TableCacheVersionDO();
            versionDO.setDatabaseName(databaseName);
            versionDO.setSchemaName(schemaName);
            versionDO.setDataSourceId(dataSourceId);
            versionDO.setStatus("2");
            versionDO.setKey(key);
            versionDO.setVersion(0L);
            versionDO.setTableCount(0L);
            try {
                tableCacheVersionMapper.insert(versionDO);
                return 0L;
            } catch (Exception e) {
                e.printStackTrace();
                return -1L;
            }
        } else {
            long version = versionDO.getVersion() + 1;
            LambdaQueryWrapper<TableCacheVersionDO> queryWrapper = new LambdaQueryWrapper();
            queryWrapper.eq(TableCacheVersionDO::getId, versionDO.getId());
            queryWrapper.eq(TableCacheVersionDO::getVersion, versionDO.getVersion());
            versionDO.setVersion(version);
            versionDO.setStatus("2");
            int n = tableCacheVersionMapper.update(versionDO, queryWrapper);
            if (n == 1) {
                return version;
            } else {
                return -1L;
            }
        }
    }


//    private String buildKey(Long dataSourceId, String databaseName, String schemaName) {
//        StringBuilder stringBuilder = new StringBuilder(dataSourceId.toString());
//        if (StringUtils.isNotBlank(databaseName)) {
//            stringBuilder.append("_").append(databaseName);
//        }
//        if (StringUtils.isNotBlank(schemaName)) {
//            stringBuilder.append("_").append(schemaName);
//        }
//        return stringBuilder.toString();
//    }

    private List<Table> pinTable(List<Table> list, TablePageQueryParam param) {
        if (CollectionUtils.isEmpty(list)) {
            return Lists.newArrayList();
        }
        PinTableParam pinTableParam = pinTableConverter.toPinTableParam(param);
        pinTableParam.setUserId(ContextUtils.getUserId());
        ListResult<String> listResult = pinService.queryPinTables(pinTableParam);
        if (!listResult.success() || CollectionUtils.isEmpty(listResult.getData())) {
            return list;
        }
        List<Table> tables = new ArrayList<>();
        Map<String, Table> tableMap = list.stream().collect(Collectors.toMap(Table::getName, Function.identity()));
        for (String tableName : listResult.getData()) {
            Table table = tableMap.get(tableName);
            if (table != null) {
                table.setPinned(true);
                tables.add(table);
            }
        }

        for (Table table : list) {
            if (table != null && !tables.contains(table)) {
                tables.add(table);
            }
        }
        return tables;
    }

    @Override
    public List<TableColumn>
    queryColumns(TableQueryParam param) {
        String tableColumnKey = getColumnKey(param.getDataSourceId(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
        MetaData metaSchema = Chat2DBContext.getMetaData();
        return CacheManage.getList(tableColumnKey, TableColumn.class,
                (key) -> param.isRefresh(), (key) ->
                        metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName()));
    }

    @Override
    public List<TableIndex> queryIndexes(TableQueryParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        return metaSchema.indexes(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());

    }

    @Override
    public DataResult<Map<String, Object>> compareTables(TableQueryParam source, TableQueryParam target) {
        List<TableColumn> sourceColumns = queryColumns(source);
        List<TableColumn> targetColumns = queryColumns(target);
        Map<String, TableColumn> sourceMap = sourceColumns.stream().collect(Collectors.toMap(TableColumn::getName, Function.identity(), (a, b) -> a));
        Map<String, TableColumn> targetMap = targetColumns.stream().collect(Collectors.toMap(TableColumn::getName, Function.identity(), (a, b) -> a));
        List<Map<String, Object>> differences = new ArrayList<>();
        for (TableColumn column : sourceColumns) {
            TableColumn targetColumn = targetMap.get(column.getName());
            Map<String, Object> difference = new LinkedHashMap<>();
            difference.put("columnName", column.getName());
            if (targetColumn == null) {
                difference.put("type", "MISSING_IN_TARGET");
                difference.put("detail", "目标表缺少字段");
            } else {
                List<String> changes = new ArrayList<>();
                if (!Objects.equals(column.getColumnType(), targetColumn.getColumnType())) changes.add("类型");
                if (!Objects.equals(column.getNullable(), targetColumn.getNullable())) changes.add("可空性");
                if (!Objects.equals(column.getDefaultValue(), targetColumn.getDefaultValue())) changes.add("默认值");
                if (!Objects.equals(column.getPrimaryKey(), targetColumn.getPrimaryKey())) changes.add("主键");
                if (!changes.isEmpty()) {
                    difference.put("type", "DIFF");
                    difference.put("detail", String.join("、", changes) + "不一致");
                }
            }
            if (!difference.isEmpty() && difference.containsKey("type")) differences.add(difference);
        }
        for (TableColumn column : targetColumns) {
            if (!sourceMap.containsKey(column.getName())) {
                Map<String, Object> difference = new LinkedHashMap<>();
                difference.put("columnName", column.getName());
                difference.put("type", "EXTRA_IN_TARGET");
                difference.put("detail", "目标表多出字段");
                differences.add(difference);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceTable", source.getTableName());
        result.put("targetTable", target.getTableName());
        result.put("sourceColumnCount", sourceColumns.size());
        result.put("targetColumnCount", targetColumns.size());
        result.put("same", differences.isEmpty());
        result.put("differences", differences);
        return DataResult.of(result);
    }

    @Override
    public List<Type> queryTypes(TypeQueryParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        return metaSchema.types(Chat2DBContext.getConnection());
    }

    @Override
    public void dropConstraint(TypeQueryRequest param,Connection connection) throws SQLException {
        String sql = Chat2DBContext.getSqlBuilder().dropTableConstraint(param.getSchemaName(), param.getTableName(), param.getKeyName(), param.getName());
        String[] split = sql.split(";");
        for (String s : split) {
            SQLExecutor.getInstance().execute(connection, s);
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTableConstraint(ConstraintInfoRequest param) throws SQLException {
        boolean isUpdate = false;
        List<String> sqls = new ArrayList<>();
        List<String> rollbackSqls = new ArrayList<>();
        Connection connection = Chat2DBContext.getConnection();
        if (Objects.nonNull(param.getOldData())) {
            isUpdate = true;
        }
        try {
            if (isUpdate) {
                // 删除约束在新建
                this.dropConstraint(param.getOldData(), connection);
                rollbackSqls = addConstraint(param.getOldData());
                sqls = addConstraint(param.getNewData());
            } else {
                TypeQueryRequest newData = param.getNewData();
                sqls = addConstraint(newData);
            }
            if (CollUtil.isNotEmpty(sqls)) {
                for (String s : sqls) {
                    SQLExecutor.getInstance().execute(connection, s);
                }
            }
        } catch (SQLException e) {
            log.warn("执行约束SQL异常,{}", e.getMessage());
            // ddl sql 自动提交
            try {
                if (CollUtil.isNotEmpty(rollbackSqls)) {
                    for (String data : rollbackSqls) {
                        SQLExecutor.getInstance().execute(connection, data);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                log.warn("执行回滚约束SQL异常,{}", ex.getMessage());
            }
            throw new BusinessException("执行创建约束SQL异常: "+ ExceptionUtils.getMessage(e));
        }
    }


    public List<String> addConstraint(TypeQueryRequest param) throws SQLException {
        if (StringUtils.isBlank(param.getConstraintType()) || StringUtils.isBlank(param.getConstraintName())) {
            throw new BusinessException("创建约束字段名为空或类型为空");
        }
        if (ConstraintTypeEnum.VIRTUAL.getName().equals(param.getConstraintType()) || ConstraintTypeEnum.FOREIGN_KEY.getName().equals(param.getConstraintType())) {
            if (StringUtils.isBlank(param.getForkSchema()) || StringUtils.isBlank(param.getForkTable()) || StringUtils.isBlank(param.getForkColumn())) {
                throw new BusinessException("修改外键约束时,参数缺失");
            }
        }
        List<String> sqls = new ArrayList<>();
        String sql = Chat2DBContext.getSqlBuilder().createTableConstraint(
                param.getSchemaName(), param.getTableName(), param.getColumnName(), param.getConstraintName(), param.getConstraintType(),
                StringUtils.isNotBlank(param.getCheck()) ? param.getCheck() : null,
                StringUtils.isNotBlank(param.getForkSchema()) ? param.getForkSchema() : null,
                StringUtils.isNotBlank(param.getForkTable()) ? param.getForkTable() : null,
                StringUtils.isNotBlank(param.getForkColumn()) ? param.getForkColumn() : null);
        if (StrUtil.isBlank(sql)) {
            throw new BusinessException("创建约束SQL生成异常");
        }
        sqls.add(sql);
        if (!param.getEnabled()) {
            String unSql = Chat2DBContext.getSqlBuilder().unEnabledConstraint(param.getSchemaName(), param.getTableName(), param.getConstraintName());
            sqls.add(unSql);
        }
        return sqls;
    }

    @Override
    public AssociationTree queryReferencedList(TypeQueryRequest request) {
        String key = CacheConstants.TABLE_UN_REFERENCED_ + "v3_" + request.getDataSourceId() + request.getSchemaName() + request.getTableName();
        if (!request.getIsRefreshCache() && redisCache.hasKey(key)) {
            return redisCache.getCacheObject(key);
        }
        List<ForeignData> foreignKeys = Chat2DBContext.getMetaData().beiForeGnKey(Chat2DBContext.getConnection(), request.getSchemaName(), request.getTableName());
        if (CollUtil.isEmpty(foreignKeys)) {
            return null;
        }

        List<AssociationTree> list = buildReferencedAssociations(foreignKeys);
        AssociationTree associationTree = AssociationTree.builder()
                .id(System.currentTimeMillis() * 1000 + new Random().nextInt(10000) + "")
                .tableName(request.getTableName())
                .schemaName(request.getSchemaName())
                .type("TABLE")
                .status(foreignKeys.get(0).getStatus())
                .children(list)
                .build();
        redisCache.setCacheObject(key, associationTree);
        redisCache.expire(key, 1, TimeUnit.DAYS);
        log.info("被引用情况数:" + list.size());
        return associationTree;
    }

    static List<AssociationTree> buildReferencedAssociations(List<ForeignData> foreignKeys) {
        Map<String, AssociationTree> relations = new LinkedHashMap<>();
        for (ForeignData foreignKey : foreignKeys) {
            String relationKey = String.join("\u0000",
                    StringUtils.defaultString(foreignKey.getForeignSchemaName()),
                    StringUtils.defaultString(foreignKey.getForeignTableName()),
                    StringUtils.defaultString(foreignKey.getConstraintName()));
            AssociationTree relation = relations.get(relationKey);
            if (relation == null) {
                relation = AssociationTree.builder()
                        .id(UUID.randomUUID().toString())
                        .tableName(foreignKey.getForeignTableName())
                        .schemaName(foreignKey.getForeignSchemaName())
                        .column(foreignKey.getColumn())
                        .foreignColumnName(foreignKey.getForeignColumnName())
                        .constraintName(foreignKey.getConstraintName())
                        .status(foreignKey.getStatus())
                        .type("TABLE")
                        .build();
                relations.put(relationKey, relation);
            } else {
                relation.setColumn(appendRelationColumn(relation.getColumn(), foreignKey.getColumn()));
                relation.setForeignColumnName(appendRelationColumn(
                        relation.getForeignColumnName(), foreignKey.getForeignColumnName()));
            }
        }
        return relations.values().stream()
                .sorted(Comparator.comparing(AssociationTree::getTableName)
                        .thenComparing(AssociationTree::getConstraintName))
                .collect(Collectors.toList());
    }

    private static String appendRelationColumn(String columns, String column) {
        if (StringUtils.isBlank(columns)) {
            return column;
        }
        if (StringUtils.isBlank(column) || Arrays.asList(columns.split(", ")).contains(column)) {
            return columns;
        }
        return columns + ", " + column;
    }

    @Override
    public AssociationTree queryReferenced(TypeQueryRequest request) {
        String key = CacheConstants.TABLE_REFERENCED_ + "v2_" + request.getDataSourceId() + request.getSchemaName() + request.getTableName();
        if (!request.getIsRefreshCache() && redisCache.hasKey(key)) {
            return redisCache.getCacheObject(key);
        }
        List<ForeignData> foreignKeys = Chat2DBContext.getMetaData().getUnForeignKey(Chat2DBContext.getConnection(), request.getTableName(), request.getSchemaName());
        if (CollUtil.isEmpty(foreignKeys)) {
            return null;
        }
        Set<ForeignData> foreignKeySet = new HashSet<>();
        for (ForeignData foreignKey : foreignKeys) {
            ForeignData foreignData = new ForeignData();
            foreignData.setForeignTableName(foreignKey.getForeignTableName());
            foreignData.setForeignSchemaName(foreignKey.getForeignSchemaName());
            foreignData.setColumn(foreignKey.getColumn());
            foreignData.setForeignColumnName(foreignKey.getForeignColumnName());
            foreignData.setConstraintName(foreignKey.getConstraintName());
            foreignData.setStatus(foreignKey.getStatus());
            foreignKeySet.add(foreignData);
        }
        List<AssociationTree> list = new ArrayList<>();
        for (ForeignData foreignKey : foreignKeySet) {
            AssociationTree associationTree = AssociationTree.builder()
                    .id(System.currentTimeMillis() * 1000 + new Random().nextInt(10000) + "")
                    .tableName(foreignKey.getForeignTableName())
                    .schemaName(foreignKey.getForeignSchemaName())
                    .column(foreignKey.getColumn())
                    .foreignColumnName(foreignKey.getForeignColumnName())
                    .constraintName(foreignKey.getConstraintName())
                    .status(foreignKey.getStatus())
                    .type("TABLE")
                    .build();
            list.add(associationTree);
            TypeQueryRequest pream = new TypeQueryRequest();
            pream.setDataSourceId(request.getDataSourceId());
            pream.setSchemaName(foreignKey.getForeignSchemaName());
            pream.setTableName(foreignKey.getForeignTableName());
            queryReferenced(pream);
        }
        AssociationTree associationTree = AssociationTree.builder()
                .id(System.currentTimeMillis() * 1000 + new Random().nextInt(10000) + "")
                .tableName(foreignKeys.get(0).getTableName())
                .schemaName(foreignKeys.get(0).getSchemaName())
                .type("TABLE")
                .status(foreignKeys.get(0).getStatus())
                .children(list)
                .build();
        redisCache.setCacheObject(key, associationTree);
        redisCache.expire(key, 1, TimeUnit.DAYS);
        return associationTree;
    }


    @Override
    public AssociationTree queryViewReferenced(TypeQueryRequest request) {
        Set<ViewReferencedData> referencedData = new HashSet<>();
        if ("1".equals(request.getCheck())) {
            referencedData.addAll(Chat2DBContext.getMetaData().getViewReferenced(Chat2DBContext.getConnection(), request.getSchemaName(), request.getTableName()));
        } else {
            referencedData.addAll(Chat2DBContext.getMetaData().getViewUponReferenced(Chat2DBContext.getConnection(), request.getSchemaName(), request.getTableName()));
        }
        if (CollUtil.isEmpty(referencedData)) {
            return null;
        }
        List<ViewReferencedData> viewReferenced = new ArrayList<>(referencedData);
        List<AssociationTree> list = new ArrayList<>();
        for (ViewReferencedData viewReferencedData : viewReferenced) {
            AssociationTree associationTree = AssociationTree.builder()
                    .id(System.currentTimeMillis() * 1000 + new Random().nextInt(10000) + "")
                    .tableName(viewReferencedData.getReferencedViewName())
                    .schemaName(viewReferencedData.getReferencedSchemaName())
                    .status(viewReferencedData.getStatus())
                    .type(viewReferencedData.getReferencedViewType())
                    .build();
            list.add(associationTree);
            TypeQueryRequest pream = new TypeQueryRequest();
            pream.setDataSourceId(request.getDataSourceId());
            pream.setCheck(request.getCheck());
            pream.setSchemaName(viewReferencedData.getReferencedSchemaName());
            pream.setTableName(viewReferencedData.getReferencedViewName());
            queryViewReferenced(pream);
        }
        return AssociationTree.builder()
                .id(System.currentTimeMillis() * 1000 + new Random().nextInt(10000) + "")
                .tableName(viewReferenced.get(0).getViewName())
                .schemaName(viewReferenced.get(0).getSchemaName())
                .type(viewReferenced.get(0).getViewType())
                .status(viewReferenced.get(0).getStatus())
                .children(list)
                .build();
    }

    @Override
    public List<TableRole> queryRoles(TypeQueryRequest request) {
        return Chat2DBContext.getMetaData().queryRoles(Chat2DBContext.getConnection(), request.getSchemaName(), request.getTableName());
    }

    @Override
    public TableMeta queryTableMeta(TypeQueryParam param) {
        MetaData metaSchema = Chat2DBContext.getMetaData();
        TableMeta tableMeta = metaSchema.getTableMeta(null, null, null);
        if (tableMeta != null) {
            List<IndexType> indexTypes = tableMeta.getIndexTypes();
            List<ColumnType> columnTypes = tableMeta.getColumnTypes();
            columnTypes = columnTypes.stream().sorted(Comparator.comparing(ColumnType::getTypeName).reversed()).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(indexTypes)) {
                tableMeta.setIndexTypes(indexTypes);
            }
            if (CollectionUtils.isNotEmpty(columnTypes)) {
                tableMeta.setColumnTypes(columnTypes);
            }
        }
        return tableMeta;
    }

    @Override
    public ActionResult saveTableVector(TableVectorParam param) {
        if (checkTableVector(param).getData()) {
            return ActionResult.isSuccess();
        }
        TableVectorMappingDO mappingDO = tableConverter.toTableVectorMappingDO(param);
        mappingDO.setStatus(TableVectorEnum.SAVED.getCode());
        tableVectorMappingMapper.insert(mappingDO);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<Boolean> checkTableVector(TableVectorParam param) {
        LambdaQueryWrapper<TableVectorMappingDO> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(TableVectorMappingDO::getApiKey, param.getApiKey());
        queryWrapper.eq(TableVectorMappingDO::getDataSourceId, param.getDataSourceId());
        queryWrapper.eq(TableVectorMappingDO::getDatabase, param.getDatabase());
        queryWrapper.eq(TableVectorMappingDO::getSchema, param.getSchema());
        TableVectorMappingDO mappingDO = tableVectorMappingMapper.selectOne(queryWrapper);
        if (Objects.nonNull(mappingDO) && TableVectorEnum.SAVED.getCode().equals(mappingDO.getStatus())) {
            return DataResult.of(true);
        }
        return DataResult.of(false);
    }

    @Override
    public DataResult<String> copyDmlSql(DmlSqlCopyParam param) {
        List<TableColumn> columns = queryColumns(param);
        SqlBuilder sqlBuilder = Chat2DBContext.getSqlBuilder();
        Table table = Table.builder().name(param.getTableName()).columnList(columns).build();
        String sql = sqlBuilder.getTableDmlSql(table, param.getType());
        return DataResult.of(sql);
    }

    @Override
    public DataResult<TableImportVo> importTable(TableImportRequest request) {
        if (StrUtil.isAllEmpty(request.getDatabaseName(), request.getSchemaName()) ||
                StrUtil.isEmpty(request.getImportUrl())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "参数异常");
        }
        LoginUser loginUser = ContextUtils.getLoginUser();
        String key = CacheConstants.IMPORT_TABLE_KEY + loginUser.getId() + ":" + StrUtil.nullToEmpty(request.getDatabaseName())
                + ":" + StrUtil.nullToEmpty(request.getSchemaName()) + ":" + StrUtil.nullToEmpty(request.getTableName());
        if (redisCache.hasKey(key)) {
            return DataResult.of(redisCache.getCacheObject(key));
        }
        if (StrUtil.isEmpty(request.getImportUrl())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "文件不存在");
        }

        // 本地资源路径
        final File file = resolveImportFile(request.getImportUrl());
        if (file == null) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "文件路径非法");
        }
        String importUrl = file.getAbsolutePath();
        if (!file.exists()) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "文件不存在");
        }
        TableImportVo vo = new TableImportVo();
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
            fileName = URLEncoder.encode("ImportTable_" +
                                    StrUtil.nullToEmpty(request.getDatabaseName()) + "_"
                                    + StrUtil.nullToEmpty(request.getSchemaName()) + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                                    + ".log",
                            StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            vo.setStatus(TaskStatusEnum.ERROR.name());
            return DataResult.of(vo);
        }
        String filePath = HzbConfig.getUploadPath();
        File logFile = FileUtil.newFile(filePath + "/" + fileName);
        vo.setLogFile(logFile.getAbsolutePath());
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();
        redisCache.setCacheObject(key, vo);
        CompletableFuture.runAsync(() -> {
            try {
                buildContext(loginUser, connectInfo);
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".sql")) {
                    doImportSQL(request, file, logFile, vo, key);
                } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    doImportCSV(request, file, logFile, vo, key);
                } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".xls")
                        || file.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                    doImportExcel(request, file, logFile, vo, key);
                } else {
                    vo.setStatus(TaskStatusEnum.ERROR.name());
                    vo.setMessage(Arrays.asList("不支持的文件格式"));
                }
            } catch (Exception e) {
                log.error("table import error", e);
                vo.setStatus(TaskStatusEnum.ERROR.name());
                vo.setMessage(Arrays.asList(e.getMessage()));
                redisCache.setCacheObject(key, vo);
                throw new RuntimeException(e);
            } finally {
                removeContext();
            }
        }).whenComplete((aVoid, throwable) -> {
            TableImportVo vo1 = redisCache.getCacheObject(key);
            if (throwable != null) {
                vo.setStatus(TaskStatusEnum.ERROR.name());
                if (Validator.isEmpty(vo.getMessage())) {
                    vo.setMessage(Arrays.asList(throwable.getMessage()));
                }
                redisCache.setCacheObject(key, vo);
            } else if (Validator.isEmpty(vo1)) {
                if (!StrUtil.equals(TaskStatusEnum.ERROR.name(), vo.getStatus())) {
                    vo.setStatus(TaskStatusEnum.FINISH.name());
                }
                redisCache.setCacheObject(key, vo);
            } else if (!StrUtil.equals(TaskStatusEnum.ERROR.name(), vo1.getStatus())) {
                vo1.setStatus(TaskStatusEnum.FINISH.name());
                redisCache.setCacheObject(key, vo1);
            }
            FileUtils.deleteFile(file.getAbsolutePath());
        });
        return DataResult.of(vo);
    }


    @Override
    public DataResult<TableImportVo> importTable1(TableImportRequest request) {
        if (StrUtil.isAllEmpty(request.getDatabaseName(), request.getSchemaName()) ||
                StrUtil.isEmpty(request.getImportUrl())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "参数异常");
        }
        LoginUser loginUser = ContextUtils.getLoginUser();
        String key = CacheConstants.IMPORT_TABLE_KEY + loginUser.getId() + ":" + StrUtil.nullToEmpty(request.getDatabaseName())
                + ":" + StrUtil.nullToEmpty(request.getSchemaName()) + ":" + StrUtil.nullToEmpty(request.getTableName());
        if (redisCache.hasKey(key)) {
            return DataResult.of(redisCache.getCacheObject(key));
        }
        if (StrUtil.isEmpty(request.getImportUrl())) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "文件不存在");
        }

        List<Integer> indexList = new ArrayList<>();
        List<String> collect1 = new ArrayList<>();
        List<String> headerNames = new ArrayList<>();
        List<String> autoIncrementList = new ArrayList<>();
        // 本地资源路径
        final File file = resolveImportFile(request.getImportUrl());
        if (file == null) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "文件路径非法");
        }
        String importUrl = file.getAbsolutePath();
        if (!file.exists()) {
            return DataResult.error(EasyToolsConstant.ERROR_CODE, "文件不存在");
        }

        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".csv") || file.getName().toLowerCase(Locale.ROOT).endsWith(".xls")
                || file.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            ExecuteResult execute = new ExecuteResult();

            DmlTableRequest dmlTableRequest = new DmlTableRequest();
            dmlTableRequest.setTableName(request.getTableName());
            dmlTableRequest.setSchemaName(request.getSchemaName());
            dmlTableRequest.setDataSourceId(request.getDataSourceId());
            DlExecuteParam param = rdbWebConverter.request2param(dmlTableRequest);
            ListResult<ExecuteResult> executeResultListResult = dlTemplateService.executeSelectTable(param);

            execute = executeResultListResult.getData().get(0);

            int index = 0;
            for (Header header : execute.getHeaderList()) {
                headerNames.add(header.getName());
                if (Validator.isNotEmpty(header.getPrimaryKey()) && header.getPrimaryKey()) {
                    indexList.add(index);
                }
                if (Validator.isNotEmpty(header.getAutoIncrement()) && header.getAutoIncrement() == 1) {
                    autoIncrementList.add(header.getName());
                }
                index = index + 1;
            }
            List<List<String>> dataList = execute.getDataList();
            for (List<String> list : dataList) {
                StringBuilder datas = new StringBuilder();
                for (Integer i : indexList) {
                    datas.append(list.get(i) + "-");
                }
                collect1.add(datas.deleteCharAt(datas.length() - 1).toString());
            }
        }
        TableImportVo vo = new TableImportVo();
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
            fileName = URLEncoder.encode("ImportTable_" +
                                    StrUtil.nullToEmpty(request.getDatabaseName()) + "_"
                                    + StrUtil.nullToEmpty(request.getSchemaName()) + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER)
                                    + ".log",
                            StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
            vo.setStatus(TaskStatusEnum.ERROR.name());
            return DataResult.of(vo);
        }
        String filePath = HzbConfig.getUploadPath();
        File logFile = FileUtil.newFile(filePath + "/" + fileName);
        vo.setLogFile(logFile.getAbsolutePath());
        ConnectInfo connectInfo = Chat2DBContext.getConnectInfo().copy();
        redisCache.setCacheObject(key, vo);
        List<String> datasList = collect1;
        CompletableFuture.runAsync(() -> {
            try {
                buildContext(loginUser, connectInfo);
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".sql")) {
                    doImportSQL(request, file, logFile, vo, key);
                } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    doImportCSV1(request, file, logFile, vo, key, indexList, datasList, headerNames, autoIncrementList);
                } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".xls")
                        || file.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                    doImportExcel1(request, file, logFile, vo, key, indexList, datasList, headerNames, autoIncrementList);
                } else {
                    vo.setStatus(TaskStatusEnum.ERROR.name());
                    vo.setMessage(Arrays.asList("不支持的文件格式"));
                }
            } catch (Exception e) {
                log.error("table import error", e);
                vo.setStatus(TaskStatusEnum.ERROR.name());
                vo.setMessage(Arrays.asList(e.getMessage()));
                redisCache.setCacheObject(key, vo);
                throw new RuntimeException(e);
            } finally {
                removeContext();
            }
        }).whenComplete((aVoid, throwable) -> {
            TableImportVo vo1 = redisCache.getCacheObject(key);
            if (throwable != null) {
                vo.setStatus(TaskStatusEnum.ERROR.name());
                if (Validator.isEmpty(vo.getMessage())) {
                    vo.setMessage(Arrays.asList(throwable.getMessage()));
                }
                redisCache.setCacheObject(key, vo);
            } else if (Validator.isEmpty(vo1)) {
                if (!StrUtil.equals(TaskStatusEnum.ERROR.name(), vo.getStatus())) {
                    vo.setStatus(TaskStatusEnum.FINISH.name());
                }
                redisCache.setCacheObject(key, vo);
            } else if (!StrUtil.equals(TaskStatusEnum.ERROR.name(), vo1.getStatus())) {
                vo1.setStatus(TaskStatusEnum.FINISH.name());
                redisCache.setCacheObject(key, vo1);
            }
            FileUtils.deleteFile(file.getAbsolutePath());
        });
        return DataResult.of(vo);
    }

    /**
     * Excel文件导入   武汉版
     *
     * @param request
     * @param importFile
     * @param logFile
     * @param vo
     * @param key
     */
    private void doImportExcel1(TableImportRequest request, File importFile, File logFile, TableImportVo vo,
                                String key, List<Integer> indexList, List<String> collect, List<String> hearderNames, List<String> autoIncrementList) {
        initRequest(request);
        vo.setStatus(TaskStatusEnum.PROCESSING.name());
        redisCache.setCacheObject(key, vo);
        final Connection connection = Chat2DBContext.getConnection();
        Statement statement = null;
        try {
            if (request.getErrorRollback()) {
                connection.setAutoCommit(false);
            }
            statement = connection.createStatement();

            String logFormat = "%s|%s|%s";
            int exported = 0;
            int errorNum = 0;
            //当导入数据表头不为空时，该集合存储表头字段，与拼接的复合主键对应的字段下标
            List<Integer> indexs = new ArrayList<>();
            //列参数
            String heards = "";
            try (PrintWriter printWriter = new PrintWriter(logFile, StandardCharsets.UTF_8.name());
                 final ExcelReader reader = ExcelUtil.getReader(importFile)) {
                // 读取第一个sheet的所有数据
                List<List<Object>> allRows = reader.read(0);
                if (CollUtil.isEmpty(allRows)) {
                    vo.setStatus(TaskStatusEnum.ERROR.name());
                    vo.setMessage(Arrays.asList("Excel文件为空"));
                    redisCache.setCacheObject(key, vo);
                    return;
                }
                vo.setTotal(allRows.size());
                MetaData metaData = Chat2DBContext.getMetaData();
                String tableName = metaData.getMetaDataName(request.getDatabaseName(), request.getSchemaName(),
                        request.getTableName());
                StringBuilder sql = new StringBuilder();
                StringBuilder titleColumn = new StringBuilder();
                //存储自增字段在表头中的下标
                List<Integer> autoIndexList = new ArrayList<>();
                int ind = 0;
                if (request.getHaveTitle()) {
                    //有表头
                    for (Object s : allRows.get(0)) {
                        if (!autoIncrementList.contains(s.toString())) {
                            if (Validator.isEmpty(s)) {
                                titleColumn.append(metaData.getMetaDataName("")).append(",");
                            } else {
                                titleColumn.append(metaData.getMetaDataName(s.toString())).append(",");
                            }
                            autoIndexList.add(ind);
                        }
                        ind++;
                    }
                    titleColumn.deleteCharAt(titleColumn.length() - 1);
                    StringBuilder heardName = new StringBuilder();
                    //获取主键在插入数据中表头的位置
                    for (Integer s : indexList) {
                        indexs.add(allRows.get(0).indexOf(hearderNames.get(s)));
                    }
                }

                for (List<Object> row : allRows) {
                    vo.setExported(exported);
                    exported++;
                    if (request.getStartRow() > vo.getExported() && request.getHaveTitle()) {
                        continue;
                    }
                    if (row.size() < hearderNames.size()) {
                        for (int i = 1; i <= hearderNames.size() - row.size(); i++) {
                            row.add("");
                        }
                    }
                    //sql拼接字段
                    StringBuilder datas = new StringBuilder();
                    //获取主键集合
                    List<String> columnParam = new ArrayList<>();
                    if (request.getHaveTitle()) {
                        for (Integer index : indexs) {
                            datas.append(row.get(index)).append("-");
                            columnParam.add(String.valueOf(allRows.get(0).get(index)));
                        }
                    } else {
                        for (Integer index : indexList) {
                            datas.append(row.get(index)).append("-");
                            columnParam.add(hearderNames.get(index));
                        }
                    }
                    String s1 = datas.deleteCharAt(datas.length() - 1).toString();
                    heards = columnParam.stream().collect(Collectors.joining("||-||"));
                    //判断数据库的表数据中是否包含该主键，从而进行更新或者插入操作
                    if (collect.contains(s1)) {
                        sql.append("UPDATE ").append(tableName).append(" SET ");
                        if (request.getHaveTitle()) {
                            for (int i = 0; i < allRows.get(0).size(); i++) {
                                if (!columnParam.contains(String.valueOf(allRows.get(0).get(i)))) {
                                    sql.append("\"").append(String.valueOf(allRows.get(0).get(i))).append("\"='").append(Validator.isNotEmpty(row.get(i)) ? String.valueOf(row.get(i)) : "").append("',");
                                }
                            }
                        }
                        sql.deleteCharAt(sql.length() - 1).append(" WHERE ").append(heards).append("='").append(s1).append("';");
                    } else {
                        sql.append("INSERT INTO ").append(tableName);
                        sql.append(" (").append(titleColumn).append(") ");
                        sql.append(" VALUES (");
                        int index = 0;
                        for (Object s : row) {
                            if (autoIndexList.contains(index)) {
                                if (Validator.isEmpty(s) ||
                                        String.valueOf(s).equalsIgnoreCase("null")) {
                                    sql.append("null,");
                                } else {
                                    sql.append("'").append(String.valueOf(s)).append("',");
                                }
                            }
                            index++;
                        }
                        sql.deleteCharAt(sql.length() - 1);
                        sql.append(");");
                    }
                    if (vo.getMessage().size() > 50) {
                        vo.getMessage().remove(0);
                    }
                    try {
                        //可能是操作 数据库 的同时又在idea执行executeUpdate()代码，前者没有commit导致数据库锁表了,会长时间等待
                        statement.executeUpdate(sql.toString());
                        final String format = String.format(logFormat,
                                LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                                , "第" + exported + "行", "执行成功");
                        vo.getMessage().add(format);
                        printWriter.println(format);
                    } catch (SQLException e) {
                        errorNum++;
                        final String format = String.format(logFormat,
                                LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                                , "第" + exported + "行", "执行失败|" + e.getMessage());
                        vo.getMessage().add(format);
                        vo.setErrorNum(errorNum);
                        printWriter.println(format);
                    }
                    redisCache.setCacheObject(key, vo);
                    sql.setLength(0); // 清空StringBuilder以便存储下一个SQL语句
                    if (errorNum > 0 && request.getErrorStop()) {
                        break;
                    }
                }
                if (errorNum > 0 && request.getErrorRollback()) {
                    connection.rollback();
                }
                if (errorNum == 0 && request.getErrorRollback()) {
                    connection.commit();
                }
            } catch (Exception e) {
                rollbackImport(connection, request);
                vo.setStatus(TaskStatusEnum.ERROR.name());
                vo.setMessage(Arrays.asList(e.getMessage()));
                redisCache.setCacheObject(key, vo);
                return;
            }
        } catch (SQLException e) {
            rollbackImport(connection, request);
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


    /**
     * CSV文件导入    武汉版
     *
     * @param request
     * @param importFile        导入表地址
     * @param logFile           日志文件地址
     * @param vo
     * @param key
     * @param indexList         主键在列名列表里面的位置
     * @param collect           数据库已有的所有主键拼接列表
     * @param hearderNames      列名列表
     * @param autoIncrementList 自增字段集合
     */
    private void doImportCSV1(TableImportRequest request, File importFile, File logFile, TableImportVo vo,
                              String key, List<Integer> indexList, List<String> collect, List<String> hearderNames, List<String> autoIncrementList) {
        //默认无表头
        initRequest(request);
        vo.setStatus(TaskStatusEnum.PROCESSING.name());
        redisCache.setCacheObject(key, vo);
        final Connection connection = Chat2DBContext.getConnection();
        Statement statement = null;
        try {
            if (request.getErrorRollback()) {
                connection.setAutoCommit(false);
            }
            statement = connection.createStatement();

            String logFormat = "%s|%s|%s";
            int exported = 0;
            int errorNum = 0;
            //当导入数据表头不为空时，该集合存储表头字段，与拼接的复合主键对应的字段下标
            List<Integer> indexs = new ArrayList<>();
            //列参数
            String heards = "";
            try (PrintWriter printWriter = new PrintWriter(logFile, StandardCharsets.UTF_8.name());
                 CsvReader reader = new CsvReader(importFile, CsvReadConfig.defaultConfig())) {
                StringBuilder sql = new StringBuilder();
                final CsvData read = reader.read();
                vo.setTotal(read.getRowCount());
                MetaData metaData = Chat2DBContext.getMetaData();
                String tableName = metaData.getMetaDataName(request.getDatabaseName(), request.getSchemaName(),
                        request.getTableName());
                StringBuilder titleColumn = new StringBuilder();
                //存储自增字段在表头中的下标
                List<Integer> autoIndexList = new ArrayList<>();
                int ind = 0;
                //判断是否包含表头
                if (request.getHaveTitle()) {
                    //有表头
                    for (String s : read.getRow(0)) {
                        //导入csv文件表头字符串出现zwnbsp字符（零宽度空白字符）处理
                        if (s.startsWith(EasyToolsConstant.UTF8_BOM)) {
                            s = s.substring(1);
                        }
                        //当数据库中不包含当前数据中的主键，则用该插入语句片段拼凑插入语句（包含复合主键）
                        //判断字段是否是自增字段，自增字段不传值
                        if (!autoIncrementList.contains(s)) {
                            titleColumn.append(metaData.getMetaDataName(s)).append(",");
                            autoIndexList.add(ind);
                        }
                        ind++;
                    }
                    titleColumn.deleteCharAt(titleColumn.length() - 1);
                    StringBuilder heardName = new StringBuilder();
                    //获取主键在插入数据中表头的位置
                    for (Integer s : indexList) {
                        indexs.add(read.getRow(0).indexOf(hearderNames.get(s)));
                    }
                }
                for (CsvRow row : read.getRows()) {
                    vo.setExported(exported);
                    exported++;
                    if (request.getStartRow() > vo.getExported() && request.getHaveTitle()) {
                        continue;
                    }
                    //sql拼接字段
                    StringBuilder datas = new StringBuilder();
                    //获取主键集合
                    List<String> columnParam = new ArrayList<>();
                    if (request.getHaveTitle()) {
                        for (Integer index : indexs) {
                            datas.append(row.get(index)).append("-");
                            columnParam.add(read.getRows().get(0).get(index));
                        }
                    } else {
                        for (Integer index : indexList) {
                            datas.append(row.get(index)).append("-");
                            columnParam.add(hearderNames.get(index));
                        }
                    }
                    String s1 = datas.deleteCharAt(datas.length() - 1).toString();
                    heards = columnParam.stream().collect(Collectors.joining("||-||"));
                    //判断数据库的表数据中是否包含该主键，从而进行更新或者插入操作
                    if (collect.contains(s1)) {
                        sql.append("UPDATE ").append(tableName).append(" SET ");
                        if (request.getHaveTitle()) {
                            for (int i = 0; i < read.getRow(0).size(); i++) {
                                if (!columnParam.contains(read.getRow(0).get(i))) {
                                    sql.append("\"").append(read.getRow(0).get(i)).append("\"='").append(row.get(i)).append("',");
                                }
                            }
                        } else {
                            for (int i = 0; i < read.getRow(0).size(); i++) {
                                if (columnParam.contains(hearderNames.get(i))) {
                                    sql.append("\"").append(hearderNames.get(i)).append("\"='").append(row.get(i)).append("',");
                                }
                            }
                        }
                        sql.deleteCharAt(sql.length() - 1).append(" WHERE ").append(heards).append("='").append(s1).append("';");
                    } else {
                        sql.append("INSERT INTO ").append(tableName);
                        sql.append(" (").append(titleColumn).append(") ");
                        sql.append(" VALUES (");
                        int index = 0;
                        for (String s : row) {
                            if (autoIndexList.contains(index)) {
                                if (StrUtil.isBlank(s) ||
                                        s.equalsIgnoreCase("null")) {
                                    sql.append("null,");
                                } else {
                                    sql.append("'").append(s).append("',");
                                }
                            }
                            index++;
                        }
                        sql.deleteCharAt(sql.length() - 1);
                        sql.append(");");
                    }

                    if (vo.getMessage().size() > 50) {
                        vo.getMessage().remove(0);
                    }
                    ;
                    try {
                        //可能是操作 数据库 的同时又在idea执行executeUpdate()代码，前者没有commit导致数据库锁表了,会长时间等待
                        statement.executeUpdate(sql.toString());
                        final String format = String.format(logFormat,
                                LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                                , "第" + exported + "行", "执行成功");
                        vo.getMessage().add(format);
                        printWriter.println(format);
                    } catch (SQLException e) {
                        errorNum++;
                        final String format = String.format(logFormat,
                                LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                                , "第" + exported + "行", "执行失败|" + sql + "|" + e.getMessage());
                        vo.getMessage().add(format);
                        vo.setErrorNum(errorNum);
                        printWriter.println(format);
                    }
                    redisCache.setCacheObject(key, vo);
                    sql.setLength(0); // 清空StringBuilder以便存储下一个SQL语句
                    if (errorNum > 0 && request.getErrorStop()) {
                        break;
                    }
                }
                if (errorNum > 0 && request.getErrorRollback()) {
                    connection.rollback();
                }
                if (errorNum == 0 && request.getErrorRollback()) {
                    connection.commit();
                }
            } catch (Exception e) {
                rollbackImport(connection, request);
                vo.setStatus(TaskStatusEnum.ERROR.name());
                vo.setMessage(Arrays.asList(e.getMessage()));
                redisCache.setCacheObject(key, vo);
                return;
            }
        } catch (SQLException e) {
            rollbackImport(connection, request);
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


    /**
     * Excel文件导入
     *
     * @param request
     * @param importFile
     * @param logFile
     * @param vo
     * @param key
     */
    private void doImportExcel(TableImportRequest request, File importFile, File logFile, TableImportVo vo,
                               String key) {
        initRequest(request);
        vo.setStatus(TaskStatusEnum.PROCESSING.name());
        redisCache.setCacheObject(key, vo);
        final Connection connection = Chat2DBContext.getConnection();
        Statement statement = null;
        try {
            if (request.getErrorRollback()) {
                connection.setAutoCommit(false);
            }
            statement = connection.createStatement();

            String logFormat = "%s|%s|%s";
            int exported = 0;
            int errorNum = 0;
            try (PrintWriter printWriter = new PrintWriter(logFile, StandardCharsets.UTF_8.name());
                 final ExcelReader reader = ExcelUtil.getReader(importFile)) {
                // 读取第一个sheet的所有数据
                List<List<Object>> allRows = reader.read(0);
                if (CollUtil.isEmpty(allRows)) {
                    vo.setStatus(TaskStatusEnum.ERROR.name());
                    vo.setMessage(Arrays.asList("Excel文件为空"));
                    redisCache.setCacheObject(key, vo);
                    return;
                }
                vo.setTotal(allRows.size());
                MetaData metaData = Chat2DBContext.getMetaData();
                String tableName = metaData.getMetaDataName(request.getDatabaseName(), request.getSchemaName(),
                        request.getTableName());
                StringBuilder sql = new StringBuilder();
                StringBuilder titleColumn = new StringBuilder();
                if (request.getHaveTitle()) {
                    //有表头
                    for (Object s : allRows.get(0)) {
                        if (Validator.isEmpty(s)) {
                            titleColumn.append(metaData.getMetaDataName("")).append(",");
                        } else {
                            titleColumn.append(metaData.getMetaDataName(s.toString())).append(",");
                        }
                    }
                    titleColumn.deleteCharAt(titleColumn.length() - 1);
                }
                for (List<Object> row : allRows) {
                    exported++;
                    vo.setExported(exported);
                    if (request.getStartRow() > exported) {
                        continue;
                    }
                    sql.append("INSERT INTO ").append(tableName);
                    if (request.getHaveTitle()) {
                        sql.append(" (").append(titleColumn).append(") ");
                    }
                    sql.append(" VALUES (");
                    for (Object s : row) {
                        if (Validator.isEmpty(s) ||
                                StrUtil.equals(s.toString(), "null")
                                || StrUtil.equals(s.toString(), "NULL")) {
                            sql.append("null,");
                        } else {
                            sql.append("'").append(s).append("',");
                        }
                    }
                    sql.deleteCharAt(sql.length() - 1);
                    sql.append(");");
                    if (vo.getMessage().size() > 50) {
                        vo.getMessage().remove(0);
                    }
                    try {
                        //可能是操作 数据库 的同时又在idea执行executeUpdate()代码，前者没有commit导致数据库锁表了,会长时间等待
                        statement.executeUpdate(sql.toString());
                        final String format = String.format(logFormat,
                                LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                                , "第" + exported + "行", "执行成功");
                        vo.getMessage().add(format);
                        printWriter.println(format);
                    } catch (SQLException e) {
                        errorNum++;
                        final String format = String.format(logFormat,
                                LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                                , "第" + exported + "行", "执行失败|" + e.getMessage());
                        vo.getMessage().add(format);
                        vo.setErrorNum(errorNum);
                        printWriter.println(format);
                    }
                    redisCache.setCacheObject(key, vo);
                    sql.setLength(0); // 清空StringBuilder以便存储下一个SQL语句
                    if (errorNum > 0 && request.getErrorStop()) {
                        break;
                    }
                }
                if (errorNum > 0 && request.getErrorRollback()) {
                    connection.rollback();
                }
                if (errorNum == 0 && request.getErrorRollback()) {
                    connection.commit();
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


    /**
     * CSV文件导入
     *
     * @param request
     * @param importFile
     * @param logFile
     * @param vo
     * @param key
     */
    private void doImportCSV(TableImportRequest request, File importFile, File logFile, TableImportVo vo, String key) {
        //默认无表头
        initRequest(request);
        vo.setStatus(TaskStatusEnum.PROCESSING.name());
        redisCache.setCacheObject(key, vo);
        final Connection connection = Chat2DBContext.getConnection();
        Statement statement = null;
        try {
            if (request.getErrorRollback()) {
                connection.setAutoCommit(false);
            }
            statement = connection.createStatement();

            String logFormat = "%s|%s|%s";
            int exported = 0;
            int errorNum = 0;
            try (PrintWriter printWriter = new PrintWriter(logFile, StandardCharsets.UTF_8.name());
                 CsvReader reader = new CsvReader(importFile, CsvReadConfig.defaultConfig())) {
                StringBuilder sql = new StringBuilder();
                final CsvData read = reader.read();
                vo.setTotal(read.getRowCount());
                MetaData metaData = Chat2DBContext.getMetaData();
                String tableName = metaData.getMetaDataName(request.getDatabaseName(), request.getSchemaName(),
                        request.getTableName());
                StringBuilder titleColumn = new StringBuilder();
                if (request.getHaveTitle()) {
                    //有表头
                    for (String s : read.getRow(0)) {
                        //导入csv文件表头字符串出现zwnbsp字符（零宽度空白字符）处理
                        if (s.startsWith(EasyToolsConstant.UTF8_BOM)) {
                            s = s.substring(1);
                        }
                        titleColumn.append(metaData.getMetaDataName(s)).append(",");
                    }
                    titleColumn.deleteCharAt(titleColumn.length() - 1);
                }
                for (CsvRow row : read.getRows()) {
                    vo.setExported(exported);
                    if (request.getStartRow() > exported) {
                        continue;
                    }
                    exported++;
                    sql.append("INSERT INTO ").append(tableName);
                    if (request.getHaveTitle()) {
                        sql.append(" (").append(titleColumn).append(") ");
                    }
                    sql.append(" VALUES (");
                    for (String s : row) {
                        if (StrUtil.isBlank(s) ||
                                StrUtil.equals(s, "null")
                                || StrUtil.equals(s, "NULL")) {
                            sql.append("null,");
                        } else {
                            sql.append("'").append(s).append("',");
                        }
                    }
                    sql.deleteCharAt(sql.length() - 1);
                    sql.append(");");
                    if (vo.getMessage().size() > 50) {
                        vo.getMessage().remove(0);
                    }
                    ;
                    try {
                        //可能是操作 数据库 的同时又在idea执行executeUpdate()代码，前者没有commit导致数据库锁表了,会长时间等待
                        statement.executeUpdate(sql.toString());
                        final String format = String.format(logFormat,
                                LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                                , "第" + exported + "行", "执行成功");
                        vo.getMessage().add(format);
                        printWriter.println(format);
                    } catch (SQLException e) {
                        errorNum++;
                        final String format = String.format(logFormat,
                                LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                                , "第" + exported + "行", "执行失败|" + sql + "|" + e.getMessage());
                        vo.getMessage().add(format);
                        vo.setErrorNum(errorNum);
                        printWriter.println(format);
                    }
                    redisCache.setCacheObject(key, vo);
                    sql.setLength(0); // 清空StringBuilder以便存储下一个SQL语句
                    if (errorNum > 0 && request.getErrorStop()) {
                        break;
                    }
                }
                if (errorNum > 0 && request.getErrorRollback()) {
                    connection.rollback();
                }
                if (errorNum == 0 && request.getErrorRollback()) {
                    connection.commit();
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

    /**
     * SQL文件导入
     *
     * @param request
     * @param importFile
     * @param logFile
     * @param vo
     * @param key
     */
    private void doImportSQL(TableImportRequest request, File importFile, File logFile, TableImportVo vo, String key) {
        //默认错误不停止
        request.setErrorStop(Optional.ofNullable(request.getErrorStop()).orElse(false));
        //默认错误不回滚
        request.setErrorRollback(Optional.ofNullable(request.getErrorRollback()).orElse(false));
        vo.setStatus(TaskStatusEnum.PROCESSING.name());
        redisCache.setCacheObject(key, vo);
        final int totalLines = FileUtil.getTotalLines(importFile);
        vo.setTotal(totalLines);
        final Connection connection = Chat2DBContext.getConnection();
        Statement statement = null;
        try {
            if (request.getErrorRollback()) {
                connection.setAutoCommit(false);
            }
            statement = connection.createStatement();

            String logFormat = "%s|%s|%s";
            int exported = 0;
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
                        sqlStatement = sqlStatement.substring(0, sqlStatement.length() - 1).trim();
                        // 执行SQL语句
                        if (StrUtil.startWithIgnoreCase(sqlStatement, "INSERT")) {
                            if (vo.getMessage().size() > 50) {
                                vo.getMessage().remove(0);
                            }
                            errorNum = executeUpdate(request, vo, statement, logFormat, errorNum, printWriter, sqlStatement);
                            redisCache.setCacheObject(key, vo);
                        } else {
                            errorNum++;
                            final String format = String.format(logFormat,
                                    LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                                    , sqlStatement, "执行失败：" + "不是INSERT语句");
                            vo.getMessage().add(format);
                            vo.setErrorNum(errorNum);
                            redisCache.setCacheObject(key, vo);
                            printWriter.println(format);
                        }

                        sql.setLength(0); // 清空StringBuilder以便存储下一个SQL语句
                        if (errorNum > 0 && request.getErrorStop()) {
                            break;
                        }
                    }
                }
                if (sql.length() > 0) {
                    String sqlStatement = sql.toString().trim();
                    errorNum = executeUpdate(request, vo, statement, logFormat, errorNum, printWriter, sqlStatement);
                }
                if (errorNum > 0 && request.getErrorRollback()) {
                    connection.rollback();
                }
                if (errorNum == 0 && request.getErrorRollback()) {
                    connection.commit();
                }
            } catch (Exception e) {
                rollbackImport(connection, request);
                vo.setStatus(TaskStatusEnum.ERROR.name());
                vo.setMessage(Arrays.asList(e.getMessage()));
                redisCache.setCacheObject(key, vo);
                return;
            }
        } catch (SQLException e) {
            rollbackImport(connection, request);
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

    private int executeUpdate(TableImportRequest request, TableImportVo vo, Statement statement, String logFormat, int errorNum, PrintWriter printWriter, String sqlStatement) {
        if (StrUtil.indexOfIgnoreCase(sqlStatement, request.getTableName()) == -1) {
            errorNum++;
            final String format = String.format(logFormat,
                    LocalDateTime.now().format(DatePattern.NORM_DATETIME_MS_FORMATTER)
                    , sqlStatement, "执行失败：不包含表名" + request.getTableName());
            vo.getMessage().add(format);
            vo.setErrorNum(errorNum);
            printWriter.println(format);
            return errorNum;
        }
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

    private File resolveImportFile(String importUrl) {
        if (StrUtil.isBlank(importUrl) || !importUrl.startsWith(Constants.RESOURCE_PREFIX)) {
            return null;
        }
        Path profile = Paths.get(HzbConfig.getProfile()).toAbsolutePath().normalize();
        Path filePath = profile.resolve(importUrl.substring(Constants.RESOURCE_PREFIX.length()).replaceFirst("^[\\\\/]", ""))
                .normalize();
        if (!filePath.startsWith(profile)) {
            return null;
        }
        return filePath.toFile();
    }


    private void initRequest(TableImportRequest request) {
        //默认无表头
        request.setHaveTitle(Optional.ofNullable(request.getHaveTitle()).orElse(false));
        if (request.getHaveTitle()) {
            request.setStartRow(Optional.ofNullable(request.getStartRow()).orElse(1));
        } else {
            request.setStartRow(Optional.ofNullable(request.getStartRow()).orElse(0));
        }
        //默认错误不停止
        request.setErrorStop(Optional.ofNullable(request.getErrorStop()).orElse(false));
        //默认错误不回滚
        request.setErrorRollback(Optional.ofNullable(request.getErrorRollback()).orElse(false));
    }

    private void rollbackImport(Connection connection, TableImportRequest request) {
        if (connection == null || !Boolean.TRUE.equals(request.getErrorRollback())) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            log.error("导入失败后回滚事务失败", rollbackException);
        }
    }

    public List<TableColumn> getTableKeyDataList(TableQueryParam param){
        MetaData metaSchema = Chat2DBContext.getMetaData();
        return metaSchema.columns(Chat2DBContext.getConnection(), param.getDatabaseName(), param.getSchemaName(), param.getTableName());
    }

    /**
     * Excel数据导入时，对更新数据进行删除再插入
     */
    @Transactional
    public boolean excelDataSave(Table table,String insertSql){
        MetaData metaSchema = Chat2DBContext.getMetaData();
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(" SET  IDENTITY_INSERT  \"").append(table.getSchemaName()).append("\".\"").append(table.getName()).append("\" ON ");
        metaSchema.executeSQL(Chat2DBContext.getConnection(), stringBuffer.toString());
        Boolean flag = metaSchema.executeSQL(Chat2DBContext.getConnection(), insertSql);
        StringBuffer BstringBuffer = new StringBuffer();
        BstringBuffer.append(" SET  IDENTITY_INSERT  \"").append(table.getSchemaName()).append("\".\"").append(table.getName()).append("\" OFF ");
        metaSchema.executeSQL(Chat2DBContext.getConnection(), BstringBuffer.toString());
        return  flag;
    }


}
