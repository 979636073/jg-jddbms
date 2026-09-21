package com.jd.biz.domain.api.param;

import com.jd.common.tools.base.wrapper.param.PageQueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

/**
 * 分页查询表信息
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TablePageQueryParam extends PageQueryParam {
    private static final long serialVersionUID = 8054519332890887747L;
    /**
     * 对应数据库存储的来源id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * 对应的连接数据库名称
     */
    @NotNull
    private String databaseName;

    /**
     * 表名
     */
    private String tableName;


    /**
     *
     */
    private String schemaName;



    /**
     * if true, refresh the cache
     */
    private boolean refresh;


    private Boolean isRefreshCache = Boolean.FALSE;


    private String searchKey;

    /**
     * 返回的结果集类型 1：表列表  2：表列表 + 表列表详情
     */
    private Integer requestType;
}
