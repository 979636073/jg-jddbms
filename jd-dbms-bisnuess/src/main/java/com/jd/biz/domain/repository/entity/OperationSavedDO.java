package com.jd.biz.domain.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 我的保存表
 * </p>
 *
 * @author ali-dbhub
 * @since 2023-04-22
 */
@Getter
@Setter
@TableName("OPERATION_SAVED")
public class OperationSavedDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间
     */
    @TableField("GMT_CREATE")
    private LocalDateTime gmtCreate;

    /**
     * 修改时间
     */
    @TableField("GMT_MODIFIED")
    private LocalDateTime gmtModified;

    /**
     * 数据源连接ID
     */
    @TableField("DATA_SOURCE_ID")
    private Long dataSourceId;

    /**
     * db名称
     */
    @TableField("DATABASE_NAME")
    private String databaseName;

    /**
     * 保存名称
     */
    @TableField("NAME")
    private String name;

    /**
     * 数据库类型
     */
    @TableField("TYPE")
    private String type;

    /**
     * ddl语句状态:DRAFT/RELEASE
     */
    @TableField("STATUS")
    private String status;

    /**
     * ddl内容
     */
    @TableField("DDL")
    private String ddl;

    /**
     * 是否在tab中被打开,y表示打开,n表示未打开
     */
    @TableField("TAB_OPENED")
    private String tabOpened;

    /**
     * 用户id
     */
    @TableField("USER_ID")
    private Long userId;

    /**
     * schema名称
     */
    @TableField("DB_SCHEMA_NAME")
    private String dbSchemaName;

    /**
     * operation type
     */
    @TableField("OPERATION_TYPE")
    private String operationType;
}
