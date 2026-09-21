package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.domain.api.enums.ExportSizeEnum;
import com.jd.biz.domain.api.enums.ExportTypeEnum;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * @author moji
 * @version ConnectionQueryRequest.java, v 0.1 2022年09月16日 14:23 moji Exp $
 * @date 2022/09/16
 */
@Data
public class TableDataExportRequest extends DataSourceBaseRequest {

    /**
     * export type
     *
     * @see ExportTypeEnum   导出类型（csv,excel等）
     */
    @NotNull
    private String exportType;

    /**
     * How much data is currently needed at the beginning
     *
     * @see ExportSizeEnum  导出大小（整表、分页）
     */
    @NotNull
    private String exportSize;


    private String tableName;


    /**
     * 导出表名称
     */
    @NotNull
    private String exportTableName;

    /**
     * 条件中的列名
     */
    private String columnName;

    /**
     * 导出idlist（用于框选导出）
     */
    private List<String> exportIdList;



    /**
     * 导出Datalist（用于框选导出）
     */
    private List<Map<String, String>> dataList;

    /**
     * 过滤器中的条件信息
     */
    private String filterSql;

    /**
     * 排序
     */
    private Boolean isDesc;

    /**
     * 排序字段
     */
    private String filterColumn;

    /**
     * 过滤器中的条件信息
     */
    private String sql;


    /**
     * 当前页码，用于导出分页数据
     */
    private Integer pageNum;

    /**
     * 页面大小，用于导出分页数据
     */
    private Integer pageSize;

    /**
     * 主键数据的二维数组参数
     */
    private List<Map<String,Object>> exportPrimaryKeyList;









}
