package com.jd.plugin.dm.type;

import com.jd.spi.enums.EditStatus;
import com.jd.spi.model.IndexType;
import com.jd.spi.model.TableIndex;
import com.jd.spi.model.TableIndexColumn;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum DMIndexTypeEnum {

    PRIMARY_KEY("Primary", "PRIMARY KEY"),

    NORMAL("Normal", "INDEX"),

    UNIQUE("Unique", "UNIQUE INDEX"),

    BITMAP("BITMAP", "BITMAP INDEX"),

    CHECK("CHECK", "CHECK"),

    /**
     * 外键
     */
    VIRTUAL("VIRTUAL", "VIRTUAL INDEX");



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
            if ("PRIMARY_KEY".equals(tableIndex.getName())) {
                script.append("ALTER TABLE \"").append(tableIndex.getSchemaName()).append("\".\"").append(tableIndex.getTableName()).append("\" ADD PRIMARY KEY ").append(buildIndexColumn(tableIndex));
            } else {
                script.append("ALTER TABLE \"").append(tableIndex.getSchemaName()).append("\".\"").append(tableIndex.getTableName()).append("\" ADD CONSTRAINT \"").append(tableIndex.getName()).append("\" PRIMARY KEY ").append(buildIndexColumn(tableIndex));
            }
        } else {
            if (UNIQUE.equals(this)) {
                script.append("CREATE UNIQUE INDEX ");
            } else {
                script.append("CREATE INDEX ");
            }
            script.append(buildIndexName(tableIndex)).append(" ON \"").append(tableIndex.getSchemaName()).append("\".\"").append(tableIndex.getTableName()).append("\" ").append(buildIndexColumn(tableIndex));
        }
        return script.toString();
    }


    private String buildIndexColumn(TableIndex tableIndex) {
        StringBuilder script = new StringBuilder();
        script.append("(");
        List<TableIndexColumn> collect = new ArrayList<>(tableIndex.getColumnList().stream().collect(Collectors.toMap(TableIndexColumn::getColumnName, Function.identity(), (a, b) -> a)).values());
        for (TableIndexColumn column : collect) {
            if (StringUtils.isNotBlank(column.getColumnName())) {
                script.append("\"").append(column.getColumnName()).append("\"");
                if (!StringUtils.isBlank(column.getAscOrDesc()) && !PRIMARY_KEY.equals(this)) {
                    script.append(" ").append(column.getAscOrDesc());
                }
                script.append(",");
            }
        }
        script.deleteCharAt(script.length() - 1);
        script.append(")");
        return script.toString();
    }

    private String buildIndexName(TableIndex tableIndex) {
        return "\"" + tableIndex.getSchemaName() + "\"." + "\"" + tableIndex.getName() + "\"";
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
                String tableName = "\"" + tableIndex.getSchemaName() + "\"." + "\"" + tableIndex.getTableName() + "\"";
                return StringUtils.join("ALTER TABLE ",tableName," DROP CONSTRAINT \"" + constraintName + "\";");
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
