package com.jd.biz.controller.rdb.request;


import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import com.jd.common.tools.base.wrapper.request.PageQueryRequest;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class TableSpaceCreateRequest extends PageQueryRequest implements DataSourceBaseRequestInfo {

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
     * 空间路径
     */
    private String path;
    /**
     * 表空间名称
     */
    private String tableSpace;
    /**
     * 表空间大小
     */
    private String totalSize;

    /**
     * 表空间大小单位
     */
    private String sizeUnit;

    /**
     * 自增步长大小
     */
    private String autoSize;

    /**
     * 自增步长大小单位
     */
    private String autoSizeUnit;
    /**
     * 表空间最大大小
     */
    private String expandUpperLimit;

    /**
     * 表空间最大大小单位
     */
    private String maxSizeUnit;

    private Boolean isUpdate;

    private String dbType;

    private List<String> tableSpaceNames;
}
