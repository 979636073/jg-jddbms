package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.spi.model.TableColumn;
import lombok.Data;

import java.util.List;

/**
 * @author moji
 * @version ConnectionQueryRequest.java, v 0.1 2022年09月16日 14:23 moji Exp $
 * @date 2022/09/16
 */
@Data
public class ViewNewRequest extends DataSourceBaseRequest {

    /**
     * 表名称
     */
    private String name;

    /**
     * viewSql
     */
    private String viewSql;

    /**
     * 列
     */
    private List<TableColumn> columnList;

    private List<String> viewNames;

    /**
     * 视图类型：VIEW 或 MATERIALIZED VIEW
     */
    private String viewType;

}
