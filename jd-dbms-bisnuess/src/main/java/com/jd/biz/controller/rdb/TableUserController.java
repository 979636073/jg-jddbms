package com.jd.biz.controller.rdb;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.controller.rdb.request.TableRequest;
import com.jd.biz.controller.rdb.vo.ExecuteResultVO;
import com.jd.biz.controller.rdb.vo.TableVO;
import com.jd.biz.controller.rdb.vo.UserOperationResult;
import com.jd.biz.domain.api.service.TableUserService;
import com.jd.common.annotation.Log;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.common.tools.common.util.I18nUtils;
import com.jd.spi.model.*;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ConnectionInfoAspect
@RequestMapping("/api/rdb/user")
@RestController
@Slf4j
public class TableUserController {

    @Autowired
    private TableUserService tableUserService;

    @Resource
    private RdbWebConverter rdbWebConverter;

    /**
     * 获取用户详情列表
     *
     * @param request 包含查询条件的请求对象
     * @return 返回包含表信息的 WebPageResult 对象
     */
    @GetMapping("/list")
    public WebPageResult<TableVO> list(@Valid TableBriefQueryRequest request) {
        ListResult<Table> tableDTOPageResult = tableUserService.tableUsers(request.getDatabaseName(), request.getRequestType());
        Integer pageSize = tableDTOPageResult.getData() != null ? tableDTOPageResult.getData().size() : 0;
        List<TableVO> tableVOS = rdbWebConverter.tableDto2vo(tableDTOPageResult.getData());
        return WebPageResult.of(tableVOS, Long.valueOf(tableDTOPageResult.getData().size()), 1, pageSize);
    }


    @GetMapping("/query")
    public DataResult<TableVO> query(@Valid TableBriefQueryRequest request) {
        Table tableDTO = tableUserService.tableUser(request.getDatabaseName(), request.getUserName());
        TableVO tableVO = rdbWebConverter.tableDto2vo(tableDTO);
        return DataResult.of(tableVO);
    }


    /**
     * 授予或删除用户角色权限
     *
     * @param request
     * @return
     */
    @Log(title = "数据库用户角色授权", businessType = BusinessType.GRANT)
    @PostMapping("/addOrDelUserRole")
    public DataResult<UserOperationResult> addOrDelUserRole(@Valid @RequestBody TableBriefQueryRequest request) throws SQLException {
        if (StringUtils.isBlank(request.getUserName())) {
            throw new BusinessException("user.role.change.paramRequired");
        }
        if (request.getNewRoles() == null || request.getOldRoles() == null) {
            throw new BusinessException("user.role.change.paramRequired");
        }
        UserOperationResult result = tableUserService.addOrDelUserRole(request.getUserName(), request.getNewRoles(), request.getOldRoles());
        if (result.isSuccess()) {
            return DataResult.of(result);
        }
        DataResult<UserOperationResult> response = DataResult.error("user.role.change.failed", failureMessage(result));
        response.setData(result);
        return response;
    }

    /**
     * 创建用户
     * @param request
     * @return
     */
    @Log(title = "创建数据库用户", businessType = BusinessType.INSERT,
            excludeParamNames = {"newPassWord"})
    @PostMapping("/createUser")
    public AjaxResult createUser(@Valid @RequestBody TableBriefQueryRequest request) {
        if (StringUtils.isAnyBlank(request.getUserName(), request.getNewPassWord())) {
            throw new BusinessException("user.namePassword.required");
        }
        List<TableRoleData> list = new ArrayList<>();
        if (CollUtil.isNotEmpty(request.getRoleList())) {
            list.addAll(request.getRoleList());
        }
        return tableUserService.createUser(request.getUserName(), request.getNewPassWord(), request.getDefaultTableSpace(), request.getTempTableSpace(), list);
    }

    /**
     * 加锁解锁用户
     * @param request
     * @return
     */
    @Log(title = "数据库用户锁定状态", businessType = BusinessType.UPDATE)
    @PostMapping("/lockUser")
    public DataResult<Boolean> lockUser(@RequestBody @Valid TableBriefQueryRequest request) {
        if (StringUtils.isBlank(request.getLockName()) || request.getIsLock() == null) {
            throw new BusinessException("user.lock.paramRequired");
        }
        return tableUserService.lockUser(request.getLockName(), request.getIsLock());
    }


    /**
     * 获取全部角色列表
     * @param request
     * @return 返回包含表信息的 ListResult 对象
     */
    @GetMapping("/allRole")
    public ListResult<TableRoleData> allRole(@Valid TableBriefQueryRequest request) {
        List<TableRoleData> tableSpaces = tableUserService.allRole(request);
        return ListResult.of(tableSpaces);
    }


