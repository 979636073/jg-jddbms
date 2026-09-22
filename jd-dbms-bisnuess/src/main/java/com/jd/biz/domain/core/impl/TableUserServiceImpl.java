package com.jd.biz.domain.core.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.controller.rdb.enums.ResultType;
import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.domain.api.service.TableUserService;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.utils.StringUtils;
import com.jd.spi.SqlBuilder;
import com.jd.spi.model.*;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import com.jd.spi.util.ExceptionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
            throw new BusinessException("user.query.failed", new Object[]{ExceptionUtils.getMessage(e)});
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
            throw new BusinessException("user.lock.failed", new Object[]{ExceptionUtils.getMessage(e)});
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
            throw new BusinessException("user.password.change.failed", new Object[]{ExceptionUtils.getMessage(e)});
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
                throw new BusinessException("user.modify.failed");
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
                throw new BusinessException("user.modify.failed");
            }
        }
    }

    @Override
    public AjaxResult addOrDelUserRole(String userName, List<TableRoleData> newRoles, List<TableRoleData> oldRoles) {
        if (StrUtil.isBlank(userName) || newRoles == null || oldRoles == null) {
            throw new BusinessException("user.role.change.paramRequired");
        }
        executeChanges(buildUserRoleChanges(Chat2DBContext.getSqlBuilder(), userName, newRoles, oldRoles),
                "user.role.change.failed");
        return AjaxResult.success();
    }

    @Override
    public AjaxResult createUser(String userName, String newPassWord, String defaultTableSpace, String temptableSpace, List<TableRoleData> roles) {
        if (StrUtil.isBlank(userName) || StrUtil.isBlank(newPassWord)) {
            throw new BusinessException("user.namePassword.required");
        }
        boolean created = false;
        try {
            String createSql = Chat2DBContext.getSqlBuilder().createUser(userName, newPassWord, defaultTableSpace, temptableSpace);
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), createSql);
            created = true;
            List<TableRoleData> requestedRoles = roles == null ? Collections.emptyList() : roles;
            executeChanges(buildUserRoleChanges(Chat2DBContext.getSqlBuilder(), userName,
                    requestedRoles, Collections.emptyList()), "user.role.change.failed");
            return AjaxResult.success();
        } catch (SQLException | RuntimeException e) {
            if (created) {
                try {
                    SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(),
                            Chat2DBContext.getSqlBuilder().dropUser(userName));
                } catch (SQLException cleanupException) {
                    log.warn("创建用户失败后清理用户失败,userName:{},error:{}", userName, cleanupException.getMessage());
                }
            }
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            String message = ExceptionUtils.getMessage((SQLException) e);
            throw new BusinessException("user.create.failed", new Object[]{message});
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
        if (request.getNewObjectRoleData() == null || request.getOldObjectRoleData() == null) {
            throw new BusinessException("user.objectRole.paramRequired");
        }
        if (StrUtil.isBlank(request.getSchemaName()) || StrUtil.isBlank(request.getTableName()) || StrUtil.isBlank(request.getUserName())) {
            throw new BusinessException("user.objectRole.paramRequired");
        }
        if (request.getSchemaName().equals(request.getUserName())) {
            throw new BusinessException("user.objectRole.sameSchema");
        }
        executeChanges(buildObjectRoleChanges(Chat2DBContext.getSqlBuilder(), request), "user.objectRole.change.failed");
        return true;
    }

    List<SqlChange> buildUserRoleChanges(SqlBuilder<?> sqlBuilder, String userName,
                                         List<TableRoleData> newRoles, List<TableRoleData> oldRoles) {
        Map<String, TableRoleData> oldGranted = grantedRoleMap(oldRoles);
        Map<String, TableRoleData> newGranted = grantedRoleMap(newRoles);
        Set<String> roleNames = new LinkedHashSet<>(oldGranted.keySet());
        roleNames.addAll(newGranted.keySet());
        List<SqlChange> revokes = new ArrayList<>();
        List<SqlChange> grants = new ArrayList<>();
        for (String roleName : roleNames) {
            TableRoleData oldRole = oldGranted.get(roleName);
            TableRoleData newRole = newGranted.get(roleName);
            boolean adminChanged = oldRole != null && newRole != null
                    && !Objects.equals(Boolean.TRUE.equals(oldRole.getIsAdmin()), Boolean.TRUE.equals(newRole.getIsAdmin()));
            if (oldRole != null && (newRole == null || adminChanged)) {
                revokes.add(new SqlChange(
                        sqlBuilder.delUserRole(userName, roleName),
                        sqlBuilder.addUserRole(userName, roleName,
                                Boolean.TRUE.equals(oldRole.getIsAdmin()) ? roleName : "")));
            }
            if (newRole != null && (oldRole == null || adminChanged)) {
                grants.add(new SqlChange(
                        sqlBuilder.addUserRole(userName, roleName,
                                Boolean.TRUE.equals(newRole.getIsAdmin()) ? roleName : ""),
                        sqlBuilder.delUserRole(userName, roleName)));
            }
        }
        revokes.addAll(grants);
        return revokes;
    }

    List<SqlChange> buildObjectRoleChanges(SqlBuilder<?> sqlBuilder, TableBriefQueryRequest request) {
        Map<String, TableObjectRoleData> oldGranted = grantedObjectRoleMap(request.getOldObjectRoleData());
        Map<String, TableObjectRoleData> newGranted = grantedObjectRoleMap(request.getNewObjectRoleData());
        Set<String> privilegeNames = new LinkedHashSet<>(oldGranted.keySet());
        privilegeNames.addAll(newGranted.keySet());
        List<SqlChange> revokes = new ArrayList<>();
        List<SqlChange> grants = new ArrayList<>();
        for (String privilegeName : privilegeNames) {
            TableObjectRoleData oldRole = oldGranted.get(privilegeName);
            TableObjectRoleData newRole = newGranted.get(privilegeName);
            boolean grantOptionChanged = oldRole != null && newRole != null
                    && !Objects.equals(Boolean.TRUE.equals(oldRole.getToRule()), Boolean.TRUE.equals(newRole.getToRule()));
            if (oldRole != null && (newRole == null || grantOptionChanged)) {
                revokes.add(new SqlChange(
                        singleSql(sqlBuilder.delObjectRole(request.getSchemaName(), request.getUserName(),
                                request.getTableName(), Collections.singletonList(oldRole))),
                        singleSql(sqlBuilder.addObjectRole(request.getSchemaName(), request.getUserName(),
                                request.getTableName(), Collections.singletonList(oldRole)))));
            }
            if (newRole != null && (oldRole == null || grantOptionChanged)) {
                grants.add(new SqlChange(
                        singleSql(sqlBuilder.addObjectRole(request.getSchemaName(), request.getUserName(),
                                request.getTableName(), Collections.singletonList(newRole))),
                        singleSql(sqlBuilder.delObjectRole(request.getSchemaName(), request.getUserName(),
                                request.getTableName(), Collections.singletonList(newRole)))));
            }
        }
        revokes.addAll(grants);
        return revokes;
    }

    private Map<String, TableRoleData> grantedRoleMap(List<TableRoleData> roles) {
        Map<String, TableRoleData> result = new LinkedHashMap<>();
        if (roles == null) {
            return result;
        }
        for (TableRoleData role : roles) {
            if (role != null && Boolean.TRUE.equals(role.getIsGranted())) {
                if (StrUtil.isBlank(role.getRole())) {
                    throw new BusinessException("user.role.name.required");
                }
                result.put(role.getRole(), role);
            }
        }
        return result;
    }

    private Map<String, TableObjectRoleData> grantedObjectRoleMap(List<TableObjectRoleData> roles) {
        Map<String, TableObjectRoleData> result = new LinkedHashMap<>();
        if (roles == null) {
            return result;
        }
        for (TableObjectRoleData role : roles) {
            if (role != null && Boolean.TRUE.equals(role.getRule())) {
                if (StrUtil.isBlank(role.getDesc())) {
                    throw new BusinessException("user.objectRole.name.required");
                }
                result.put(role.getDesc(), role);
            }
        }
        return result;
    }

    private String singleSql(List<String> sqlList) {
        if (CollUtil.isEmpty(sqlList) || StrUtil.isBlank(sqlList.get(0))) {
            throw new BusinessException("user.objectRole.unsupported");
        }
        return sqlList.get(0);
    }

    private void executeChanges(List<SqlChange> changes, String errorCode) {
        List<SqlChange> completed = new ArrayList<>();
        try {
            for (SqlChange change : changes) {
                SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), change.sql);
                completed.add(change);
            }
        } catch (SQLException e) {
            for (int i = completed.size() - 1; i >= 0; i--) {
                try {
                    SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), completed.get(i).rollbackSql);
                } catch (SQLException rollbackException) {
                    log.warn("权限变更补偿失败,sql:{},error:{}", completed.get(i).rollbackSql,
                            rollbackException.getMessage());
                }
            }
            throw new BusinessException(errorCode, new Object[]{ExceptionUtils.getMessage(e)});
        }
    }

    static class SqlChange {
        private final String sql;
        private final String rollbackSql;

        SqlChange(String sql, String rollbackSql) {
            this.sql = sql;
            this.rollbackSql = rollbackSql;
        }

        String getSql() {
            return sql;
        }

        String getRollbackSql() {
            return rollbackSql;
        }
    }
}
