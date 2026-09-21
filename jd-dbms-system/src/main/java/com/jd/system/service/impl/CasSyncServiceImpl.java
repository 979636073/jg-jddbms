package com.jd.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.easy.cas.client.model.SsoRole;
import com.easy.cas.client.model.SsoUser;
import com.easy.cas.client.model.Tips;
import com.easy.cas.client.service.CasSyncService;
import com.easy.cas.client.util.PropertiesUtil;
import com.jd.common.core.domain.entity.SysRole;
import com.jd.common.core.domain.entity.SysUser;
import com.jd.common.utils.SecurityUtil;
import com.jd.system.mapper.SysRoleMapper;
import com.jd.system.service.ISysConfigService;
import com.jd.system.service.ISysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class CasSyncServiceImpl implements CasSyncService {
    private static final Logger log = LoggerFactory.getLogger(CasSyncServiceImpl.class);
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private ISysUserService userService;
    @Autowired
    private ISysConfigService configService;

    @Override
    public List<SsoRole> listRole(String client_id, String client_secret) {
        log.info("查询同步角色列表,client_id:{}", client_id);
        List<SsoRole> ssoRoleList = new ArrayList<>();
        try {
            if (StrUtil.isBlank(client_id) || StrUtil.isBlank(client_secret)) {
                log.warn("查询同步角色列表,客户端ID或秘钥为空");
                return ssoRoleList;
            }
            if (!StrUtil.equals(client_id, PropertiesUtil.getClientId())
                    || !StrUtil.equals(client_secret, PropertiesUtil.getClientSecret())) {
                log.warn("查询同步角色列表,客户端ID或秘钥不一致");
                return ssoRoleList;
            }

            List<SysRole> sysRoles = sysRoleMapper.selectRoleList(new SysRole());
            for (SysRole sysRole : sysRoles) {
                SsoRole ssoRole = new SsoRole();
                ssoRole.setCode(sysRole.getRoleKey());
                ssoRole.setName(sysRole.getRoleName());
                ssoRoleList.add(ssoRole);
            }
            log.info("查询同步角色列表完成,client_id:{},角色数量:{}", client_id, sysRoles.size());
            return ssoRoleList;
        } catch (Exception e) {
            return ssoRoleList;
        }
    }

    @Override
    public Tips sendUserAndRole(SsoUser ssoUser) {
        if (ssoUser == null || StrUtil.isBlank(ssoUser.getUsername())) {
            log.warn("查询同步用户角色,参数为空");
            return Tips.getErrorTips("查询同步用户角色,参数为空");
        }
        List<SysRole> roleList = new ArrayList<>();
        if (StrUtil.isNotEmpty(ssoUser.getRoles())) {
            String[] split = ssoUser.getRoles().split(",");
            for (String key : split) {
                SysRole sysRole = sysRoleMapper.checkRoleKeyUnique(key);
                if (sysRole != null) {
                    roleList.add(sysRole);
                }
            }
        }
        Long[] roleIds = null;
        if (CollUtil.isNotEmpty(roleList)) {
            roleIds = new Long[roleList.size()];
            for (int i = 0; i < roleList.size(); i++) {
                roleIds[i] = roleList.get(i).getRoleId();
            }
        }
        //查询用户
        SysUser sysUser = userService.selectUserByUserName(ssoUser.getUsername());
        if (sysUser == null) {
            //用户不存在新增用户
            String password;
            password = configService.selectConfigByKey("sys.user.initPassword");
            sysUser = new SysUser();
            sysUser.setDeptId(0L); //由于未同步部门新增时给默认部门
            sysUser.setUserName(ssoUser.getUsername());
            sysUser.setNickName(ssoUser.getNickname());
            sysUser.setEmail(ssoUser.getEmail());
            sysUser.setPhonenumber(ssoUser.getPhone());
            sysUser.setSex("0");
            sysUser.setPassword(SecurityUtil.encryptPassword(password));//由于未同步密码新增时给默认密码
            sysUser.setStatus("0");
            sysUser.setDelFlag("0");
            sysUser.setRoleIds(roleIds);
            boolean b1 = userService.checkEmailUnique(sysUser);
            boolean b2 = userService.checkPhoneUnique(sysUser);
            if (b1 && b2) {
                userService.insertUser(sysUser);
            } else {
                log.warn("同步用户角色,用户的手机或邮箱已被使用,{}", ssoUser);
                return Tips.getErrorTips("同步用户角色,用户的手机或邮箱已被使用");
            }

        } else {
            //更新用户
            sysUser.setNickName(ssoUser.getNickname());
            sysUser.setPhonenumber(ssoUser.getPhone());
            sysUser.setEmail(ssoUser.getEmail());
            if (!"admin".equals(sysUser.getUserName())) {
                // 若是超管，不接受门户角色修改
                sysUser.setRoleIds(roleIds);
            }
            boolean b1 = userService.checkEmailUnique(sysUser);
            boolean b2 = userService.checkPhoneUnique(sysUser);
            if (b1 && b2) {
                userService.updateUser(sysUser);
            } else {
                log.warn("同步用户角色,用户的手机或邮箱已被使用,{}", ssoUser);
                return Tips.getErrorTips("同步用户角色,用户的手机或邮箱已被使用");
            }
        }
        return Tips.getSuccessTips();
    }

    @Override
    public Tips sendDelUser(SsoUser ssoUser) {
        return null;
    }
}
