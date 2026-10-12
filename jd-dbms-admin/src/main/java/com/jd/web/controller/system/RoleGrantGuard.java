package com.jd.web.controller.system;

import com.jd.common.core.domain.entity.SysMenu;
import com.jd.common.core.domain.entity.SysRole;
import com.jd.common.core.domain.entity.SysUser;
import com.jd.common.exception.ServiceException;
import com.jd.common.utils.SecurityUtil;
import com.jd.system.service.ISysMenuService;
import com.jd.system.service.ISysRoleService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Prevent a delegated account from granting a role or menu it cannot use. */
@Component
public class RoleGrantGuard {
    private final ISysMenuService menuService;
    private final ISysRoleService roleService;

    public RoleGrantGuard(ISysMenuService menuService, ISysRoleService roleService) {
        this.menuService = menuService;
        this.roleService = roleService;
    }

    public void checkAssignableRoles(Long[] roleIds) {
        checkAssignableRoles(roleIds, null);
    }

    public void checkAssignableRoles(Long[] roleIds, Long targetUserId) {
        if (SysUser.isAdmin(SecurityUtil.getUserId()) || roleIds == null) return;
        boolean securityAdmin = isSecurityAdmin();
        Set<Long> permitted = menuService.selectMenuList(SecurityUtil.getUserId()).stream()
                .map(SysMenu::getMenuId).collect(Collectors.toSet());
        for (Long roleId : roleIds) {
            SysRole role = roleId == null ? null : roleService.selectRoleById(roleId);
            if (role == null || role.isAdmin() || "admin".equalsIgnoreCase(role.getRoleKey()) ||
                    !"0".equals(role.getStatus()) ||
                    (!securityAdmin && !permitted.containsAll(menuService.selectMenuListByRoleId(roleId))) ||
                    (securityAdmin && SecurityUtil.getUserId().equals(targetUserId) &&
                            SecurityUtil.getLoginUser().getUser().getRoles().stream()
                                    .noneMatch(held -> roleId.equals(held.getRoleId())))) {
                throw new ServiceException("不能分配超出当前权限范围的角色");
            }
        }
    }

    public void checkDefinition(SysRole role) {
        if (SysUser.isAdmin(SecurityUtil.getUserId())) return;
        if (role == null || "admin".equalsIgnoreCase(role.getRoleKey()) ||
                "common".equalsIgnoreCase(role.getRoleKey()) ||
                (role.getRoleKey() != null && role.getRoleKey().toLowerCase(java.util.Locale.ROOT).startsWith("dbms_")) ||
                (role.getRoleId() != null && SysRole.isAdmin(role.getRoleId()))) {
            throw new ServiceException("不能配置超级管理员角色");
        }
        Set<Long> permitted = menuService.selectMenuList(SecurityUtil.getUserId()).stream()
                .map(SysMenu::getMenuId).collect(Collectors.toSet());
        if (isSecurityAdmin()) {
            permitted.add(2002L);
            for (long actionMenuId = 2100L; actionMenuId <= 2107L; actionMenuId++)
                permitted.add(actionMenuId);
        }
        Set<Long> requested = role.getMenuIds() == null ? new HashSet<>() : new HashSet<>(Arrays.asList(role.getMenuIds()));
        if (!permitted.containsAll(requested)) {
            throw new ServiceException("不能授予超出当前权限范围的菜单和操作");
        }
    }

    public void checkManagedRole(Long roleId) {
        if (SysUser.isAdmin(SecurityUtil.getUserId())) return;
        SysRole role = roleId == null ? null : roleService.selectRoleById(roleId);
        if (isSecurityAdmin() && role != null && !role.isAdmin() &&
                !"admin".equalsIgnoreCase(role.getRoleKey())) return;
        Set<Long> permitted = menuService.selectMenuList(SecurityUtil.getUserId()).stream()
                .map(SysMenu::getMenuId).collect(Collectors.toSet());
        if (role == null || role.isAdmin() || "admin".equalsIgnoreCase(role.getRoleKey()) ||
                !permitted.containsAll(menuService.selectMenuListByRoleId(roleId))) {
            throw new ServiceException("不能管理超出当前权限范围的角色");
        }
    }

    public void checkMutableRole(Long roleId) {
        checkManagedRole(roleId);
        if (SysUser.isAdmin(SecurityUtil.getUserId())) return;
        SysRole role = roleService.selectRoleById(roleId);
        if (role != null && ("common".equalsIgnoreCase(role.getRoleKey()) ||
                role.getRoleKey().toLowerCase(java.util.Locale.ROOT).startsWith("dbms_"))) {
            throw new ServiceException("内置角色仅允许超级管理员修改");
        }
    }

    private boolean isSecurityAdmin() {
        return SecurityUtil.getLoginUser().getUser() != null &&
                SecurityUtil.getLoginUser().getUser().getRoles() != null &&
                SecurityUtil.getLoginUser().getUser().getRoles().stream()
                        .anyMatch(role -> "dbms_security".equals(role.getRoleKey()) && "0".equals(role.getStatus()));
    }

    public boolean canDelegateDatabaseActions() {
        return SysUser.isAdmin(SecurityUtil.getUserId()) || isSecurityAdmin();
    }

    public void requireSuperAdmin() {
        if (!SysUser.isAdmin(SecurityUtil.getUserId())) {
            throw new ServiceException("该系统配置仅允许超级管理员修改");
        }
    }
}
