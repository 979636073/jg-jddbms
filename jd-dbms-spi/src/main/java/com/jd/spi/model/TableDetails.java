package com.jd.spi.model;

import lombok.Data;

/**
 * 表空间详情信息
 */
@Data
public class TableDetails{

    /**
     * 表名
     */
    private String name;
    /**
     * 模式
     */
    private String schema;
    /**
     * 空间
     */
    private String tableSpace;
    /**
     * 最终调整时间
     */
    private String lastAnalyzed;
    /**
     * 行数
     */
    private String numRows;
    /**
     * 创建时间
     */
    private String created;
    /**
     * ddl最终调整时间
     */
    private String lastDDL;

    /**
     * 是否有视图
     */
    private String valid;

    /**
     * 表描述
     */
    private String comment;

    private String userId;
    private String password;
    private String accountStatus;
    private String lockDate;
    private String expiryDate;
    private String defaultTableSpace;
    private String tempTableSpace;
    private String profile;
    private String consumerGroup;
    private String passwordVersions;
    private String editionsEnabled;
    private String authenticationType;
    private String schemaName;
    private String dataBaseName;
    private String nowDate;

    /**
     * 空间路径
     */
    private String path;
    /**
     * 空间类型
     */
    private String type;
    /**
     * 空间状态
     */
    private String status;
    /**
     * 文件数量
     */
    private String fileNum;
    /**
     * 总大小
     */
    private String totalSize;
    /**
     * 空闲大小
     */
    private String freeSize;

    /**
     * 使用大小
     */
    private String useSize;

    /**
     * 使用率
     */
    private String usageRate;

    /**
     * 自动扩充
     */
    private String autoScaling;

    /**
     * 自动步长
     */
    private String autoSize;


    /**
     * 自动步长
     */
    private Boolean autoCheck;

    /**
     * 是否设置最大值
     */
    private Boolean check;

    /**
     * 扩充上限
     */
    private String expandUpperLimit;

    /**
     * 使用最大占比
     */
    private String useOfMax;
}
