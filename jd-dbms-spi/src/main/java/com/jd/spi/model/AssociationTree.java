package com.jd.spi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AssociationTree implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private String id;
    /**
     * 字段
     */
    private String column;
    /**
     * 关联对象对应的外键列
     */
    private String foreignColumnName;
    /**
     * 外键约束名
     */
    private String constraintName;
    /**
     * 类型
     */
    private String type;
    /**
     * 模式（库）
     */
    private String schemaName;
    /**
     * 表名
     */
    private String tableName;
    /**
     * 状态
     */
    private String status;
    /**
     * 树状结构信息
     */
    private List<AssociationTree> children;

}
