package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.rdb.vo.ColumnWHVO;
import lombok.Data;
import java.util.List;

@Data
public class TableColumnMappingRequest extends DataSourceBaseRequest {

    /**
     * 需映射的字段
     */
    private List<ColumnWHVO> columnWHVOS;


    /**
     * 需映射的字段
     */
    private List<ColumnWHVO> fromColumnWHVOS;


    /**
     * 目的表名
     */
    private String toTableName;

    private Boolean isRefresh;

    private Integer resultType;

}
