package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class TableCompareRequest implements DataSourceBaseRequestInfo {
    @NotNull
    private Long dataSourceId;
    private String databaseName;
    private String schemaName;
    @NotBlank
    private String sourceTableName;
    @NotBlank
    private String targetTableName;
}
