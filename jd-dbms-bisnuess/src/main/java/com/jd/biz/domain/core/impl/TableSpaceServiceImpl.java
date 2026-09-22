package com.jd.biz.domain.core.impl;

import cn.hutool.core.util.StrUtil;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.controller.rdb.enums.ResultType;
import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.controller.rdb.request.TableSpaceCreateRequest;
import com.jd.biz.controller.rdb.request.TableSpaceUpdateRequest;
import com.jd.biz.domain.api.service.TableSpaceService;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.jdbc.DefaultValueHandler;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableDetails;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 表空间管理
 */
@Service
@Slf4j
public class TableSpaceServiceImpl implements TableSpaceService {


    @Override
    public ListResult<Table> list(String databaseName, Integer requestType) {
        try {
            List<Table> tableSpaces = Chat2DBContext.getMetaData().tableSpaces(Chat2DBContext.getConnection(), databaseName);
            if (ResultType.ONE.getType().equals(requestType)) {
                List<String> strings = tableSpaces.stream().map(Table::getName).collect(Collectors.toList());
                List<Table> list = new ArrayList<>();
                strings.forEach(s -> {
                    Table tableSpace = new Table();
                    tableSpace.setName(s);
                    list.add(tableSpace);
                });
                return ListResult.of(list);
            }
            return ListResult.of(tableSpaces);
        } catch (Exception e) {
            String errorMessage = e.getLocalizedMessage();
            if (e.getMessage().contains(":")) {
                errorMessage = e.getMessage().split(":")[1].trim();
            }
            throw new BusinessException(errorMessage);
        }
    }


    @Override
    public Table query(String path, String dbType) {
        Table table = Chat2DBContext.getMetaData().tableSpace(Chat2DBContext.getConnection(), path);
        if (Objects.nonNull(table) && Objects.nonNull(table.getTableDetails())) {
            if (DBTypeEnum.DM.name().equals(dbType)) {
                table.setQuerySql(Chat2DBContext.getSqlBuilder().createSpace(table.getTableDetails().getPath(), table.getTableDetails().getTableSpace(), table.getTableDetails().getTotalSize(), table.getTableDetails().getExpandUpperLimit(), StringUtils.isNotBlank(table.getTableDetails().getAutoScaling()) ? table.getTableDetails().getAutoScaling() : null));
            } else {
                table.setQuerySql(Chat2DBContext.getSqlBuilder().createSpace(table.getTableDetails().getPath(), table.getTableDetails().getTableSpace(), table.getTableDetails().getTotalSize() + "M", table.getTableDetails().getExpandUpperLimit() + "M", StringUtils.isNotBlank(table.getTableDetails().getAutoScaling()) ? table.getTableDetails().getAutoScaling() + "M" : null));
            }
        }
        return table;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActionResult createTablespace(TableSpaceCreateRequest request) {
        if (request == null || StringUtils.isBlank(request.getTableSpace())
                || StringUtils.isBlank(request.getPath()) || StrUtil.isEmpty(request.getTotalSize())) {
            throw new BusinessException("参数缺失");
        } else {
            if (StrUtil.isEmpty(request.getSizeUnit())) {
                throw new BusinessException("单位缺失");
            }
        }
        String dbType = getDbType();
        if (StringUtils.isBlank(dbType)) {
            throw new BusinessException("参数缺失");
        }
        if (DBTypeEnum.ORACLE.name().equals(dbType)) {
            request.setTotalSize(request.getTotalSize() + request.getSizeUnit().charAt(0));
            if (StringUtils.isNotBlank(request.getAutoSize())) {
                if (StringUtils.isBlank(request.getAutoSizeUnit())) {
                    throw new BusinessException("参数缺失");
                }
                request.setAutoSize(request.getAutoSize() + request.getAutoSizeUnit().charAt(0));
            }
            if (StringUtils.isNotBlank(request.getExpandUpperLimit())) {
                if (StringUtils.isBlank(request.getMaxSizeUnit())) {
                    throw new BusinessException("参数缺失");
                }
                request.setExpandUpperLimit(request.getExpandUpperLimit() + request.getMaxSizeUnit().charAt(0));
            }
        } else if (DBTypeEnum.DM.name().equals(dbType)) {
            request.setTotalSize(converterUnit(request.getSizeUnit(), request.getTotalSize()));
            if (StringUtils.isNotBlank(request.getAutoSize())) {
                if (StringUtils.isBlank(request.getAutoSizeUnit())) {
                    throw new BusinessException("参数缺失");
                }
                request.setAutoSize(converterUnit(request.getAutoSizeUnit(), request.getAutoSize()));
            }
            if (StringUtils.isNotBlank(request.getExpandUpperLimit())) {
                if (StringUtils.isBlank(request.getMaxSizeUnit())) {
                    throw new BusinessException("参数缺失");
                }
                request.setExpandUpperLimit(converterUnit(request.getMaxSizeUnit(), request.getExpandUpperLimit()));
            }
        }
        String sql = Chat2DBContext.getSqlBuilder().createSpace(request.getPath(), request.getTableSpace(), request.getTotalSize(), StringUtils.isNotBlank(request.getExpandUpperLimit()) ? request.getExpandUpperLimit() : null, StringUtils.isNotBlank(request.getAutoSize()) ? request.getAutoSize() : null);
        try {
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql);
        } catch (SQLException e) {
            throw new BusinessException("创建或修改表空间异常:" + e.getMessage());
        }
        return ActionResult.isSuccess();
    }

