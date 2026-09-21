package com.jd.biz.domain.core.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.controller.rdb.enums.ResultType;
import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.domain.api.service.TableUserService;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.utils.StringUtils;
import com.jd.spi.model.*;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import com.jd.spi.util.ExceptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 表用户管理
 */
@Service
@Slf4j
public class TableUserServiceImpl implements TableUserService {


    @Override
    public ListResult<Table> tableUsers(String databaseName, Integer requestType) {
        try {
            List<Table> tableUsers = Chat2DBContext.getMetaData().tableUsers(Chat2DBContext.getConnection(), databaseName);
            List<Table> tableVOS = new ArrayList<>();
            if (ResultType.ONE.getType().equals(requestType)) {
                List<String> strings = tableUsers.stream().map(Table::getName).collect(Collectors.toList());
                strings.forEach(s -> {
                    Table table = new Table();
                    table.setName(s);
                    tableVOS.add(table);
                });
                return ListResult.of(tableVOS);
            }
            return ListResult.of(tableUsers);
        } catch (Exception e) {
            String errorMessage = e.getLocalizedMessage();
            if (e.getMessage().contains(":")) {
                errorMessage = e.getMessage().split(":")[1].trim();
            }
            throw new BusinessException(errorMessage);
        }
    }

    @Override
    public Table tableUser(String databaseName, String username) {
        Table table = Chat2DBContext.getMetaData().tableUser(Chat2DBContext.getConnection(), databaseName, username);
        if (Objects.nonNull(table) && Objects.nonNull(table.getTableDetails())) {
            table.setQuerySql(Chat2DBContext.getSqlBuilder().createUser(table.getTableDetails().getName()));
        }
        table.setTableUserRoles(getUserRole(username));
        return table;
    }

    public List<TableUserRole> getUserRole(String username) {
        return Chat2DBContext.getMetaData().getUserRule(Chat2DBContext.getConnection(), username);
    }

