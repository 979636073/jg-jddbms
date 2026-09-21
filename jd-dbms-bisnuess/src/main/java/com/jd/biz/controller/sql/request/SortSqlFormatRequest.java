package com.jd.biz.controller.sql.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SortSqlFormatRequest  extends DataSourceBaseRequest {




    /**
     * 列名
     */
    @NotNull
    private String columnName;

    /**
     * 排序方式    asc   desc
     */
    @NotNull
    private String sorType;

    /**
     * 空置排序方式
     * nulls first
     * nulls last
     * 空
     */
    private String nullType;

}
