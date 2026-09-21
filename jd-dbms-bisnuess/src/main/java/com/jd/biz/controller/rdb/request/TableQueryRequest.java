
package com.jd.biz.controller.rdb.request;

import com.jd.common.tools.base.wrapper.request.PageQueryRequest;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import javax.validation.constraints.NotNull;
import lombok.Data;



/**
 * @author jipengfei
 * @version : TableColumnQueryRequest.java
 */
@Data
public class TableQueryRequest extends PageQueryRequest implements DataSourceBaseRequestInfo {

    
    private static final long serialVersionUID = 5794716286491282784L;

    /**
     * 数据源id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * DB名称
     */
    @NotNull
    private String databaseName;

    /**
     * 表名
     */
    private String tableName;
}