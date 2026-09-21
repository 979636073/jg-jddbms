package com.jd.spi.model;


import lombok.Data;

/**
 * 数据库用户权限
 */
@Data
public class TableUserRole {
    /**
     * 权限
     */
    private String role;
    /**
     * 是否是管理员
     */
    private String isAdmin;
    /**
     * 是否默认权限
     */
    private String isDefault;
    /**
     * 被授予人
     */
    private String grantee;
}
