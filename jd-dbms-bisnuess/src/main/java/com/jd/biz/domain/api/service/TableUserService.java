package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.controller.rdb.vo.ExecuteResultVO;
import com.jd.biz.controller.rdb.vo.UserOperationResult;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 表用户管理
 */
public interface TableUserService {

    ListResult<Table> tableUsers(String databaseName, Integer requestType);

    Table tableUser(String databaseName, String username);

    DataResult<Boolean> lockUser(String lockName, Boolean isLock);

    DataResult<ExecuteResult> managePassWord(String name, String newPassWord);

    void modify(TableSpace tableSpace);

    UserOperationResult addOrDelUserRole(String userName, List<TableRoleData> newRoles, List<TableRoleData> oldRoles) throws SQLException;

    AjaxResult createUser(String userName, String newPassWord, String defaultTableSpace, String temptableSpace, List<TableRoleData> roles);

    List<TableRoleData> allRole(TableBriefQueryRequest request);

    List<TableObjectRoleData> allObjectRole(TableBriefQueryRequest request);

    Map<String, List<TableObjectRoleData>> allObjectRoles(TableBriefQueryRequest request);

    UserOperationResult addObjectRole(TableBriefQueryRequest request) throws SQLException;

    UserOperationResult addObjectRoles(TableBriefQueryRequest request) throws SQLException;
}
