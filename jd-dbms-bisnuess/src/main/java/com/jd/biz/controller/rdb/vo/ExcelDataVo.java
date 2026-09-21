package com.jd.biz.controller.rdb.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ExcelDataVo {
    /**
     * 表名称
     */
    private String name;

    /**
     * 模式名称
     */
    private String schema;

    /**
     * 需要新增的数据
     */
    private List<Map<String,Object>> insertDataList;

    private Integer addSuccess;

    private Integer updateSuccess;

    private Integer addFail;

    private Integer updateFail;


    /**
     * 需要更新的数据
     */
    private List<Map<String,Object>> updateDataList;
}
