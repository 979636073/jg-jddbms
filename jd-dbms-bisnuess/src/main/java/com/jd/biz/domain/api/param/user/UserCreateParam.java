package com.jd.biz.domain.api.param.user;

import com.jd.biz.domain.api.enums.RoleCodeEnum;
import com.jd.biz.domain.api.enums.ValidStatusEnum;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * create
 *
 * @author Jiaju Zhuang
 */
@Data
public class UserCreateParam {
    /**
     * 用户名
     */
    @NotNull
    private String userName;

    /**
     * 密码
     */
    @NotNull
    private String password;

    /**
     * 昵称
     */
    @NotNull
    private String nickName;

    /**
     * 邮箱
     */
    @NotNull
    private String email;

    /**
     * 角色编码
     *
     * @see RoleCodeEnum
     */
    @NotNull
    private String roleCode;

    /**
     * 用户状态
     *
     * @see ValidStatusEnum
     */
    @NotNull
    private String status;
}
