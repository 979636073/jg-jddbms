package com.jd.spi.model;

import lombok.Data;

@Data
public class ViewReferencedData {

    /**
     * 视图所在模式
     */
    private String schemaName;
    /**
     * 视图名称
     */
    private String viewName;
    /**
     * 视图类型
     */
    private String viewType;

    /**
     * 视图状态
     */
    private String status;
    /**
     * 被依赖视图所在模式
     */
    private String referencedSchemaName;
    /**
     * 被依赖视图名称
     */
    private String referencedViewName;
    /**
     * 被依赖视图类型
     */
    private String referencedViewType;

}
