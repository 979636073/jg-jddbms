package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NewTableSqlRequest extends DataSourceBaseRequest {

    /**
     * 新的表结构
     */
    @NotNull
    private TableRequest newTable;

}