    /**
     * 获取全部对象角色列表
     * @return 返回包含表信息的 ListResult 对象
     */
    @PostMapping("/allObjectRole")
    public ListResult<TableObjectRoleData> allObjectRole(@RequestBody TableBriefQueryRequest request) {
        return ListResult.of(tableUserService.allObjectRole(request));
    }


    /**
     * 获取全部对象角色列表
     * @return 返回包含表信息的 ListResult 对象
     */
    @Log(title = "数据库对象权限授权", businessType = BusinessType.GRANT)
    @PostMapping("/addObjectRole")
    public DataResult<UserOperationResult> addObjectRole(@RequestBody @Valid TableBriefQueryRequest request) throws SQLException {
        UserOperationResult result = tableUserService.addObjectRole(request);
        if (result.isSuccess()) {
            return DataResult.of(result);
        }
        DataResult<UserOperationResult> response = DataResult.error("user.objectRole.change.failed", failureMessage(result));
        response.setData(result);
        return response;
    }


    /**
     * 修改用户密码
     *
     * @param request
     * @return
     */
    @Log(title = "修改数据库用户密码", businessType = BusinessType.UPDATE,
            excludeParamNames = {"newPassWord"})
    @PostMapping("/managePassWord")
    public DataResult<ExecuteResult> managePassWord(@RequestBody @Valid TableBriefQueryRequest request) {
        if (StringUtils.isAnyBlank(request.getUserName(), request.getNewPassWord())) {
            throw new BusinessException("user.namePassword.required");
        }
        return tableUserService.managePassWord(request.getUserName(), request.getNewPassWord());
    }

    /**
     * @param request
     * @return
     */
    @GetMapping("/queryUserRolesList")
    public DataResult<TableVO> queryUserRolesList(@Valid TableBriefQueryRequest request) {
        Table tableDTO = tableUserService.tableUser(request.getDatabaseName(), request.getUserName());
        TableVO tableVO = rdbWebConverter.tableDto2vo(tableDTO);
        return DataResult.of(tableVO);
   }

    /**
     * 用户修改
     * @param request
     * @return
     */
    @Log(title = "修改数据库用户", businessType = BusinessType.UPDATE)
    @PostMapping("/modify")
    public AjaxResult modifySql(@Valid @RequestBody TableBriefQueryRequest request) {
        TableSpace tableSpace = new TableSpace();
        BeanUtil.copyProperties(request, tableSpace);
        tableUserService.modify(tableSpace);
        return AjaxResult.success();
    }

    /**
     * 删除用户
     * @return
     */
    @Log(title = "删除数据库用户", businessType = BusinessType.DELETE)
    @PostMapping("/dropUser")
    public DataResult<UserOperationResult> dropUser(@Valid @RequestBody TableBriefQueryRequest request) {
        if (CollUtil.isEmpty(request.getUserNames())
                || request.getUserNames().stream().anyMatch(StringUtils::isBlank)) {
            throw new BusinessException("user.drop.nameRequired");
        }
        UserOperationResult result = dropUsers(request.getUserNames(), userName -> {
            String sql = Chat2DBContext.getSqlBuilder().dropUser(userName);
            SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql);
        });
        if (result.isSuccess()) {
            return DataResult.of(result);
        }
        List<String> failedUserNames = new ArrayList<>();
        for (UserOperationResult.Item item : result.getItems()) {
            if ("FAILED".equals(item.getStatus())) failedUserNames.add(item.getName());
        }
        String errorCode = "user.drop.partialFailed";
        String errorMessage = I18nUtils.getMessage(errorCode,
                new Object[]{result.getItems().size() - failedUserNames.size(), failedUserNames.size(), String.join(", ", failedUserNames)});
        DataResult<UserOperationResult> response = DataResult.error(errorCode, errorMessage);
        response.setData(result);
        return response;
    }

    UserOperationResult dropUsers(List<String> userNames, UserDropper dropper) {
        UserOperationResult result = new UserOperationResult();
        for (String userName : userNames) {
            try {
                dropper.drop(userName);
                result.add(userName, "DROP", "SUCCESS", null);
            } catch (Exception e) {
                result.add(userName, "DROP", "FAILED", e.getMessage());
                log.warn("删除用户失败,userName:{},error:{}", userName, e.getMessage());
            }
        }
        return result;
    }

    private String failureMessage(UserOperationResult result) {
        return result.getItems().stream()
                .filter(item -> "FAILED".equals(item.getStatus()) || "ROLLBACK_FAILED".equals(item.getStatus()))
                .map(item -> item.getAction() + " " + item.getName() + ": " + item.getMessage())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    interface UserDropper {
        void drop(String userName) throws Exception;
    }
}
