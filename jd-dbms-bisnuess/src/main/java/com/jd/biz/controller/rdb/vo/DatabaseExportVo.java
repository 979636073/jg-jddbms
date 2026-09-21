package com.jd.biz.controller.rdb.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class DatabaseExportVo implements Serializable {
    private static final long serialVersionUID = 8377899386569086415L;
    /**
     * 数据源id
     */
    private Long dataSourceId;

    /**
     * DB名称
     */
    private String databaseName;

    /**
     * 表所在空间
     */
    private String schemaName;
    /**
     * 执行状态
     */
    private String status;
    /**
     * 已执行数
     */
    private int exported;

    /**
     * 总数
     */
    private int total;
    /**
     * 下载地址
     */
    private String downloadUrl;

    private Long userId;
}
