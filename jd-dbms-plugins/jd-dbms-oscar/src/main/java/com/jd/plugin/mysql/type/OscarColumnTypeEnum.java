package com.jd.plugin.mysql.type;

import com.jd.spi.ColumnBuilder;
import com.jd.spi.enums.EditStatus;
import com.jd.spi.model.ColumnType;
import com.jd.spi.model.TableColumn;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public enum OscarColumnTypeEnum implements ColumnBuilder {

    BIT("BIT", true, false, true, false, false, false, true, true, false, false),
    OID("OID", false, false, true, false, false, false, true, true, false, false),
    TINYINT("TINYINT", false, false, true, true, false, false, true, true, false, false),

    SMALLINT("SMALLINT", false, false, true, true, false, false, true, true, false, false),

    INT("INT", false, false, true, true, false, false, true, true, false, false),

    BIGINT("BIGINT", false, false, true, true, false, false, true, true, false, false),

    NUMERIC("NUMERIC", true, true, true, false, false, false, true, true, false, false),

    FLOAT("FLOAT", true, true, true, false, false, false, true, true, false, false),
    DATE("DATE", false, false, true, false, false, false, true, true, false, false),
    TIMESTAMP("TIMESTAMP", false, false, true, false, false, false, true, true, true, false),
    TIME("TIME", false, false, true, false, false, false, true, true, false, false),
    BPCHAR("BPCHAR", true, false, true, false, true, true, true, true, false, false),

    VARCHAR("VARCHAR", true, false, true, false, true, false, true, true, false, false),

    BINARY("BINARY", true, false, true, false, false, false, true, true, false, false),

    VARBINARY("VARBINARY", true, false, true, false, false, false, true, true, false, false),

    BLOB("BLOB", false, false, true, false, false, false, true, false, false, false),

    TEXT("TEXT", false, false, true, false, true, true, true, false, false, false),

    BOOLEAN("BOOLEAN", false, false, true, true, false, false, true, true, false, false),

    REAL("REAL", true, true, true, false, false, false, true, true, false, false),

    JSON("JSON", false, false, true, false, false, false, true, false, false, false);

    private ColumnType columnType;

    public static OscarColumnTypeEnum getByType(String dataType) {
        return COLUMN_TYPE_MAP.get(dataType.toUpperCase());
    }

    public ColumnType getColumnType() {
        return columnType;
    }


    OscarColumnTypeEnum(String dataTypeName, boolean supportLength, boolean supportScale, boolean supportNullable, boolean supportAutoIncrement, boolean supportBPCHARset, boolean supportCollation, boolean supportComments, boolean supportDefaultValue, boolean supportExtent, boolean supportValue) {
        this.columnType = new ColumnType(dataTypeName, supportLength, supportScale, supportNullable, supportAutoIncrement, supportBPCHARset, supportCollation, supportComments, supportDefaultValue, supportExtent, supportValue, false);
    }

    private static Map<String, OscarColumnTypeEnum> COLUMN_TYPE_MAP = Maps.newHashMap();

    static {
        for (OscarColumnTypeEnum value : OscarColumnTypeEnum.values()) {
            COLUMN_TYPE_MAP.put(value.getColumnType().getTypeName(), value);
        }
    }


    @Override
    public String buildCreateColumnSql(TableColumn column) {
        OscarColumnTypeEnum type = COLUMN_TYPE_MAP.get(column.getColumnType().toUpperCase());
        if (type == null) {
            return "";
        }
        StringBuilder script = new StringBuilder();

        script.append("\"").append(column.getName()).append("\"").append(" ");

        script.append(buildDataType(column, type)).append(" ");

        script.append(buildDefaultValue(column, type)).append(" ");

        return script.toString();
    }

    private String buildBPCHARset(TableColumn column, OscarColumnTypeEnum type) {
        if (!type.getColumnType().isSupportCharset() || StringUtils.isEmpty(column.getCharSetName())) {
            return "";
        }
        return StringUtils.join("VARCHAR SET ", column.getCharSetName());
    }

    private String buildCollation(TableColumn column, OscarColumnTypeEnum type) {
        if (!type.getColumnType().isSupportCollation() || StringUtils.isEmpty(column.getCollationName())) {
            return "";
        }
        return StringUtils.join("COLLATE ", column.getCollationName());
    }

    @Override
    public String buildModifyColumn(TableColumn tableColumn) {

        if (EditStatus.DELETE.name().equals(tableColumn.getEditStatus())) {
            return StringUtils.join("DROP COLUMN ", tableColumn.getName() + "");
        }
        if (EditStatus.ADD.name().equals(tableColumn.getEditStatus())) {
            return StringUtils.join(" ALTER TABLE ","\"",
                    tableColumn.getSchemaName(),"\".\"",tableColumn.getTableName(),"\""," ADD COLUMN ", buildCreateColumnSql(tableColumn));
        }
        if (EditStatus.MODIFY.name().equals(tableColumn.getEditStatus())) {
            StringBuilder script = new StringBuilder();
            if (!StringUtils.equals(tableColumn.getOldName(), tableColumn.getName())) {
                script.append(" ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"")
                        .append(tableColumn.getTableName()).append("\"").append(" RENAME COLUMN ").append("\"")
                        .append(tableColumn.getOldColumn().getName()).append("\"").append(" TO ").append("\"")
                        .append(tableColumn.getName()).append("\"").append(";").append("\n");

            }
            script.append(" ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"")
                    .append(tableColumn.getTableName()).append("\"").append(" ").append("MODIFY (")
                    .append(buildCreateColumnSql(tableColumn)).append("); \n");
            OscarColumnTypeEnum type = COLUMN_TYPE_MAP.get(tableColumn.getColumnType().toUpperCase());
            script.append(buildNullable(tableColumn, type)).append(" ");


            if (StringUtils.isNotBlank(tableColumn.getComment())) {
                script.append("\n").append(buildComment(tableColumn)).append(";\n");
            }

            return script.toString();
        }
        return "";
    }

    private String buildAutoIncrement(TableColumn column, OscarColumnTypeEnum type) {
        if (!type.getColumnType().isSupportAutoIncrement()) {
            return "";
        }
        if (column.getAutoIncrement() != null && column.getAutoIncrement()) {
            return "AUTO_INCREMENT";
        }
        return "";
    }

    private String buildComment(TableColumn column) {
        StringBuilder script = new StringBuilder();
        script.append("COMMENT ON COLUMN ").append(column.getSchemaName()).append(".").append(column.getTableName()).append(".").append("\"").append(column.getName()).append("\"").append(" IS'").append(column.getComment()).append("'");
        return script.toString();
    }

    private String buildComment(TableColumn column, OscarColumnTypeEnum type) {
        if (!type.columnType.isSupportComments() || StringUtils.isEmpty(column.getComment())) {
            return "";
        }
        return StringUtils.join("COMMENT '", column.getComment(), "'");
    }

    private String buildExt(TableColumn column, OscarColumnTypeEnum type) {
        if (!type.columnType.isSupportExtent() || StringUtils.isEmpty(column.getExtent())) {
            return "";
        }
        return column.getComment();
    }

    private String buildDefaultValue(TableColumn column, OscarColumnTypeEnum type) {
        if (!type.getColumnType().isSupportDefaultValue() || StringUtils.isEmpty(column.getDefaultValue())) {
            return "";
        }

        if ("EMPTY_STRING".equalsIgnoreCase(column.getDefaultValue().trim())) {
            return StringUtils.join("DEFAULT ''");
        }

        if ("NULL".equalsIgnoreCase(column.getDefaultValue().trim())) {
            return StringUtils.join("DEFAULT NULL");
        }

        if (Arrays.asList(BPCHAR, VARCHAR, BINARY, VARBINARY).contains(type)) {
            return StringUtils.join("DEFAULT '", column.getDefaultValue(), "'");
        }

        if (Arrays.asList(DATE, TIME).contains(type)) {
            return StringUtils.join("DEFAULT '", column.getDefaultValue(), "'");
        }

        if (Arrays.asList( TIMESTAMP).contains(type)) {
            if ("CURRENT_TIMESTAMP".equalsIgnoreCase(column.getDefaultValue().trim())) {
                return StringUtils.join("DEFAULT ", column.getDefaultValue());
            }
            return StringUtils.join("DEFAULT '", column.getDefaultValue(), "'");
        }

        return StringUtils.join("DEFAULT ", column.getDefaultValue());
    }

    private String buildNullable(TableColumn column, OscarColumnTypeEnum type) {
        if (!type.getColumnType().isSupportNullable()) {
            return "";
        }
        StringBuilder script = new StringBuilder();
        script.append("ALTER TABLE ").append(column.getSchemaName()).append(".").append(column.getTableName()).append(" ");
        if (column.getNullable() != null && 1 == column.getNullable()) {

            return script.append(" ALTER COLUMN ").append("\"").append(column.getName()).append("\"").append(" ").append(" SET NOT NULL; ").toString();
        } else {
            return script.append(" ALTER COLUMN ").append("\"").append(column.getName()).append("\"").append(" ").append(" DROP NOT NULL ;").toString();
        }
    }

    private String buildDataType(TableColumn column, OscarColumnTypeEnum type) {
        String columnType = type.columnType.getTypeName();
        if (Arrays.asList(BINARY, VARBINARY, VARCHAR, BPCHAR, FLOAT).contains(type)) {
            return StringUtils.join(columnType, "(", column.getColumnSize(), ")");
        }

        if (Arrays.asList(BIT).contains(type)) {
            return StringUtils.join(columnType, "(", column.getColumnSize(), ")");
        }
        if (Arrays.asList(NUMERIC).contains(type)) {
            if (column.getColumnSize() == null || column.getDecimalDigits() == null) {
                return columnType;
            }
            if (column.getColumnSize() != null && column.getDecimalDigits() == null) {
                return StringUtils.join(columnType, "(", column.getColumnSize() + ")");
            }
            if (column.getColumnSize() != null && column.getDecimalDigits() != null) {
                return StringUtils.join(columnType, "(", column.getColumnSize() + "," + column.getDecimalDigits() + ")");
            }
        }
        return columnType;
    }

    public String buildColumn(TableColumn column) {
        OscarColumnTypeEnum type = COLUMN_TYPE_MAP.get(column.getColumnType().toUpperCase());
        if (type == null) {
            return "";
        }
        StringBuilder script = new StringBuilder();

        script.append("").append(column.getName()).append("").append(" ");
        script.append(buildDataType(column, type)).append(" ");
        return script.toString();
    }

    private String unsignedDataType(String dataTypeName, String middle) {
        String[] split = dataTypeName.split(" ");
        if (split.length == 2) {
            return StringUtils.join(split[0], middle, split[1]);
        }
        return StringUtils.join(dataTypeName, middle);
    }

    public static List<ColumnType> getTypes() {
        return Arrays.stream(OscarColumnTypeEnum.values()).map(columnTypeEnum ->
                columnTypeEnum.getColumnType()
        ).collect(Collectors.toList());
    }


}
