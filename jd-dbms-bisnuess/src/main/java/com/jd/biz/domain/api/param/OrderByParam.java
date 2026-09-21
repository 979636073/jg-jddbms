package com.jd.biz.domain.api.param;

import com.jd.spi.model.OrderBy;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;


@Data
public class OrderByParam {

    /**
     * 控制台id
     */
    @NotNull
    private Long consoleId;

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
     * schema名称
     */
    private String schemaName;


    /**
     * origin sql
     */
    private String originSql;


    /**
     * 排序字段
     */
    private List<OrderBy> orderByList;
}
