package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.spi.model.TableColumn;
import lombok.Data;

import javax.validation.constraints.NotNull;
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
    @NotNull
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

}
