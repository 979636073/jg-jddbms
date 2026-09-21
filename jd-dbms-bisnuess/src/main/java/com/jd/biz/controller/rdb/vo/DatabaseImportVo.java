package com.jd.biz.controller.rdb.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DatabaseImportVo implements Serializable {
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
     * 异常总数
     */
    private int errorNum;

    /**
     * 执行消息
     */
    private List<String> message;

    /**
     * 总数
     */
    private int total;
    /**
     * 执行日志路径
     */
    private String logFile;

    private Long userId;
}
