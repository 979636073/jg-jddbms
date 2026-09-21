package com.jd.spi.model;

import lombok.Data;

@Data
public class ForeignData {

    /**
     * 外键指向schema
     */
    private String foreignSchemaName;

    private String kName;
    /**
     * 约束名
     */
    private String constraintName;

    /**
     * 外键指向表名
     */
    private String foreignTableName;

    /**
     * 外键指向表名
     */
    private String foreignColumnName;

    /**
     * 约束状态
     */
    private String status;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 索引所属schema
     */
    private String schemaName;

    /**
     * 字段名称
     */
    private String column;

}
