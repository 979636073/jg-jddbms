package com.jd.biz.domain.api.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>
 * 我的执行记录
 * </p>
 *
 * @author ali-dbhub
 * @since 2022-09-18
 */
@Data
public class OperationLog {

    /**
     * 主键
     */
    private Long id;

    /**
     * 执行用户ID
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime gmtCreate;

    /**
     * 修改时间
     */
    private LocalDateTime gmtModified;

    /**
     * 数据源连接ID
     */
    private Long dataSourceId;

    /**
     * 数据源
     */
    private String dataSourceName;

    /**
     * db名称
     */
    private String databaseName;

    /**
     * 数据库类型
     */
    private String type;

    /**
     * ddl内容
     */
    private String ddl;



    /**
     * 状态
     */
    private String status;

    /**
     * 操作行数
     */
    private Long operationRows;

    /**
     * 使用时长
     */
    private Long useTime;

    /**
     * 扩展信息
     */
    private String extendInfo;

    /**
     * SQL类型（由扩展信息解析，不对应数据库列）
     */
    private String sqlType;

    /**
     * 失败信息（由扩展信息解析，不对应数据库列）
     */
    private String errorMessage;

    /**
     * schema名称
     */
    private String schemaName;
}
