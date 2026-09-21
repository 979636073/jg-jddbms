
package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.Data;

/**
 * @author jipengfei
 * @version : UpdateDatasourceRequest.java
 */
@Data
public class UpdateDatabaseRequest extends DataSourceBaseRequest {

    private String newDatabaseName;
}