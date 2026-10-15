package com.jd.biz.domain.core.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jd.common.enums.DBTypeEnum;
import com.jd.biz.controller.rdb.enums.ResultType;
import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.controller.rdb.vo.UserOperationResult;
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
import java.util.Locale;
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
            throw new BusinessException("user.query.failed", new Object[]{ExceptionUtils.getMessage(e)}, e);
        }
    }

    @Override
    public Table tableUser(String databaseName, String username) {
        Table table = Chat2DBContext.getMetaData().tableUser(Chat2DBContext.getConnection(), databaseName, username);
        if (Objects.nonNull(table) && Objects.nonNull(table.getTableDetails())) {
            table.setQuerySql(userScript(table.getTableDetails().getName()));
        }
        table.setTableUserRoles(getUserRole(username));
        return table;
    }

    private String userScript(String userName) {
        SqlBuilder<?> builder = Chat2DBContext.getSqlBuilder();
        return buildUserScript(builder, userName, getUserRole(userName), Chat2DBContext.getMetaData()
                .userObjectRoles(Chat2DBContext.getConnection(), userName));
    }

    String buildUserScript(SqlBuilder<?> builder, String userName, List<TableUserRole> roles,
                           List<TableObjectRoleData> privileges) {
        StringBuilder script = new StringBuilder(builder.createUser(userName)).append(';');
        if (roles != null) {
            for (TableUserRole role : roles) {
                if (StrUtil.isNotBlank(role.getRole())) {
                    boolean admin = "YES".equalsIgnoreCase(role.getIsAdmin()) || "是".equals(role.getIsAdmin());
                    script.append('\n').append(builder.addUserRole(userName, role.getRole(), admin ? role.getRole() : "")).append(';');
                }
            }
        }
        if (privileges != null) {
            for (TableObjectRoleData privilege : privileges) {
                if (StrUtil.isBlank(privilege.getOwner()) || StrUtil.isBlank(privilege.getTableName())
                        || StrUtil.isBlank(privilege.getDesc())) {
                    continue;
                }
                List<String> statements = builder.addObjectRole(privilege.getOwner(), userName,
                        privilege.getTableName(), Collections.singletonList(privilege));
                for (String statement : statements) {
                    script.append('\n').append(statement).append(';');
                }
            }
        }
        return script.toString();
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
        return DataResult.error("user.lock.failed", "数据库未完成锁定状态变更");
    }

    @Override
    public DataResult<ExecuteResult> managePassWord(String name, String newPassWord) {
        try {
            String sql = Chat2DBContext.getSqlBuilder().manageUserPassword(name, newPassWord);
            ExecuteResult execute = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql);
            if (!Boolean.TRUE.equals(execute.getSuccess())) {
                return DataResult.error("user.password.change.failed", execute.getMessage());
            }
            return DataResult.of(execute);
        } catch (SQLException e) {
            throw new BusinessException("user.password.change.failed", new Object[]{ExceptionUtils.getMessage(e)});
        }
    }

    @Override
    public void modify(TableSpace tableSpace) {
        if (tableSpace == null || StringUtils.isBlank(tableSpace.getUserName())
                || (StringUtils.isBlank(tableSpace.getDefaultTableSpace())
                && StringUtils.isBlank(tableSpace.getTempTableSpace()))) {
            throw new BusinessException("user.modify.paramRequired");
        }
        String sql = Chat2DBContext.getSqlBuilder().modifyUserTableSpaces(tableSpace.getUserName(),
                tableSpace.getDefaultTableSpace(), tableSpace.getTempTableSpace());
        try {
            ExecuteResult result = SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql);
            if (result == null || !Boolean.TRUE.equals(result.getSuccess())) {
                throw new BusinessException("user.modify.failed");
            }
        } catch (SQLException e) {
            throw new BusinessException("user.modify.failed", new Object[]{ExceptionUtils.getMessage(e)});
        }
    }

    @Override
    public UserOperationResult addOrDelUserRole(String userName, List<TableRoleData> newRoles, List<TableRoleData> oldRoles) {
        if (StrUtil.isBlank(userName) || newRoles == null || oldRoles == null) {
            throw new BusinessException("user.role.change.paramRequired");
        }
        return executeChanges(buildUserRoleChanges(Chat2DBContext.getSqlBuilder(), userName, newRoles, oldRoles));
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
            UserOperationResult roleResult = executeChanges(buildUserRoleChanges(Chat2DBContext.getSqlBuilder(), userName,
                    requestedRoles, Collections.emptyList()));
            if (!roleResult.isSuccess()) {
                throw new BusinessException("user.role.change.failed", new Object[]{roleResult.getItems().stream()
                        .filter(item -> "FAILED".equals(item.getStatus())).findFirst().get().getMessage()});
            }
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
            String cause = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            if (cause.contains("password not accord with complexity rule") || cause.contains("ora-28003")
                    || cause.contains("密码长度不符合要求") || cause.contains("密码复杂度")) {
                throw new BusinessException("user.password.policy.failed");
            }
            String message = ExceptionUtils.getMessage(e);
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
        List<TableObjectRoleData> granted = Collections.emptyList();
        if (StrUtil.isNotBlank(request.getSchemaName()) && StrUtil.isNotBlank(request.getTableName())
                && StrUtil.isNotBlank(request.getUserName())) {
            granted = Chat2DBContext.getMetaData().allObjectRole(Chat2DBContext.getConnection(),
                    request.getUserName(), request.getSchemaName(), request.getTableName());
        }
        return normalizeObjectRoles(granted);
    }

    @Override
    public Map<String, List<TableObjectRoleData>> allObjectRoles(TableBriefQueryRequest request) {
        if (StrUtil.isBlank(request.getSchemaName()) || StrUtil.isBlank(request.getUserName())
                || request.getTableNames() == null || request.getTableNames().isEmpty()
                || request.getTableNames().size() > 200
                || request.getTableNames().stream().anyMatch(StrUtil::isBlank)) {
            throw new BusinessException("user.objectRole.paramRequired");
        }
        Map<String, List<TableObjectRoleData>> granted = Chat2DBContext.getMetaData().allObjectRoles(
                Chat2DBContext.getConnection(), request.getUserName(), request.getSchemaName(), request.getTableNames());
        Map<String, List<TableObjectRoleData>> result = new LinkedHashMap<>();
        for (String tableName : request.getTableNames()) {
            result.put(tableName, normalizeObjectRoles(granted.get(tableName)));
        }
        return result;
    }

    private List<TableObjectRoleData> normalizeObjectRoles(List<TableObjectRoleData> granted) {
        String dbType = Chat2DBContext.getDBConfig().getDbType();
        String[] array;
        if (DBTypeEnum.DM.name().equals(dbType)) {
            array = new String[]{"ALL", "SELECT", "INSERT", "DELETE", "UPDATE", "REFERENCES", "SELECT FOR DUMP", "ALTER", "INDEX"};
        } else {
            array = new String[]{"ALL", "SELECT", "INSERT", "DELETE", "UPDATE", "REFERENCES", "ALTER", "INDEX"};
        }
        Map<String, TableObjectRoleData> privileges = new LinkedHashMap<>();
        for (String privilegeName : array) {
            TableObjectRoleData empty = new TableObjectRoleData();
            empty.setDesc(privilegeName);
            privileges.put(privilegeName, empty);
        }
        if (granted != null) {
                for (TableObjectRoleData privilege : granted) {
                    if (privilege != null && StrUtil.isNotBlank(privilege.getDesc())) {
                        TableObjectRoleData existing = privileges.get(privilege.getDesc());
                        if (existing != null && Boolean.TRUE.equals(existing.getRule())) {
                            existing.setToRule(Boolean.TRUE.equals(existing.getToRule())
                                    || Boolean.TRUE.equals(privilege.getToRule()));
                        } else {
                            privileges.put(privilege.getDesc(), privilege);
                        }
                    }
                }
            TableObjectRoleData all = privileges.get("ALL");
            boolean allGranted = Boolean.TRUE.equals(all.getRule());
            boolean allTransferable = Boolean.TRUE.equals(all.getToRule());
            boolean allIndividualGranted = true;
            boolean allIndividualTransferable = true;
            for (String privilegeName : array) {
                if (!"ALL".equals(privilegeName)) {
                    TableObjectRoleData privilege = privileges.get(privilegeName);
                    allIndividualGranted &= Boolean.TRUE.equals(privilege.getRule());
                    allIndividualTransferable &= Boolean.TRUE.equals(privilege.getToRule());
                }
            }
            allGranted |= allIndividualGranted;
            allTransferable |= allIndividualGranted && allIndividualTransferable;
            all.setRule(allGranted);
            all.setToRule(allGranted && allTransferable);
        }
        return privileges.values().stream().sorted(Comparator.comparing(TableObjectRoleData::getDesc)).collect(Collectors.toList());
    }

    @Override
    public UserOperationResult addObjectRole(TableBriefQueryRequest request) {
        if (request.getNewObjectRoleData() == null || request.getOldObjectRoleData() == null) {
            throw new BusinessException("user.objectRole.paramRequired");
        }
        if (StrUtil.isBlank(request.getSchemaName()) || StrUtil.isBlank(request.getTableName()) || StrUtil.isBlank(request.getUserName())) {
            throw new BusinessException("user.objectRole.paramRequired");
        }
        if (request.getSchemaName().equals(request.getUserName())) {
            throw new BusinessException("user.objectRole.sameSchema");
        }
        return executeChanges(buildObjectRoleChanges(Chat2DBContext.getSqlBuilder(), request));
    }

    @Override
    public UserOperationResult addObjectRoles(TableBriefQueryRequest request) {
        if (StrUtil.isBlank(request.getSchemaName()) || StrUtil.isBlank(request.getUserName())
                || request.getObjectRoleChanges() == null || request.getObjectRoleChanges().isEmpty()
                || request.getObjectRoleChanges().size() > 200) {
            throw new BusinessException("user.objectRole.paramRequired");
        }
        UserOperationResult summary = new UserOperationResult();
        for (TableBriefQueryRequest change : request.getObjectRoleChanges()) {
            String tableName = change == null ? null : change.getTableName();
            try {
                if (change == null) {
                    throw new BusinessException("user.objectRole.paramRequired");
                }
                change.setSchemaName(request.getSchemaName());
                change.setUserName(request.getUserName());
                UserOperationResult one = addObjectRole(change);
                String failure = one.getItems().stream()
                        .filter(item -> "FAILED".equals(item.getStatus()) || "ROLLBACK_FAILED".equals(item.getStatus()))
                        .map(item -> item.getName() + ": " + item.getMessage())
                        .collect(Collectors.joining("; "));
                summary.add(tableName, "OBJECT_ROLE", one.isSuccess() ? "SUCCESS" : "FAILED", failure);
            } catch (RuntimeException error) {
                summary.add(tableName, "OBJECT_ROLE", "FAILED", ExceptionUtils.getMessage(error));
            }
        }
        return summary;
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
                revokes.add(new SqlChange(roleName, "REVOKE",
                        sqlBuilder.delUserRole(userName, roleName),
                        sqlBuilder.addUserRole(userName, roleName,
                                Boolean.TRUE.equals(oldRole.getIsAdmin()) ? roleName : "")));
            }
            if (newRole != null && (oldRole == null || adminChanged)) {
                grants.add(new SqlChange(roleName, "GRANT",
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
                revokes.add(new SqlChange(privilegeName, "REVOKE",
                        singleSql(sqlBuilder.delObjectRole(request.getSchemaName(), request.getUserName(),
                                request.getTableName(), Collections.singletonList(oldRole))),
                        singleSql(sqlBuilder.addObjectRole(request.getSchemaName(), request.getUserName(),
                                request.getTableName(), Collections.singletonList(oldRole)))));
            }
            if (newRole != null && (oldRole == null || grantOptionChanged)) {
                grants.add(new SqlChange(privilegeName, "GRANT",
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

    private UserOperationResult executeChanges(List<SqlChange> changes) {
        return executeChanges(changes, sql -> SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql));
    }

    UserOperationResult executeChanges(List<SqlChange> changes, SqlRunner runner) {
        UserOperationResult result = new UserOperationResult();
        for (int index = 0; index < changes.size(); index++) {
            SqlChange change = changes.get(index);
            try {
                runner.run(change.sql);
                result.add(change.name, change.action, "SUCCESS", null);
            } catch (SQLException e) {
                result.add(change.name, change.action, "FAILED", ExceptionUtils.getMessage(e));
                for (int i = result.getItems().size() - 2; i >= 0; i--) {
                    SqlChange completed = changes.get(i);
                    try {
                        runner.run(completed.rollbackSql);
                        result.getItems().get(i).update("ROLLED_BACK", null);
                    } catch (SQLException rollbackException) {
                        result.getItems().get(i).update("ROLLBACK_FAILED", ExceptionUtils.getMessage(rollbackException));
                        log.warn("权限变更补偿失败,sql:{},error:{}", completed.rollbackSql,
                                rollbackException.getMessage());
                    }
                }
                for (int i = index + 1; i < changes.size(); i++) {
                    SqlChange skipped = changes.get(i);
                    result.add(skipped.name, skipped.action, "SKIPPED", null);
                }
                break;
            }
        }
        return result;
    }

    interface SqlRunner {
        void run(String sql) throws SQLException;
    }

    static class SqlChange {
        private final String name;
        private final String action;
        private final String sql;
        private final String rollbackSql;

        SqlChange(String name, String action, String sql, String rollbackSql) {
            this.name = name;
            this.action = action;
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
