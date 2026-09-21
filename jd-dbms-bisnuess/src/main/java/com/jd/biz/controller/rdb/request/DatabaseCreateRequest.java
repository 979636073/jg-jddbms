package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.Data;

@Data
public class DatabaseCreateRequest extends DataSourceBaseRequest {

    private String name;

    private String comment;

    private String charset;

    private String collation;
}