    @Override
    public DataResult<Boolean> lockUser(String lockName, Boolean isLock) {
        try {
            String sql = Chat2DBContext.getSqlBuilder().lockOrUnlock(lockName, isLock);
            ExecuteResult execute = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql);
            if (execute.getSuccess()) {
                return DataResult.of(true);
            }
        } catch (SQLException e) {
            throw new BusinessException(ExceptionUtils.getMessage(e));
        }
        return DataResult.of(false);
    }

    @Override
    public DataResult<ExecuteResult> managePassWord(String name, String newPassWord) {
        try {
            String sql = Chat2DBContext.getSqlBuilder().manageUserPassword(name, newPassWord);
            ExecuteResult execute = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql);
            return DataResult.of(execute);
        } catch (SQLException e) {
            throw new BusinessException("用户密码修改失败");
        }
    }

    @Override
    public void modify(TableSpace tableSpace) {
        if (StringUtils.isNotBlank(tableSpace.getDefaultTableSpace())) {
            StringBuilder sb = new StringBuilder();
            sb.append("alter user ");
            sb.append("\"").append(tableSpace.getUserName()).append("\"");
            sb.append(" default tablespace ");
            sb.append("\"").append(tableSpace.getDefaultTableSpace()).append("\"");
            Boolean aBoolean = Chat2DBContext.getMetaData().executeSQL(Chat2DBContext.getConnection(), sb.toString());
            if (!aBoolean) {
                throw new BusinessException("当前用户无权限修改");
            }
        }
        if (StringUtils.isNotBlank(tableSpace.getTempTableSpace())) {
            StringBuilder sb = new StringBuilder();
            sb.append("alter user ");
            sb.append("\"").append(tableSpace.getUserName()).append("\"");
            sb.append(" TEMPORARY tablespace ");
            sb.append("\"").append(tableSpace.getTempTableSpace()).append("\"");
            Boolean aBoolean = Chat2DBContext.getMetaData().executeSQL(Chat2DBContext.getConnection(), sb.toString());
            if (!aBoolean) {
                throw new BusinessException("当前用户无权限修改");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult addOrDelUserRole(String userName, List<TableRoleData> newRoles, List<TableRoleData> oldRoles) {
        boolean ref = false;
        List<TableRoleData> collect = oldRoles.stream().filter(TableRoleData::getIsGranted).collect(Collectors.toList());
        try {
            String dbType = Chat2DBContext.getConnectInfo().getDbType();
            if (StringUtils.isEmpty(dbType)) {
                throw new BusinessException("数据源类型未知");
            }
            List<String> delSqlList = new ArrayList<>();
            if (CollUtil.isNotEmpty(collect)) {
                for (TableRoleData tableRoleData : collect) {
                    String delSql = Chat2DBContext.getSqlBuilder().delUserRole(userName, tableRoleData.getRole());
                    delSqlList.add(delSql);
                }
            }
            if (CollUtil.isNotEmpty(delSqlList)) {
                for (String s : delSqlList) {
                    SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), s);
                }
                ref = true;
            }
            List<TableRoleData> roleData = newRoles.stream().filter(TableRoleData::getIsGranted).collect(Collectors.toList());
            List<String> list = new ArrayList<>();
            for (TableRoleData newRole : roleData) {
                String adminRole ="";
                if (newRole.getIsAdmin()) {
                    adminRole = newRole.getRole();
                }
                String addSql = Chat2DBContext.getSqlBuilder().addUserRole(userName, newRole.getRole(), adminRole);
                list.add(addSql);
            }
            if (CollUtil.isNotEmpty(list)) {
                for (String s : list) {
                    SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), s);
                }
            }
        } catch (SQLException e) {
            log.warn("设置角色失败,{}", e.getMessage());
            String[] split = e.getMessage().split(":\n");
            List<String> list = Arrays.asList(split);
            String message = "";
            if (CollUtil.isEmpty(list) || list.size() < 2) {
                message = "设置角色失败";
            } else {
                message = list.get(1);
            }
            if (ref) {
                List<String> oldList = new ArrayList<>();
                for (TableRoleData newRole : collect) {
                    String adminRole ="";
                    if (newRole.getIsAdmin()) {
                        adminRole = newRole.getRole();
                    }
                    String addSql = Chat2DBContext.getSqlBuilder().addUserRole(userName, newRole.getRole(), adminRole);
                    oldList.add(addSql);
                }
                if (CollUtil.isNotEmpty(oldList)) {
                    for (String s : oldList) {
                        try {
                            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), s);
                        } catch (SQLException ex) {
                            log.warn("异常信息,e:{}", ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                }
            }
            throw new BusinessException(message);
        }
        return AjaxResult.success();
    }


    public void addOrDelUserRole(String userName, List<String> adds) throws SQLException {
        try {
            List<String> sql = new ArrayList<>();
            if (CollUtil.isNotEmpty(adds)) {
                for (String s : adds) {
                    String addSql = Chat2DBContext.getSqlBuilder().addUserRole(userName, s, "");
                    sql.add(addSql);
                }
            }
            for (String s : sql) {
                SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), s);
            }
        } catch (SQLException e) {
            throw new BusinessException("设置角色失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult createUser(String userName, String newPassWord, String defaultTableSpace, String temptableSpace, List<TableRoleData> roles) {
        try {
            String createSql = Chat2DBContext.getSqlBuilder().createUser(userName, newPassWord, defaultTableSpace, temptableSpace);
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), createSql);
            List<String> collect = roles.stream().filter(TableRoleData::getIsGranted).map(TableRoleData::getRole).collect(Collectors.toList());
            addOrDelUserRole(userName, collect);
            return AjaxResult.success();
        } catch (SQLException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    @Override
    public List<TableRoleData> allRole(TableBriefQueryRequest request) {
        List<TableRoleData> tableRoleData = new ArrayList<>();
        if (org.apache.commons.lang3.StringUtils.isNotBlank(request.getUserName())) {
            tableRoleData = Chat2DBContext.getMetaData().findRole(Chat2DBContext.getConnection(), request.getUserName());
        }
        List<String> list = Chat2DBContext.getMetaData().allRole(Chat2DBContext.getConnection());
        if (CollUtil.isEmpty(tableRoleData)) {
            tableRoleData = new ArrayList<>();
            for (String s : list) {
                TableRoleData tableRole = new TableRoleData();
                tableRole.setRole(s);
                tableRole.setIsGranted(false);
//                if(StringUtils.equals("PUBLIC",tableRole.getRole())||StringUtils.equals("SOI",tableRole.getRole())||StringUtils.equals("VTI",tableRole.getRole())){
//                    tableRole.setIsGranted(true);
//                }
                tableRole.setIsDefault(false);
                tableRole.setIsAdmin(false);
                tableRoleData.add(tableRole);
            }
        } else {
            List<String> strings = tableRoleData.stream().map(TableRoleData::getRole).collect(Collectors.toList());
            for (String s : list) {
                if (strings.contains(s)){
                    continue;
                }
                TableRoleData tableRole = new TableRoleData();
                tableRole.setRole(s);
                tableRole.setIsGranted(false);
//                if(StringUtils.equals("PUBLIC",tableRole.getRole())||StringUtils.equals("SOI",tableRole.getRole())||StringUtils.equals("VTI",tableRole.getRole())){
//                    tableRole.setIsGranted(true);
//                }
                tableRole.setIsDefault(false);
                tableRole.setIsAdmin(false);
                tableRoleData.add(tableRole);
            }
        }
        return tableRoleData;
    }

    @Override
    public List<TableObjectRoleData> allObjectRole(TableBriefQueryRequest request) {
        String dbType = Chat2DBContext.getDBConfig().getDbType();
        String[] array = null;
        if (DBTypeEnum.DM.name().equals(dbType)) {
            array = new String[]{"ALL", "SELECT", "INSERT", "DELETE", "UPDATE", "REFERENCES", "SELECT FOR DUMP", "ALTER", "INDEX"};
        } else {
            array = new String[]{"ALL", "SELECT", "INSERT", "DELETE", "UPDATE", "REFERENCES", "ALTER", "INDEX"};
        }
        Set<TableObjectRoleData> set = new HashSet<>();
        if (StrUtil.isNotBlank(request.getSchemaName()) && StrUtil.isNotBlank(request.getTableName()) && StrUtil.isNotBlank(request.getUserName())) {
            // 查询对象权限
            List<TableObjectRoleData> list = Chat2DBContext.getMetaData().allObjectRole(Chat2DBContext.getConnection(), request.getUserName(), request.getSchemaName(), request.getTableName());
            if (CollUtil.isEmpty(list)) {
                for (String s : array) {
                    TableObjectRoleData tableRoleData = new TableObjectRoleData();
                    tableRoleData.setDesc(s);
                    set.add(tableRoleData);
                }
            } else {
                set.addAll(list);
                if (list.size() == array.length-1) {
                    TableObjectRoleData tableRoleData = new TableObjectRoleData();
                    tableRoleData.setDesc(array[0]);
                    tableRoleData.setRule(Boolean.TRUE);
                    List<Boolean> booleans = list.stream().map(TableObjectRoleData::getToRule).collect(Collectors.toList());
                    tableRoleData.setToRule(booleans.size() == array.length-1);
                    set.add(tableRoleData);
                } else {
                    List<String> collect = list.stream().map(TableObjectRoleData::getDesc).collect(Collectors.toList());
                    for (String s : array) {
                        if (!collect.contains(s)) {
                            TableObjectRoleData tableRoleData = new TableObjectRoleData();
                            tableRoleData.setDesc(s);
                            set.add(tableRoleData);
                        }
                    }
                }
            }
        } else {
            for (String s : array) {
                TableObjectRoleData tableRoleData = new TableObjectRoleData();
                tableRoleData.setDesc(s);
                set.add(tableRoleData);
            }
        }
        return set.stream().sorted(Comparator.comparing(TableObjectRoleData::getDesc)).collect(Collectors.toList());
    }

    @Override
    public Boolean addObjectRole(TableBriefQueryRequest request) {
        boolean ref = false;
        try {
            if (CollUtil.isEmpty(request.getNewObjectRoleData()) || CollUtil.isEmpty(request.getOldObjectRoleData())) {
                throw new BusinessException("对象权限入参缺失");
            }
            if (StrUtil.isBlank(request.getSchemaName()) || StrUtil.isBlank(request.getTableName()) || StrUtil.isBlank(request.getUserName())) {
                throw new BusinessException("入参缺失");
            }
            if (request.getSchemaName().equals(request.getUserName())) {
                throw new BusinessException("模式名不能与用户名相同");
            }
            List<String> delSql = Chat2DBContext.getSqlBuilder().delObjectRole(request.getSchemaName(), request.getUserName(), request.getTableName(), request.getOldObjectRoleData());
            if (CollUtil.isNotEmpty(delSql)) {
                for (String s : delSql) {
                    SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), s);
                }
            }
            ref = true;
            List<TableObjectRoleData> newObjectRoleData = request.getNewObjectRoleData();
            List<String> addSql = Chat2DBContext.getSqlBuilder().addObjectRole(request.getSchemaName(), request.getUserName(), request.getTableName(), newObjectRoleData);
            if (CollUtil.isNotEmpty(addSql)) {
                for (String s : addSql) {
                    SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), s);
                }
            }
        } catch (BusinessException | SQLException e) {
            log.warn("对象权限变更失败,e:{}", e.getMessage());
            try {
                if (ref) {
                    List<String> addSql = Chat2DBContext.getSqlBuilder().addObjectRole(request.getSchemaName(), request.getUserName(), request.getTableName(), request.getOldObjectRoleData());
                    for (String s : addSql) {
                        SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), s);
                    }
                }
            } catch (SQLException ex) {
                log.warn("对象权限变更失败,入参:{}", JSON.toJSONString(request));
            }
            throw new BusinessException( e.getMessage());
        }
        return true;
    }
}
