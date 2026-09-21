
package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.Data;

/**
 * @author jipengfei
 * @version : UpdateSchemaRequest.java
 */
@Data
public class UpdateSchemaRequest extends DataSourceBaseRequest {

    private String newSchemaName;

}