package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TypeQueryRequest implements DataSourceBaseRequestInfo {

    @NotNull
    private Long dataSourceId;
    /**
     * DB名称
     */
    private String databaseName;

    private String tableName;

    private String schemaName;
    /**
     * 约束名称
     */
    private String constraintName;

    /**
     * 字段名称
     */
    private String columnName;

    /**
     * 约束名称
     */
    private String constraintType;

    /**
     * 关联的模式
     */
    private String forkSchema;

    /**
     * 关联表
     */
    private String forkTable;

    /**
     * 外键字段名称
     */
    private String forkColumn;

    /**
     * 检查规则
     */
    private String check;

    /**
     * index名称
     */
    private String name;

    /**
     * 键名称
     */
    private String keyName;

    /**
     * 修改
     */
    private Boolean isUpdate;

    /**
     * 是否生效
     */
    private Boolean enabled = true;

    private Boolean isRefreshCache = Boolean.FALSE;
}
