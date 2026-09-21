package com.jd.biz.domain.api.param;

import com.jd.spi.model.Header;
import com.jd.spi.model.ResultOperation;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class UpdateSelectResultParam {
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
     * 展示头的列表
     */
    @NotEmpty
    private List<Header> headerList;


    /**
     * 修改后数据的列表
     */
    @NotEmpty
    private List<ResultOperation> operations;


    /**
     * 表名
     */
    @NotEmpty
    private String tableName;


    /**
     * 是否是视图新增
     */
    private Boolean isView;
}
