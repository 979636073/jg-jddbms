package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;



@Data
public class ProcedureDetailRequest implements DataSourceBaseRequestInfo {

    
    private static final long serialVersionUID = -364547173428396332L;
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
     * 表所在空间，pg,oracle需要，mysql不需要
     */
    private String schemaName;

    /**
     * procedure name
     */
    @NotBlank
    private String procedureName;

    /**
     * if true, refresh the cache
     */
    private boolean refresh;
}
