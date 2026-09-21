package com.jd.biz.domain.api.param;

import com.jd.common.tools.base.wrapper.param.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;


/**
 * 查询表信息
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TableQueryParam extends QueryParam {
    private static final long serialVersionUID = -8918610899872508804L;
    /**
     * 对应数据库存储的来源id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * 对应的连接数据库名称
     */
    @NotNull
    private String databaseName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 空间名
     */
    private String schemaName;

    /**
     * viewSql
     */
    private String viewSql;

    /**
     * 存储过程名称
     */
    private String procedureName;

    /**
     * 函数名称
     */
    private String functionName;

    /**
     * 触发器名称
     */
    private String triggerName;


    private Boolean insert;

    private Boolean update;

    private Boolean delete;

    private Boolean after;

    private Boolean before;

    private boolean refresh;

    private Boolean isView = false;


    private Boolean isRefreshCache = Boolean.FALSE;
}