    private String converterUnit(String unit, String size) {
        if ("KB".equals(unit)) {
            size = new BigDecimal(size).divide(new BigDecimal("1024").setScale(2, RoundingMode.HALF_UP)).toString();
        }
        if ("GB".equals(unit)) {
            size = new BigDecimal(size).multiply(new BigDecimal("1024")).toString();
        }
        return size;
    }

    @Override
    public void dropTablespace(TableSpaceCreateRequest request) {
        try{
            if (request.getTableSpaceNames().size() > 0) {
                List<String> tableSpaceNames = request.getTableSpaceNames();
                tableSpaceNames.forEach(tableSpace -> {
                    Chat2DBContext.getMetaData().dropTablespace(Chat2DBContext.getConnection(), tableSpace);
                });
            }
        }catch (Exception e){
            throw new RuntimeException("表空间删除出错，错误信息：" + e.getMessage());
        }
    }

    @Override
    public List<TableDetails> listPath(TableBriefQueryRequest request) {
        return Chat2DBContext.getMetaData().tableSpacesFile(Chat2DBContext.getConnection(), request.getPageNo(), request.getPageSize());
    }

    @Override
    public List<Map<String, String>> tempList(Integer type) {
        List<Map<String, String>> valueList = new ArrayList<>();
        List<String> list = Chat2DBContext.getMetaData().tempList(Chat2DBContext.getConnection(), type);
        HashSet<String> strings = new HashSet<>(list);
        int i = 0;
        for (String string : strings) {
            Map<String, String> map = new HashMap<>();
            map.put("name", string);
            i++;
            map.put("id", i+"");
            valueList.add(map);
        }
        return valueList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActionResult updateTablespaceSql(TableSpaceUpdateRequest request) {
        if (Objects.isNull(request.getNewTableSpace()) || Objects.isNull(request.getOldTableSpace())) {
            throw new BusinessException("参数缺失");
        }
        String dbType = getDbType();
        if (StringUtils.isBlank(dbType)) {
            throw new BusinessException("参数缺失");
        }
        List<String> sqls = new ArrayList<>();
        TableSpaceCreateRequest newTableSpace = request.getNewTableSpace();
        TableSpaceCreateRequest oldTableSpace = request.getOldTableSpace();
        validateTablespace(newTableSpace);
        if (StringUtils.isNotBlank(newTableSpace.getAutoSize())) {
            if (StringUtils.isBlank(newTableSpace.getAutoSizeUnit())) {
                throw new BusinessException("自增单位缺失");
            }
            if (!StringUtils.equals(newTableSpace.getAutoSize(), oldTableSpace.getAutoSize()) ||
                    !StringUtils.equals(newTableSpace.getAutoSizeUnit(), oldTableSpace.getAutoSizeUnit()) ||
                    (StrUtil.isNotBlank(newTableSpace.getExpandUpperLimit())
                            && !StringUtils.equals(newTableSpace.getExpandUpperLimit(), oldTableSpace.getExpandUpperLimit()))) {
                String maxSize = "";
                if (DBTypeEnum.DM.name().equals(dbType)) {
                    newTableSpace.setAutoSize(converterUnit(newTableSpace.getAutoSizeUnit(), newTableSpace.getAutoSize()));
                    if (StringUtils.isNotBlank(newTableSpace.getExpandUpperLimit())) {
                        if (StringUtils.isBlank(newTableSpace.getMaxSizeUnit())) {
                            throw new BusinessException("最大值单位缺失");
                        }
                        maxSize = converterUnit(newTableSpace.getMaxSizeUnit(), newTableSpace.getExpandUpperLimit());
                    }
                } else if (DBTypeEnum.ORACLE.name().equals(dbType)) {
                    newTableSpace.setAutoSize(newTableSpace.getAutoSize() + newTableSpace.getAutoSizeUnit().charAt(0));
                    if (StringUtils.isNotBlank(newTableSpace.getExpandUpperLimit())) {
                        if (StringUtils.isBlank(newTableSpace.getMaxSizeUnit())) {
                            throw new BusinessException("最大值单位缺失");
                        }
                        maxSize = newTableSpace.getExpandUpperLimit() + newTableSpace.getMaxSizeUnit().charAt(0);
                    }
                }
                String sql = Chat2DBContext.getSqlBuilder().updateAutoSpace(Chat2DBContext.getConnection(), newTableSpace.getPath(), newTableSpace.getAutoSize(), maxSize, newTableSpace.getTableSpace());
                sqls.add(sql);
            }
        } else {
            String sql = Chat2DBContext.getSqlBuilder().updateOFFAutoSpace(Chat2DBContext.getConnection(), newTableSpace.getPath(), newTableSpace.getTableSpace());
            sqls.add(sql);
        }
        if (StringUtils.isNotBlank(newTableSpace.getTotalSize())) {
            if (!StringUtils.equals(newTableSpace.getTotalSize(), oldTableSpace.getTotalSize())
                    || !StringUtils.equals(newTableSpace.getSizeUnit(), oldTableSpace.getSizeUnit())) {
                if (DBTypeEnum.DM.name().equals(dbType)) {
                    String size = converterUnit(newTableSpace.getSizeUnit(), newTableSpace.getTotalSize());
                    newTableSpace.setTotalSize(size);
                } else if (DBTypeEnum.ORACLE.name().equals(dbType)) {
                    newTableSpace.setTotalSize(newTableSpace.getTotalSize() + newTableSpace.getSizeUnit().charAt(0));
                }
                String sql = Chat2DBContext.getSqlBuilder().updateDefaultSpace(Chat2DBContext.getConnection(), newTableSpace.getTableSpace(), newTableSpace.getPath(), newTableSpace.getTotalSize());
                sqls.add(sql);
            }
        }
        try {
            if (!StringUtils.equals(newTableSpace.getTableSpace(), oldTableSpace.getTableSpace())) {
                executeTablespaceSql(new StringBuilder("ALTER TABLESPACE \"")
                        .append(oldTableSpace.getTableSpace()).append("\" rename to ")
                        .append(newTableSpace.getTableSpace()).append(";").toString());
            }
            if (!StringUtils.equals(newTableSpace.getPath(), oldTableSpace.getPath())) {
                executePathChange(newTableSpace.getTableSpace(), oldTableSpace.getPath(), newTableSpace.getPath());
            }
            for (String sql : sqls) {
                executeTablespaceSql(sql);
            }
        } catch (SQLException e) {
            throw new BusinessException("表空间修改SQL执行失败:" + e.getMessage());
        }
        return ActionResult.isSuccess();
    }

    private void validateTablespace(TableSpaceCreateRequest tableSpace) {
        if (StringUtils.isBlank(tableSpace.getTableSpace()) || StringUtils.isBlank(tableSpace.getPath())
                || StringUtils.isBlank(tableSpace.getTotalSize()) || StringUtils.isBlank(tableSpace.getSizeUnit())) {
            throw new BusinessException("参数缺失");
        }
    }

    private void executePathChange(String tableSpace, String oldPath, String newPath) throws SQLException {
        String prefix = "ALTER TABLESPACE \"" + tableSpace + "\" ";
        executeTablespaceSql(prefix + "offline;");
        SQLException failure = null;
        try {
            executeTablespaceSql(prefix + "rename datafile '" + oldPath + "' to '" + newPath + "';");
        } catch (SQLException e) {
            failure = e;
        }
        try {
            executeTablespaceSql(prefix + "online;");
        } catch (SQLException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    void executeTablespaceSql(String sql) throws SQLException {
        SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql, new DefaultValueHandler());
    }

    private String getDbType() {
        String dbType = "";
        if (Objects.isNull(Chat2DBContext.getConnectInfo())) {
            throw new BusinessException("参数缺失");
        }
        if (StringUtils.isNotBlank(Chat2DBContext.getConnectInfo().getDbType())) {
            dbType = Chat2DBContext.getConnectInfo().getDbType();
        }
        if (StringUtils.isBlank(dbType)) {
            throw new BusinessException("参数缺失");
        }
        return dbType;
    }
}
