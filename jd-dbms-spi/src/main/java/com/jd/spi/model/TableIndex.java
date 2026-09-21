package com.jd.spi.model;

import java.io.Serializable;
import java.util.List;


import com.jd.spi.enums.IndexTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 索引信息
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TableIndex implements Serializable {
    private static final long serialVersionUID = 1L;

    private String oldName;

    /**
     * 索引名称
     */
    private String name;


    /**
     * 键名称
     */
    private String keyName;

    /**
     * 约束状态
     */
    private String status;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 索引类型
     *
     * @see IndexTypeEnum
     */
    private String type;

    /**
     * 是否唯一
     */
    private Boolean unique = false;

    /**
     * 注释
     */
    private String comment;

    /**
     * 索引所属schema
     */
    private String schemaName;

    /**
     * 数据库名
     */
    private String databaseName;

    /**
     * 索引包含的列
     */
    private List<TableIndexColumn> columnList;

    /**
     * 字段名称
     */
    private String column;

    /**
     * 是否非空
     */
    private Boolean isN = false;


    private String editStatus;

    /**
     * 是否并发
     */
    private Boolean concurrently;

    /**
     * 索引方法
     */
    private String method;

    /**
     * 约束内容
     */
    private String constraintsDesc;

    /**
     * 外键信息
     */
    private ForeignData foreign;

    /**
     * 外键指向的集合 1对多、 多对多
     */
    private List<ForeignData> foreignList;
    /**
     * 外键指向的列名
     */
    private List<String> foreignColumnNamelist;

//   KingBaseIndex 再用
    /**
     * 外键指向schema
     */
    private String foreignSchemaName;

    /**
     * 外键指向表名
     */
    private String foreignTableName;


    /**
     * check约束内容 大于等于
     */
    private String maxEqual;

    /**
     * check约束内容 大于
     */
    private String max;

    /**
     * check约束内容 小于等于
     */
    private String minEqual;

    /**
     * check约束内容 小于
     */
    private String min;
    /**
     * check约束结果
     */
    private List<String> checkData;

}
