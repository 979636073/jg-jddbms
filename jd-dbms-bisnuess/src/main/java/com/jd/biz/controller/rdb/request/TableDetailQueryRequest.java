package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author moji
 * @version ConnectionQueryRequest.java, v 0.1 2022年09月16日 14:23 moji Exp $
 * @date 2022/09/16
 */
@Data
public class TableDetailQueryRequest extends DataSourceBaseRequest {

    /**
     * 表名称
     */
//    @NotNull
    private String tableName;

    private String copySchemaName;

    /**
     * viewSql
     */
    private String viewSql;


    private Boolean isData = Boolean.FALSE;


    private Boolean isRefreshCache = Boolean.FALSE;
}
