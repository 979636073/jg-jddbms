package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class GrantDetailRequest implements DataSourceBaseRequestInfo {
    /**
     * 数据源id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * DB名称
     */
    private String databaseName;

    /**
     * 表名称
     */
    @NotBlank(message = "表名不能为空")
    private String tableName;

    /**
     * 被授权用户
     */
    @NotBlank(message = "被授权用户不能为空")
    private String toGrantUser;
    /**
     * 表所在空间，pg,oracle需要，mysql不需要
     */
    private String schemaName;

    /**
     * if true, refresh the cache
     */
    private boolean refresh;

    private Boolean insert;
    private Boolean update;
    private Boolean delete;
}
