package com.jd.biz.controller.rdb.enums;

import com.jd.biz.domain.core.util.MetaNameUtils;
import com.jd.spi.enums.EditStatus;
import com.jd.spi.model.IndexType;
import com.jd.spi.model.TableIndex;
import com.jd.spi.model.TableIndexColumn;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

public enum DMIndexTypeEnum {

    PRIMARY_KEY("Primary", "PRIMARY KEY"),

    NORMAL("Normal", "INDEX"),

    UNIQUE("Unique", "UNIQUE INDEX"),

    BITMAP("BITMAP", "BITMAP INDEX"),

    CHECK("CHECK", "CHECK"),

    /**
     * 外键
     */
    VIRTUAL("VIRTUAL", "VIRTUAL INDEX"),

    /**
     * 外键显示使用
     */
    FOREIGN_KEY("FOREIGN_KEY", "FOREIGN_KEY INDEX");



    public IndexType getIndexType() {
        return indexType;
    }

    public void setIndexType(IndexType indexType) {
        this.indexType = indexType;
    }

    private IndexType indexType;


    public String getName() {
        return name;
    }

    private String name;


    public String getKeyword() {
        return keyword;
    }

    private String keyword;

    DMIndexTypeEnum(String name, String keyword) {
        this.name = name;
        this.keyword = keyword;
        this.indexType = new IndexType(name);
    }


    public static DMIndexTypeEnum getByType(String type) {
        for (DMIndexTypeEnum value : DMIndexTypeEnum.values()) {
            if (value.name.equalsIgnoreCase(type)) {
                return value;
            }
        }
        return null;
    }

    public String buildIndexScript(TableIndex tableIndex) {
        StringBuilder script = new StringBuilder();
        if (PRIMARY_KEY.equals(this)) {
            script.append("ALTER TABLE ").append(buildTableName(tableIndex)).append(" ADD PRIMARY KEY ").append(buildIndexColumn(tableIndex));
        } else {
            if (UNIQUE.equals(this)) {
                script.append("CREATE UNIQUE INDEX ");
            } else {
                script.append("CREATE INDEX ");
            }
            script.append(buildIndexName(tableIndex)).append(" ON ").append(buildTableName(tableIndex)).append(" ").append(buildIndexColumn(tableIndex));
        }
        return script.toString();
    }


    private String buildIndexColumn(TableIndex tableIndex) {
        StringBuilder script = new StringBuilder();
        script.append("(");
        for (TableIndexColumn column : tableIndex.getColumnList()) {
            if (StringUtils.isNotBlank(column.getColumnName())) {
                script.append(MetaNameUtils.quoteIdentifier(column.getColumnName()));
                if (!StringUtils.isBlank(column.getAscOrDesc()) && !PRIMARY_KEY.equals(this)) {
                    String order = column.getAscOrDesc();
                    if (!"ASC".equalsIgnoreCase(order) && !"DESC".equalsIgnoreCase(order)) {
                        throw new IllegalArgumentException("索引排序方向不合法");
                    }
                    script.append(" ").append(order.toUpperCase());
                }
                script.append(",");
            }
        }
        script.deleteCharAt(script.length() - 1);
        script.append(")");
        return script.toString();
    }

    private String buildIndexName(TableIndex tableIndex) {
        return MetaNameUtils.quoteIdentifier(tableIndex.getSchemaName()) + "." + MetaNameUtils.quoteIdentifier(tableIndex.getName());
    }

    private String buildTableName(TableIndex tableIndex) {
        return MetaNameUtils.quoteIdentifier(tableIndex.getSchemaName()) + "." + MetaNameUtils.quoteIdentifier(tableIndex.getTableName());
    }

    public String buildModifyIndex(TableIndex tableIndex,String constraintName) {
        if (EditStatus.DELETE.name().equals(tableIndex.getEditStatus())) {
            return buildDropIndex(tableIndex,constraintName);
        }
        if (EditStatus.MODIFY.name().equals(tableIndex.getEditStatus())) {
            return StringUtils.join(buildDropIndex(tableIndex,constraintName), ";\n", buildIndexScript(tableIndex));
        }
        if (EditStatus.ADD.name().equals(tableIndex.getEditStatus())) {
            return StringUtils.join(buildIndexScript(tableIndex));
        }
        return "";
    }

    private String buildDropIndex(TableIndex tableIndex,String constraintName) {
        if (DMIndexTypeEnum.PRIMARY_KEY.getName().equals(tableIndex.getType())) {
            if(StringUtils.isNotBlank(constraintName)) {
                return StringUtils.join("ALTER TABLE ", buildTableName(tableIndex), " DROP CONSTRAINT ", MetaNameUtils.quoteIdentifier(constraintName), ";");
            }

        }
        StringBuilder script = new StringBuilder();
        script.append("DROP INDEX ");
        script.append(buildIndexName(tableIndex));

        return script.toString();
    }

    public static List<IndexType> getIndexTypes() {
        return Arrays.asList(DMIndexTypeEnum.values()).stream().map(DMIndexTypeEnum::getIndexType).collect(java.util.stream.Collectors.toList());
    }
}
