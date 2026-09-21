package com.jd.spi.model;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class Command implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * sql statement
     */
    @NotNull
    private String script;

    /**
     * console id
     */
    @NotNull
    private Long consoleId;

    /**
     * sessionId
     */
    private String sessionId;

    /**
     * isCommit
     */
    private Boolean isCommit = false;

    /**
     * 返回结果可以提交或回滚
     */
    private Boolean sign;

    /**
     * 关闭所有会话
     */
    private Boolean isAllDelete = false;

    /**
     * isRollback
     */
    private Boolean isRollback = false;

    /**
     * queryTemplate
     */
    private Boolean queryTemplate = false;

    /**
     * 出错是否执行
     */
    private Boolean isErrorExecute =  false;

    /**
     * Data source id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * DB name
     */
    @NotNull
    private String databaseName;

    /**
     * schema name
     */
    private String schemaName;

    /**
     *
     */
    private String tableName;

    /**
     *Page coding
      * Only available for select statements
     */
    private Integer pageNo;

    /**
     * Paging Size
      * Only available for select statements
     */
    private Integer pageSize;

    /**
     * Return all data
     * Only available for select statements
     */
    private Boolean pageSizeAll;

    /**
     * 是否跳过精确总数查询，适用于表数据预览。
     */
    private Boolean skipCount = false;

    /**
     * 排序升序降序 true升序
     */
    private Boolean isAsc;

    /**
     * 排序字段
     */
    private String orderByColumn;


    /**
     * 是否查数据列
     */
    private Boolean isColumn = true;

    /**
     * 是否执行过程编译
     */
    private Boolean isExecuteCompile = false;
}
