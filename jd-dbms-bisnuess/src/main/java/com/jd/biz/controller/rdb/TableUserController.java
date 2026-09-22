package com.jd.biz.controller.rdb;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.controller.rdb.request.TableRequest;
import com.jd.biz.controller.rdb.vo.ExecuteResultVO;
import com.jd.biz.controller.rdb.vo.TableVO;
import com.jd.biz.domain.api.service.TableUserService;
import com.jd.common.annotation.Log;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
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
    public AjaxResult addOrDelUserRole(@Valid @RequestBody TableBriefQueryRequest request) throws SQLException {
        if (StringUtils.isBlank(request.getUserName())) {
            throw new BusinessException("user.role.change.paramRequired");
        }
        if (request.getNewRoles() == null || request.getOldRoles() == null) {
            throw new BusinessException("user.role.change.paramRequired");
        }
        return tableUserService.addOrDelUserRole(request.getUserName(), request.getNewRoles(), request.getOldRoles());
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
    @GetMapping("/lockUser")
    public DataResult<Boolean> lockUser(@Valid TableBriefQueryRequest request) {
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
    public DataResult<Boolean> addObjectRole(@RequestBody @Valid TableBriefQueryRequest request) throws SQLException {
        return DataResult.of(tableUserService.addObjectRole(request));
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
    public ActionResult dropUser(@Valid @RequestBody TableBriefQueryRequest request) {
        if (CollUtil.isEmpty(request.getUserNames())
                || request.getUserNames().stream().anyMatch(StringUtils::isBlank)) {
            throw new BusinessException("user.drop.nameRequired");
        }
        List<String> failedUserNames = new ArrayList<>();
        int successCount = 0;
        for (String userName : request.getUserNames()) {
            try {
                String sql = Chat2DBContext.getSqlBuilder().dropUser(userName);
                SQLExecutor.getInstance().execute(Chat2DBContext.getConnection(), sql);
                successCount++;
            } catch (Exception e) {
                failedUserNames.add(userName);
                log.warn("删除用户失败,userName:{},error:{}", userName, e.getMessage());
            }
        }
        if (failedUserNames.isEmpty()) {
            return ActionResult.isSuccess();
        }
        String errorCode = "user.drop.partialFailed";
        String errorMessage = I18nUtils.getMessage(errorCode,
                new Object[]{successCount, failedUserNames.size(), String.join(", ", failedUserNames)});
        return ActionResult.fail(errorCode, errorMessage, null);
    }
}
