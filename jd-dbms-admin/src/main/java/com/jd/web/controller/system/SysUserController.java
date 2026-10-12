package com.jd.web.controller.system;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.jd.common.annotation.Log;
import com.jd.common.config.HzbConfig;
import com.jd.common.core.controller.BaseController;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.core.domain.entity.SysDept;
import com.jd.common.core.domain.entity.SysRole;
import com.jd.common.core.domain.entity.SysUser;
import com.jd.common.core.page.TableDataInfo;
import com.jd.common.enums.BusinessType;
import com.jd.common.utils.SecurityUtil;
import com.jd.common.utils.StringUtils;
import com.jd.common.utils.poi.ExcelUtil;
import com.jd.framework.web.service.TokenService;
import com.jd.system.service.ISysDeptService;
import com.jd.system.service.ISysPostService;
import com.jd.system.service.ISysRoleService;
import com.jd.system.service.ISysUserService;

/**
 * 用户信息
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/user")
public class SysUserController extends BaseController
{
    private static final String PRESET_AVATAR_PATTERN = "^/avatar-presets/avatar-(0[1-9]|1[0-9]|20)\\.svg$";

    @Autowired
    private ISysUserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private ISysPostService postService;

    @Autowired
    private RoleGrantGuard roleGrantGuard;

    /**
     * 获取用户列表
     */
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysUser user)
    {
        startPage();
        List<SysUser> list = userService.selectUserList(user);
        return getDataTable(list);
    }

    @Log(title = "用户管理", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('system:user:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysUser user)
    {
        List<SysUser> list = userService.selectUserList(user);
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        util.exportExcel(response, list, "用户数据");
    }

    @Log(title = "用户管理", businessType = BusinessType.IMPORT)
    @PreAuthorize("@ss.hasPermi('system:user:import')")
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception
    {
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        List<SysUser> userList = util.importExcel(file.getInputStream());
        String operName = getUsername();
        String message = userService.importUser(userList, updateSupport, operName);
        return success(message);
    }

    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        util.importTemplateExcel(response, "用户数据");
    }

    /**
     * 根据用户编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:user:query')")
    @GetMapping(value = { "/", "/{userId}" })
    public AjaxResult getInfo(@PathVariable(value = "userId", required = false) Long userId)
    {
        userService.checkUserDataScope(userId);
        AjaxResult ajax = AjaxResult.success();
        List<SysRole> roles = roleService.selectRoleAll();
        ajax.put("roles", SysUser.isAdmin(userId) ? roles : roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList()));
        ajax.put("posts", postService.selectPostAll());
        if (StringUtils.isNotNull(userId))
        {
            SysUser sysUser = userService.selectUserById(userId);
            ajax.put(AjaxResult.DATA_TAG, sysUser);
            ajax.put("postIds", postService.selectPostListByUserId(userId));
            ajax.put("roleIds", sysUser.getRoles().stream().map(SysRole::getRoleId).collect(Collectors.toList()));
        }
        return ajax;
    }

    /**
     * 新增用户
     */
    @PreAuthorize("@ss.hasPermi('system:user:add')")
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysUser user)
    {
        roleGrantGuard.checkAssignableRoles(user.getRoleIds(), user.getUserId());
        if (StringUtils.isNotEmpty(user.getAvatar()) && !user.getAvatar().matches(PRESET_AVATAR_PATTERN))
        {
            return error("请选择系统提供的头像");
        }
        user.setNickName(user.getUserName());
        if (!userService.checkUserNameUnique(user))
        {
            return error("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user))
        {
            return error("新增用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user))
        {
            return error("新增用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setCreateBy(getUsername());
        user.setPassword(SecurityUtil.encryptPassword(user.getPassword()));
        return toAjax(userService.insertUser(user));
    }

    /**
     * 修改用户
     */
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysUser user)
    {
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        SysUser existingUser = userService.selectUserById(user.getUserId());
        if (existingUser == null)
        {
            return error("用户不存在");
        }
        if (StringUtils.isNotEmpty(user.getAvatar()) && !user.getAvatar().equals(existingUser.getAvatar())
                && !user.getAvatar().matches(PRESET_AVATAR_PATTERN))
        {
            return error("请选择系统提供的头像");
        }
        roleGrantGuard.checkAssignableRoles(user.getRoleIds(), user.getUserId());
        if (!userService.checkUserNameUnique(user))
        {
            return error("修改用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user))
        {
            return error("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user))
        {
            return error("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setUpdateBy(getUsername());
        int rows = userService.updateUser(user);
        if (rows > 0) tokenService.delLoginUserSessions(user.getUserId(), null);
        return toAjax(rows);
    }

    /** 在平台用户中为指定账号上传自定义头像。 */
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PostMapping("/{userId}/avatar")
    public AjaxResult uploadAvatar(@PathVariable Long userId, @RequestParam("avatarfile") MultipartFile file) throws Exception
    {
        if (SysUser.isAdmin(userId) && !userId.equals(getUserId()))
        {
            return error("不允许修改超级管理员头像");
        }
        userService.checkUserDataScope(userId);
        SysUser user = userService.selectUserById(userId);
        if (user == null)
        {
            return error("用户不存在");
        }
        if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
        {
            return error("请选择不超过 2 MB 的图片");
        }
        String mime = file.getContentType();
        if (!"image/png".equals(mime) && !"image/jpeg".equals(mime) && !"image/gif".equals(mime))
        {
            return error("仅支持 PNG、JPG、GIF 图片");
        }
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null || image.getWidth() > 2048 || image.getHeight() > 2048)
        {
            return error("图片无效或尺寸超过 2048 × 2048");
        }
        Path directory = Paths.get(HzbConfig.getProfile(), "avatars");
        Files.createDirectories(directory);
        String avatar = "/profile/avatars/" + UUID.randomUUID() + ".png";
        Path output = directory.resolve(avatar.substring(avatar.lastIndexOf('/') + 1));
        if (!ImageIO.write(image, "png", output.toFile()))
        {
            return error("图片保存失败");
        }
        if (!saveAvatar(userId, avatar))
        {
            Files.deleteIfExists(output);
            return error("头像更新失败");
        }
        AjaxResult result = success();
        result.put("imgUrl", avatar);
        return result;
    }

    /** 使用内置头像，头像编号由服务端严格校验。 */
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}/avatar")
    public AjaxResult selectAvatar(@PathVariable Long userId, @RequestBody Map<String, String> request)
    {
        if (SysUser.isAdmin(userId) && !userId.equals(getUserId()))
        {
            return error("不允许修改超级管理员头像");
        }
        userService.checkUserDataScope(userId);
        if (userService.selectUserById(userId) == null)
        {
            return error("用户不存在");
        }
        String avatar = request.get("avatar");
        if (avatar == null || !avatar.matches(PRESET_AVATAR_PATTERN))
        {
            return error("请选择系统提供的头像");
        }
        return toAjax(saveAvatar(userId, avatar) ? 1 : 0);
    }

    private boolean saveAvatar(Long userId, String avatar)
    {
        SysUser update = new SysUser(userId);
        update.setAvatar(avatar);
        update.setUpdateBy(getUsername());
        if (userService.updateUserProfile(update) <= 0)
        {
            return false;
        }
        if (userId.equals(getUserId()))
        {
            getLoginUser().getUser().setAvatar(avatar);
            tokenService.setLoginUser(getLoginUser());
        }
        return true;
    }

    /**
     * 删除用户
     */
    @PreAuthorize("@ss.hasPermi('system:user:remove')")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userIds}")
    public AjaxResult remove(@PathVariable Long[] userIds)
    {
        if (ArrayUtils.contains(userIds, getUserId()))
        {
            return error("当前用户不能删除");
        }
        int rows = userService.deleteUserByIds(userIds);
        if (rows > 0) for (Long userId : userIds) tokenService.delLoginUserSessions(userId, null);
        return toAjax(rows);
    }

    /**
     * 重置密码
     */
    @PreAuthorize("@ss.hasPermi('system:user:resetPwd')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/resetPwd")
    public AjaxResult resetPwd(@RequestBody SysUser user)
    {
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        user.setPassword(SecurityUtil.encryptPassword(user.getPassword()));
        user.setUpdateBy(getUsername());
        int rows = userService.resetPwd(user);
        if (rows > 0) {
            tokenService.delLoginUserSessions(user.getUserId(), null);
        }
        return toAjax(rows);
    }

    /**
     * 状态修改
     */
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysUser user)
    {
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        user.setUpdateBy(getUsername());
        int rows = userService.updateUserStatus(user);
        if (rows > 0) tokenService.delLoginUserSessions(user.getUserId(), null);
        return toAjax(rows);
    }

    /**
     * 根据用户编号获取授权角色
     */
    @PreAuthorize("@ss.hasPermi('system:user:query')")
    @GetMapping("/authRole/{userId}")
    public AjaxResult authRole(@PathVariable("userId") Long userId)
    {
        AjaxResult ajax = AjaxResult.success();
        SysUser user = userService.selectUserById(userId);
        List<SysRole> roles = roleService.selectRolesByUserId(userId);
        ajax.put("user", user);
        ajax.put("roles", SysUser.isAdmin(userId) ? roles : roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList()));
        return ajax;
    }

    /**
     * 用户授权角色
     */
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.GRANT)
    @PutMapping("/authRole")
    public AjaxResult insertAuthRole(Long userId, Long[] roleIds)
    {
        userService.checkUserDataScope(userId);
        roleGrantGuard.checkAssignableRoles(roleIds, userId);
        userService.insertUserAuth(userId, roleIds);
        tokenService.delLoginUserSessions(userId, null);
        return success();
    }

    /**
     * 获取部门树列表
     */
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @GetMapping("/deptTree")
    public AjaxResult deptTree(SysDept dept)
    {
        return success(deptService.selectDeptTreeList(dept));
    }
}
