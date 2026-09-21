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
public class UserUpdateParam {
    /**
     * 主键
     */
    @NotNull
    private Long id;

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
    private String roleCode;

    /**
     * 用户状态
     *
     * @see ValidStatusEnum
     */
    @NotNull
    private String status;
}
