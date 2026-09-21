package com.jd.plugin.gbase.type;

import com.jd.spi.enums.EditStatus;
import com.jd.spi.model.IndexType;
import com.jd.spi.model.TableIndex;
import com.jd.spi.model.TableIndexColumn;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

public enum GbaseIndexTypeEnum {

    PRIMARY_KEY("Primary", "PRIMARY KEY"),

    NORMAL("Normal", "INDEX"),

    FULLTEXT("Fulltext", "FULLTEXT INDEX"),

    SPATIAL("Spatial", "SPATIAL INDEX");

    public String getName() {
        return name;
    }

    private String name;


    public String getKeyword() {
        return keyword;
    }

    private String keyword;

    public IndexType getIndexType() {
        return indexType;
    }

    public void setIndexType(IndexType indexType) {
        this.indexType = indexType;
    }

    private IndexType indexType;

    GbaseIndexTypeEnum(String name, String keyword) {
        this.name = name;
        this.keyword = keyword;
        this.indexType = new IndexType(name);
    }


    public static GbaseIndexTypeEnum getByType(String type) {
        for (GbaseIndexTypeEnum value : GbaseIndexTypeEnum.values()) {
            if (value.name.equalsIgnoreCase(type)) {
                return value;
            }
        }
        return null;
    }

    public String buildIndexScript(TableIndex tableIndex) {
        StringBuilder script = new StringBuilder();

        script.append(keyword).append(" ");

        script.append(buildIndexName(tableIndex)).append(" ");
//        script.append(" on ").append("`")
//                .append(tableIndex.getDatabaseName()).append("`").append(".")
//                .append("`").append(tableIndex.getTableName()).append("`").append(" ");
        script.append(buildIndexColumn(tableIndex)).append(" ");

        script.append(buildIndexComment(tableIndex)).append(" ");

        return script.toString();
    }

    private String buildIndexComment(TableIndex tableIndex) {
        if (StringUtils.isBlank(tableIndex.getComment())) {
            return "";
        } else {
            return StringUtils.join(" COMMENT '", tableIndex.getComment(), "'");
        }

    }

    private String buildIndexColumn(TableIndex tableIndex) {
        StringBuilder script = new StringBuilder();
        script.append("(");
        for (TableIndexColumn column : tableIndex.getColumnList()) {
            if (StringUtils.isNotBlank(column.getColumnName())) {
                script.append("`").append(column.getColumnName()).append("`");
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
        if (this.equals(PRIMARY_KEY)) {
            return "";
        } else {
            return "`" + tableIndex.getName() + "`";
        }
    }

    public String buildModifyIndex(TableIndex tableIndex) {
        if (EditStatus.DELETE.name().equals(tableIndex.getEditStatus())) {
            return buildDropIndex(tableIndex);
        }
        if (EditStatus.MODIFY.name().equals(tableIndex.getEditStatus())) {
            return StringUtils.join(buildDropIndex(tableIndex), ",\n", "CREATE ", buildIndexScript(tableIndex));
        }
        if (EditStatus.ADD.name().equals(tableIndex.getEditStatus())) {
            return StringUtils.join(" ADD  ", buildIndexScript(tableIndex));
        }
        return "";
    }

    private String buildDropIndex(TableIndex tableIndex) {
        if (GbaseIndexTypeEnum.PRIMARY_KEY.getName().equals(tableIndex.getType())) {
            return StringUtils.join("DROP PRIMARY KEY");
        }
        return StringUtils.join("DROP INDEX `", tableIndex.getOldName(),"`");
    }

    public static List<IndexType> getIndexTypes() {
        return Arrays.asList(GbaseIndexTypeEnum.values()).stream().map(GbaseIndexTypeEnum::getIndexType).collect(java.util.stream.Collectors.toList());
    }
}
