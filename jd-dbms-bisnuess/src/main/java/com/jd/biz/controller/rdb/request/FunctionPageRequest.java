package com.jd.biz.controller.rdb.request;

import com.jd.common.tools.base.wrapper.request.PageQueryRequest;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import javax.validation.constraints.NotNull;
import lombok.Data;



@Data
public class FunctionPageRequest extends PageQueryRequest implements DataSourceBaseRequestInfo {

    
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
     * 模糊搜索词
     */
    private String searchKey;

    /**
     * function name
     */
    private String functionName;

    /**
     * if true, refresh the cache
     */
    private boolean refresh;
}
