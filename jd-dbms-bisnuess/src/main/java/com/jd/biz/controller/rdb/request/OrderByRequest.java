package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import com.jd.spi.model.OrderBy;
import lombok.Data;

import java.util.List;

@Data
public class OrderByRequest extends DataSourceBaseRequest implements DataSourceBaseRequestInfo {

    /**
     * origin sql
     */
    private String originSql;

    /**
     * 排序字段
     */
    private List<OrderBy> orderByList;

}
