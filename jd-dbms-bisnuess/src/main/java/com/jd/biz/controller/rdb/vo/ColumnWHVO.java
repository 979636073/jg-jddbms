package com.jd.biz.controller.rdb.vo;

import lombok.Data;

import java.util.List;

@Data
public class ColumnWHVO {

    /**
     * 序号
     */
    private Integer number;

    /**
     * 列名
     */
    private String name;

    /**
     * 列的类型
     * 比如 varchar(100) ,double(10,6)
     */

    private String columnType;

    /**
     * 索引类型
     */
    private String indexType;

    /**
     * 注视
     */
    private String comment;

    private List<ColumnWHVO> fromColumnWHVOList;

    private List<ColumnWHVO> toColumnWHVOList;
}
