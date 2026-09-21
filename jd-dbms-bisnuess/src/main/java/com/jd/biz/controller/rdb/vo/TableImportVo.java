package com.jd.biz.controller.rdb.vo;

import com.google.common.collect.Lists;
import com.jd.biz.domain.api.enums.TaskStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableImportVo implements Serializable {
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


    public TableImportVo(Long dataSourceId, String databaseName, String schemaName, String status, int exported, int errorNum, Long userId,List<String> message) {
        this.dataSourceId = dataSourceId;
        this.databaseName = databaseName;
        this.schemaName = schemaName;
        this.status = status;
        this.exported = exported;
        this.errorNum = errorNum;
        this.userId = userId;
        this.message = message;
    }
}
