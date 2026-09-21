package com.jd.biz.domain.api.model;

import com.jd.biz.domain.api.enums.RoleCodeEnum;
import com.jd.biz.domain.api.enums.ValidStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import java.util.Date;

/**
 * 用户信息
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    /**
     * 主键
     */
    @NotNull
    private Long id;

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
    private String roleCode;

    /**
     * 用户状态
     *
     * @see ValidStatusEnum
     */
    @NotNull
    private String status;

    /**
     * 修改时间
     */
    private Date gmtModified;

    /**
     * 修改人用户id
     */
    private Long modifiedUserId;

    /**
     * 修改人用户
     */
    private User modifiedUser;
}
