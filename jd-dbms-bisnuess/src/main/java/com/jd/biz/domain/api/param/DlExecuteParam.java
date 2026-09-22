package com.jd.biz.domain.api.param;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @author moji
 * @version DataSourceExecuteParam.java, v 0.1 2022年10月14日 13:53 moji Exp $
 * @date 2022/10/14
 */
@Data
public class DlExecuteParam {
    @NotNull
    private String tableName;

    /**
     * sql语句
     */
    @NotNull
    private String sql;

    /**
     * 控制台id
     */
    @NotNull
    private Long consoleId;

    /**
     * 数据源id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * sessionId
     */
    private String sessionId;

    /**
     * isCommit
     */
    private Boolean isCommit;

    private Boolean queryTemplate;

    /**
     * DB名称
     */
    @NotNull
    private String databaseName;


    /**
     * schema名称
     */
    private String schemaName;

    /**
     * 分页编码
     * 只有select语句才有
     */
    private Integer pageNo;

    /**
     * 出错是否执行
     */
    private Boolean isErrorExecute =  false;

    /**
     * 关闭所有会话
     */
    private Boolean isAllDelete = false;

    /**
     * 分页大小
     * 只有select语句才有
     */
    private Integer pageSize;

    /**
     * 本次执行的唯一标识，用于取消正在运行的 SQL。
     */
    private String executionId;

    /**
     * 查询超时秒数。
     */
    private Integer queryTimeoutSeconds;

    /**
     * 返回全部数据
     * 只有select语句才有
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

    private Boolean isLog;

    /**
     * 排序字段
     */
    private String orderByColumn;


    /**
     * 是否查数据列
     */
    private Boolean isColumn = true;

    /**
     * isRollback
     */
    private Boolean isRollback;

    /**
     * 是否执行过程编译
     */
    private Boolean isExecuteCompile = false;

}
