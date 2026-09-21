package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.rdb.vo.ExcelDataHighVO;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * Excel表数据导入
 *
 * @author 是仪
 */
@Data
public class ExcelDataRequest extends DataSourceBaseRequest {


    /**
     * 表名称
     */
    private String name;

    private List<String> columns;

    /**
     * 视图导入or sql导入
     */
    private Boolean isSql = Boolean.FALSE;

    private String fileName;
    /**
     * 类型 1 新增 2 修改
     */
    private Integer type;

    /**
     * 需要新增的数据
     */
    private List<String> indexList;


    /**
     * 需要新增的数据
     */
    private List<Map<String,Object>> insertDataList;

    /**
     * 需要更新的数据
     */
    private List<Map<String, Object>> updateDataList;

    /**
     * 新增失败的数据
     */
    private List<Map<String,Object>> insertDataErrorList;

    /**
     * 更新失败的数据
     */
    private List<Map<String,Object>> updateDataErrorList;

    private String engine;


    private String charset;


    private String collate;

    private Long incrementValue;

    private String partition;

    /**
     * 出错是否执行
     */
    private Boolean isErrorExecute =  false;

}
