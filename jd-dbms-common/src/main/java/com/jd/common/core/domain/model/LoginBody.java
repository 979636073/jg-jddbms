package com.jd.common.core.domain.model;

import lombok.Data;

/**
 * 用户登录对象
 * 
 * @author ruoyi
 */
@Data
public class LoginBody
{
    /**
     * 用户名
     */
    private String username;

    /**
     * 用户密码
     */
    private String password;

    /**
     * 验证码
     */
    private String code;


    /**
     * 验证码
     */
    private String ticketToken;

    /**
     * 唯一标识
     */
    private String uuid;


}
