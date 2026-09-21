package com.jd.plugin.oracle.type;

import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import com.jd.spi.ColumnBuilder;
import com.jd.spi.enums.EditStatus;
import com.jd.spi.model.ColumnType;
import com.jd.spi.model.TableColumn;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public enum OracleColumnTypeEnum implements ColumnBuilder {
    //JSON("JSON", false, false, true, false, false, false, true, false, false, false)

    BFILE("BFILE", false, false, true, false, false, false, true, true, false, false,""),

    BINARY_DOUBLE("BINARY_DOUBLE", false, false, true, false, false, false, true, true, false, false,""),


    BINARY_FLOAT("BINARY_FLOAT", false, false, true, false, false, false, true, true, false, false,""),


    BLOB("BLOB", false, false, true, false, false, false, true, true, false, false,""),
    CHAR("CHAR", true, false, true, false, false, false, true, true, false, true,""),
    CHAR_VARYING("CHAR VARYING", false, false, true, false, false, false, true, true, false, true,""),
    CHARACTER("CHARACTER", false, false, true, false, false, false, true, true, false, true,""),
    CHARACTER_VARYING("CHARACTER VARYING", false, false, true, false, false, false, true, true, false, true,""),
    CLOB("CLOB", false, false, true, false, false, false, true, true, false, false,""),
    DATE("DATE", false, false, true, false, false, false, true, true, false, false,""),
    DECIMAL("DECIMAL", false, true, true, false, false, false, true, true, false, false,""),
    DOUBLE_PRECISION("DOUBLE PRECISION", false, false, true, false, false, false, true, true, false, false,""),
    FLOAT("FLOAT", true, false, true, false, false, false, true, true, false, false,""),
    INT("INT", false, false, true, false, false, false, true, true, false, false,""),
    INTEGER("INTEGER", false, false, true, false, false, false, true, true, false, false,""),
    LONG("LONG", false, false, true, false, false, false, true, true, false, false,""),
    LONG_RAW("LONG RAW", false, false, true, false, false, false, true, true, false, false,""),
    NATIONAL_CHAR("NATIONAL CHAR", true, false, true, false, false, false, true, true, false, true,""),


    NATIONAL_CHAR_VARYING("NATIONAL CHAR VARYING", false, false, true, false, false, false, true, true, false, true,""),
    NATIONAL_CHARACTER("NATIONAL CHARACTER", false, false, true, false, false, false, true, true, false, true,""),
    NATIONAL_CHARACTER_VARYING("NATIONAL CHARACTER VARYING", false, false, true, false, false, false, true, true, false, true,""),
    NCHAR("NCHAR", false, false, true, false, false, false, true, true, false, false,""),
    MLSLABEL("MLSLABEL", false, false, true, false, false, false, true, true, false, false,""),
    URITYPE("URITYPE", false, false, true, false, false, false, true, true, false, false,""),
    NCHAR_VARYING("NCHAR VARYING", false, false, true, false, false, false, true, true, false, false,""),
    NCLOB("NCLOB", false, false, true, false, false, false, true, true, false, false,""),
    NUMBER("NUMBER", false, true, true, false, false, false, true, true, false, false,""),
    NVARCHAR2("NVARCHAR2", true, false, true, false, false, false, true, true, false, true,""),
    RAW("RAW", true, false, true, false, false, false, true, true, false, false,""),
    REAL("REAL", false, false, true, false, false, false, true, true, false, false,""),
    ROWID("ROWID", false, false, true, false, false, false, true, true, false, false,""),
    SMALLINT("SMALLINT", false, false, true, false, false, false, true, true, false, false,""),
    TIMESTAMP("TIMESTAMP", false, false, true, false, false, false, true, true, false, false,""),

    TIMESTAMP_WITH_LOCAL_TIME_ZONE("TIMESTAMP WITH LOCAL TIME ZONE", false, false, true, false, false, false, true, true, false, false,""),
    TIMESTAMP_WITH_TIME_ZONE("TIMESTAMP WITH TIME ZONE", false, false, true, false, false, false, true, true, false, false,""),
    UROWID("UROWID", false, false, true, false, false, false, true, true, false, false,""),
    VARCHAR("VARCHAR", true, false, true, false, false, false, true, true, false, true,"50"),
    VARCHAR2("VARCHAR2", true, false, true, false, false, false, true, true, false, true,"50"),
    TEXT("TEXT", false, false, true, false, false, false, true, true, false, true, ""),
    ;
    private ColumnType columnType;

    public static OracleColumnTypeEnum getByType(String dataType) {
        return COLUMN_TYPE_MAP.get(dataType.toUpperCase());
    }

    private static Map<String, OracleColumnTypeEnum> COLUMN_TYPE_MAP = Maps.newHashMap();

    static {
        for (OracleColumnTypeEnum value : OracleColumnTypeEnum.values()) {
            COLUMN_TYPE_MAP.put(value.getColumnType().getTypeName(), value);
        }
    }

    public ColumnType getColumnType() {
        return columnType;
    }


    OracleColumnTypeEnum(String dataTypeName, boolean supportLength, boolean supportScale, boolean supportNullable, boolean supportAutoIncrement, boolean supportCharset, boolean supportCollation, boolean supportComments, boolean supportDefaultValue, boolean supportExtent, boolean supportUnit, String defaultValue) {
        this.columnType = new ColumnType(dataTypeName, supportLength, supportScale, supportNullable, supportAutoIncrement, supportCharset, supportCollation, supportComments, supportDefaultValue, supportExtent, false, supportUnit, defaultValue);
    }

    @Override
    public String buildCreateColumnSql(TableColumn column) {
        OracleColumnTypeEnum type = COLUMN_TYPE_MAP.get(column.getColumnType().toUpperCase());
        if (type == null) {
            return "";
        }
        StringBuilder script = new StringBuilder();

        script.append("\"").append(column.getName()).append("\"").append(" ");

        script.append(buildDataType(column, type)).append(" ");
        String s = buildDefaultValue(column, type);
        script.append(s).append(" ");
        script.append(buildNullable(column, type)).append(" ");
        return script.toString();
    }


    private String buildNullable(TableColumn column, OracleColumnTypeEnum type) {
        if (!type.getColumnType().isSupportNullable()) {
            return "";
        }
        if (column.getNullable() != null && 0 == column.getNullable()) {
            return "NULL";
        } else {
            return "NOT NULL";
        }
    }

    private String buildDefaultValue(TableColumn column, OracleColumnTypeEnum type) {
        if (Objects.isNull(column.getOldColumn())) {
            if (!type.getColumnType().isSupportDefaultValue() || StringUtils.isEmpty(column.getDefaultValue())) {
                return "";
            }
        }
        if (Objects.nonNull(column.getOldColumn())) {
            if (Objects.isNull(column.getOldColumn().getDefaultValue())) {
                if (StrUtil.isEmpty(column.getDefaultValue())) {
                    return "";
                }
            }
        }
        if (!type.getColumnType().isSupportDefaultValue() || StringUtils.isEmpty(column.getDefaultValue())) {
            if (Objects.nonNull(column.getOldColumn()) && Objects.nonNull(column.getOldColumn().getDefaultValue())) {
                return " DEFAULT NULL";
            }
        }

        if ("EMPTY_STRING".equalsIgnoreCase(column.getDefaultValue().trim())) {
            return StringUtils.join("DEFAULT ''");
        }

        if ("NULL".equalsIgnoreCase(column.getDefaultValue().trim())) {
            return StringUtils.join("DEFAULT NULL");
        }

        return StringUtils.join("DEFAULT ", column.getDefaultValue());
    }

    private String buildDataType(TableColumn column, OracleColumnTypeEnum type) {
        String columnType = type.columnType.getTypeName();
        if (Arrays.asList(CHAR, CHAR_VARYING, CHARACTER, CHARACTER_VARYING,
                NVARCHAR2, VARCHAR, VARCHAR2, NATIONAL_CHAR,
                NATIONAL_CHAR_VARYING, NATIONAL_CHARACTER,
                NATIONAL_CHARACTER_VARYING, NCHAR, NCHAR_VARYING).contains(type)) {
            StringBuilder script = new StringBuilder();
            script.append(columnType);
            if (column.getColumnSize() != null && StringUtils.isEmpty(column.getUnit())) {
                script.append("(").append(column.getColumnSize()).append(")");
            } else if (column.getColumnSize() != null && !StringUtils.isEmpty(column.getUnit())) {
                script.append("(").append(column.getColumnSize()).append(" ").append(column.getUnit()).append(")");
            }
            return script.toString();
        }

        if (Arrays.asList(DECIMAL, FLOAT, NUMBER, UROWID, RAW, TIMESTAMP).contains(type)) {
            StringBuilder script = new StringBuilder();
            script.append(columnType);
            if (column.getColumnSize() != null && column.getDecimalDigits() == null) {
                script.append("(").append(column.getColumnSize()).append(")");
            } else if (column.getColumnSize() != null && column.getDecimalDigits() != null) {
                script.append("(").append(column.getColumnSize()).append(",").append(column.getDecimalDigits()).append(")");
            }
            return script.toString();
        }

        if (Arrays.asList(TIMESTAMP_WITH_TIME_ZONE, TIMESTAMP_WITH_LOCAL_TIME_ZONE).contains(type)) {
            StringBuilder script = new StringBuilder();
            if (column.getColumnSize() == null) {
                script.append(columnType);
            } else {
                String[] split = columnType.split("TIMESTAMP");
                script.append("TIMESTAMP").append("(").append(column.getColumnSize()).append(")").append(split[1]);
            }
            return script.toString();
        }


        return columnType;
    }


    @Override
    public String buildModifyColumn(TableColumn tableColumn) {

        if (EditStatus.DELETE.name().equals(tableColumn.getEditStatus())) {
            StringBuilder script = new StringBuilder();
            script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"");
            script.append(" ").append("DROP COLUMN ").append("\"").append(tableColumn.getName()).append("\"");
            return script.toString();
        }
        if (EditStatus.ADD.name().equals(tableColumn.getEditStatus())) {
            StringBuilder script = new StringBuilder();
            script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"");
            script.append(" ").append("ADD (").append(buildCreateColumnSql(tableColumn)).append(")");
            return script.toString();
        }
        if (EditStatus.MODIFY.name().equals(tableColumn.getEditStatus())) {
            StringBuilder script = new StringBuilder();
            script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"");
            script.append(" ").append("MODIFY (").append(buildModifyColumnSql(tableColumn, tableColumn.getOldColumn())).append(") \n");

            if (!StringUtils.equalsIgnoreCase(tableColumn.getOldName(), tableColumn.getName())) {
                script.append(";");
                script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"");
                script.append(" ").append("RENAME COLUMN ").append("\"").append(tableColumn.getOldName()).append("\"").append(" TO ").append("\"").append(tableColumn.getName()).append("\"");

            }
            return script.toString();

        }
        return "";
    }

    public String buildModifyColumnSql(TableColumn column, TableColumn oldColumn) {
        OracleColumnTypeEnum type = COLUMN_TYPE_MAP.get(column.getColumnType().toUpperCase());
        if (type == null) {
            return "";
        }
        StringBuilder script = new StringBuilder();

        script.append("\"").append(column.getName()).append("\"").append(" ");

        script.append(buildDataType(column, type)).append(" ");

        script.append(buildDefaultValue(column, type)).append(" ");

        if (oldColumn.getNullable() != column.getNullable()) {
            script.append(buildNullable(column, type)).append(" ");
        }

        return script.toString();
    }

    public static List<ColumnType> getTypes() {
        return Arrays.stream(OracleColumnTypeEnum.values()).map(columnTypeEnum ->
                columnTypeEnum.getColumnType()
        ).collect(Collectors.toList());
    }
}
