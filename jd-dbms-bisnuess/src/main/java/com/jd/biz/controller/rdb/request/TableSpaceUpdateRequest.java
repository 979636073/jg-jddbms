package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class TableSpaceUpdateRequest implements DataSourceBaseRequestInfo {

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

    private TableSpaceCreateRequest newTableSpace;


    private TableSpaceCreateRequest oldTableSpace;

}
