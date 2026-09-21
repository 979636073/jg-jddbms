package com.jd.biz.controller.sql.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class FilterSqlFormatRequest  extends DataSourceBaseRequest {



    /**
     * 列名
     */
    @NotNull
    private String columnName;


    /**
     * 过滤类型     =、!=、<>
     */
    @NotNull
    private String filterType;


    /**
     * 在between过滤类型中为起始值，在其他过滤类型中为选中值
     */
    @NotNull
    private String startValue;

    /**
     * 在between过滤类型中为结束值
     */
    private String endValue;



}
