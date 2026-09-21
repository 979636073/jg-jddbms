package com.jd.spi.model;

import lombok.Data;

@Data
public class TableRole {
    /**
     * 库名
     */
    private String schemaName;
    /**
     * 表名
     */
    private String tableName;
    /**
     * 被授权者
     */
    private String grantee;
    /**
     * 权限模式
     */
    private String privilege;
    /**
     * 是否可以转让授权
     */
    private String grantAble;
    /**
     * 权限拥有者
     */
    private String grantor;
}
