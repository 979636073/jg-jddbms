package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class DmlSqlCopyRequest extends DataSourceBaseRequest {

    @NotNull
    private String tableName;

    private String type;
}
