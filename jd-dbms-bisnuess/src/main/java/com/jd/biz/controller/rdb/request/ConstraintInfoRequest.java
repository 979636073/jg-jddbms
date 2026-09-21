package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class ConstraintInfoRequest implements DataSourceBaseRequestInfo  {

    @NotNull
    private Long dataSourceId;
    /**
     * DB名称
     */
    private String databaseName;

    /**
     * 旧数据
     */
    private TypeQueryRequest oldData;

    /**
     * 新数据
     */
    private TypeQueryRequest newData;

}
