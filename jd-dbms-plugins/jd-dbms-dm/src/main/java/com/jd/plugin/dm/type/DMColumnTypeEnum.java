package com.jd.plugin.dm.type;

import cn.hutool.core.util.StrUtil;
import com.jd.spi.ColumnBuilder;
import com.jd.spi.enums.EditStatus;
import com.jd.spi.model.ColumnType;
import com.jd.spi.model.TableColumn;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public enum DMColumnTypeEnum implements ColumnBuilder {

    BFILE("BFILE", false, false, true, false, false, false, true, true, false, false,"512"),

    BIGINT("BIGINT", false, false, true, false, false, false, true, true, false, false, "19"),


    BINARY("BINARY", false, false, true, false, false, false, true, true, false, false,"10"),


    BIT("BIT", false, false, true, false, false, false, true, true, false, false,"1"),


    BLOB("BLOB", false, false, true, false, false, false, true, true, false, false, "2147483647"),


    CHAR("CHAR", true, false, true, false, false, false, true, true, false, true, "10"),

    CHARACTER("CHARACTER", true, false, true, false, false, false, true, true, false, true,"10"),

    CLOB("CLOB", false, false, true, false, false, false, true, true, false, false,"2147483647"),

    DATE("DATE", false, false, true, false, false, false, true, true, false, false,"13"),

    DECIMAL("DECIMAL", true, true, true, false, false, false, true, true, false, false,"22"),

    DOUBLE("DOUBLE", true, false, true, false, false, false, true, true, false, false,"53"),


    FLOAT("FLOAT", true, false, true, false, false, false, true, true, false, false, "53"),

    INT("INT", false, false, true, true, false, false, true, true, false, false,"10"),

    INTEGER("INTEGER", false, false, true, false, false, false, true, true, false, false,"10"),

    INTERVAL_DAY("INTERVAL DAY", true, false, true, false, false, false, true, true, false, false,"2"),

    INTERVAL_DAY_TO_HOUR("INTERVAL DAY TO HOUR", true, false, true, false, false, false, true, true, false, false,"2"),


    INTERVAL_DAY_TO_MINUTE("INTERVAL DAY TO MINUTE", true, false, true, false, false, false, true, true, false, false,"2"),

    INTERVAL_DAY_TO_SECOND("INTERVAL DAY TO SECOND", true, false, true, false, false, false, true, true, false, false,"2"),

    INTERVAL_HOUR("INTERVAL HOUR", true, false, true, false, false, false, true, true, false, false,"2"),

    INTERVAL_HOUR_TO_MINUTE("INTERVAL HOUR TO MINUTE", true, false, true, false, false, false, true, true, false, false,"2"),

    INTERVAL_HOUR_TO_SECOND("INTERVAL HOUR TO SECOND", true, false, true, false, false, false, true, true, false, false, "2"),

    INTERVAL_MINUTE("INTERVAL MINUTE", true, false, true, false, false, false, true, true, false, false,"2"),

    INTERVAL_MINUTE_TO_SECOND("INTERVAL MINUTE TO SECOND", true, false, true, false, false, false, true, true, false, false, "2"),


    INTERVAL_MONTH("INTERVAL MONTH", true, false, true, false, false, false, true, true, false, false,"2"),

    INTERVAL_SECOND("INTERVAL SECOND", true, false, true, false, false, false, true, true, false, false,"2"),


    INTERVAL_YEAR("INTERVAL YEAR", true, false, true, false, false, false, true, true, false, false, "2"),


    INTERVAL_YEAR_TO_MONTH("INTERVAL YEAR TO MONTH", true, false, true, false, false, false, true, true, false, false, "2"),

    LONGVARBINARY("LONGVARBINARY", false, false, true, false, false, false, true, true, false, false, "2147483647"),


    LONGVARCHAR("LONGVARCHAR", false, false, true, false, false,      false, true, true, false, false, "2147483647"),


    NUMERIC("NUMERIC", true, true, true, false, false, false, true, true, false, false, "22"),


    NUMBER("NUMBER", true, true, true, false, false, false, true, true, false, false, "22"),

    REAL("REAL", false, false, true, false, false, false, true, true, false, false, "4"),


    SMALLINT("SMALLINT", false, false, true, false, false, false, true, true, false, false, "5"),


    TIME("TIME", false, false, true, false, false, false, true, true, false, false, "15"),


    TIME_WITH_TIME_ZONE("TIME WITH TIME ZONE", false, false, true, false, false, false, true, true, false, false, "7"),


    TIMESTAMP("TIMESTAMP", false, false, true, false, false, false, true, true, false, false, "36"),


    TIMESTAMP_WITH_TIME_ZONE("TIMESTAMP WITH TIME ZONE", false, false, true, false, false, false, true, true, false, false, "６"),

    TIMESTAMP_WITH__LOCAL_TIME_ZONE("TIMESTAMP WITH LOCAL TIME ZONE", false, false, true, false, false, false, true, true, false, false, "６"),

    DATETIME_WITH_TIME_ZONE("DATETIME WITH TIME ZONE", false, false, true, false, false, false, true, true, false, false, "６"),


    TINYINT("TINYINT", false, false, true, false, false, false, true, true, false, false, "3"),


    VARBINARY("VARBINARY", true, false, true, false, false, false, true, true, false, false, "50"),


    VARCHAR("VARCHAR", true, false, true, false, false, false, true, true, false, true, "50"),


    VARCHAR2("VARCHAR2", true, false, true, false, false, false, true, true, false, true, "50"),

    DEC("DEC", true, false, true, false, false, false, true, true, false, true, "22"),

    DOUBLE_PRECISION("DOUBLE PRECISION", true, false, true, false, false, false, true, true, false, true, "53"),

    DATETIME("DATETIME", false, false, true, false, false, false, true, true, false, false, "13"),

    TEXT("TEXT", false, false, true, false, false, false, true, true, false, true, "2147483647"),
    ;
    private ColumnType columnType;

    public static DMColumnTypeEnum getByType(String dataType) {
        return COLUMN_TYPE_MAP.get(dataType.toUpperCase());
    }

    private static Map<String, DMColumnTypeEnum> COLUMN_TYPE_MAP = Maps.newHashMap();

    static {
        for (DMColumnTypeEnum value : DMColumnTypeEnum.values()) {
            COLUMN_TYPE_MAP.put(value.getColumnType().getTypeName(), value);
        }
    }

    public ColumnType getColumnType() {
        return columnType;
    }

    DMColumnTypeEnum(String dataTypeName, boolean supportLength, boolean supportScale, boolean supportNullable, boolean supportAutoIncrement, boolean supportCharset, boolean supportCollation, boolean supportComments, boolean supportDefaultValue, boolean supportExtent, boolean supportUnit, String defaultValue) {
        this.columnType = new ColumnType(dataTypeName, supportLength, supportScale, supportNullable, supportAutoIncrement, supportCharset, supportCollation, supportComments, supportDefaultValue, supportExtent, false, supportUnit, defaultValue);
    }

    @Override
    public String buildCreateColumnSql(TableColumn column) {
        DMColumnTypeEnum type = COLUMN_TYPE_MAP.get(column.getColumnType().toUpperCase());
        if (type == null) {
            return "";
        }
        StringBuilder script = new StringBuilder();

        script.append("\"").append(column.getName()).append("\"").append(" ");
        if (Arrays.asList(DMColumnTypeEnum.TIMESTAMP, DMColumnTypeEnum.DATETIME).contains(type)) {
            script.append(column.getColumnType()).append(" ");
        } else {
            script.append(buildDataType(column, type)).append(" ");
        }
        String s = buildDefaultValue(column, type);
        script.append(s).append(" ");
        script.append(buildNullable(column, type)).append(" ");
        return script.toString();
    }

    private String buildAutoIncrement(TableColumn column, DMColumnTypeEnum type) {
        if (!type.getColumnType().isSupportAutoIncrement()) {
            return "";
        }
        if (column.getAutoIncrement() != null && column.getAutoIncrement()
                && column.getSeed() != null && column.getSeed() > 0 && column.getIncrement() != null && column.getIncrement() > 0) {
            return "IDENTITY(" + column.getSeed() + "," + column.getIncrement() + ")";
        }
        if (column.getAutoIncrement() != null && column.getAutoIncrement()) {
            return "IDENTITY(1,1)";
        }
        return "";
    }

    private String buildNullable(TableColumn column, DMColumnTypeEnum type) {
        if (!type.getColumnType().isSupportNullable()) {
            return "";
        }
        if (column.getNullable() != null && 0 == column.getNullable()) {
            return "NULL";
        } else {
            return "NOT NULL";
        }
    }

    private String buildDefaultValue(TableColumn column, DMColumnTypeEnum type) {
        boolean isCreate = false;
        if (Objects.isNull(column.getOldColumn())) {
            isCreate = true;
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
                return " DROP DEFAULT";
            }
        }

        if ("EMPTY_STRING".equalsIgnoreCase(column.getDefaultValue().trim())) {
            return StringUtils.join("DEFAULT ''");
        }

        if ("NULL".equalsIgnoreCase(column.getDefaultValue().trim())) {
            return StringUtils.join("DEFAULT NULL");
        }
        String join = "";
        if (isCreate) {
            join = " DEFAULT (" + column.getDefaultValue() + ")" ;
        } else {
            join = " SET DEFAULT (" + column.getDefaultValue() + ")" ;
        }
        return StringUtils.join(join);
    }

    private String buildDataType(TableColumn column, DMColumnTypeEnum type) {
        String columnType = type.columnType.getTypeName();
        if (Arrays.asList(CHAR, VARCHAR, VARCHAR2, LONGVARCHAR, DEC, VARBINARY).contains(type)) {
            StringBuilder script = new StringBuilder();
            script.append(columnType);
            if (column.getColumnSize() != null && StringUtils.isEmpty(column.getUnit())) {
                script.append("(").append(column.getColumnSize()).append(")");
            } else if (column.getColumnSize() != null && !StringUtils.isEmpty(column.getUnit())) {
                script.append("(").append(column.getColumnSize()).append(" ").append(column.getUnit()).append(")");
            }
            return script.toString();
        }

        if (Arrays.asList(DECIMAL, FLOAT, NUMBER, TIMESTAMP, NUMERIC).contains(type)) {
            StringBuilder script = new StringBuilder();
            script.append(columnType);
            if (column.getColumnSize() != null && column.getDecimalDigits() == null) {
                script.append("(").append(column.getColumnSize()).append(")");
            } else if (column.getColumnSize() != null && column.getDecimalDigits() != null) {
                script.append("(").append(column.getColumnSize()).append(",").append(column.getDecimalDigits()).append(")");
            }
            return script.toString();
        }

        if (Objects.equals(TIMESTAMP_WITH_TIME_ZONE, type)) {
            StringBuilder script = new StringBuilder();
            if (column.getColumnSize() == null) {
                script.append(columnType);
            } else {
                String[] split = columnType.split("TIMESTAMP");
                script.append("TIMESTAMP").append("(").append(column.getColumnSize()).append(")").append(split[1]);
            }
            return script.toString();
        }

        if (Arrays.asList(INTERVAL_DAY_TO_HOUR,
                INTERVAL_DAY_TO_MINUTE, INTERVAL_DAY_TO_SECOND,
                INTERVAL_HOUR_TO_MINUTE,
                INTERVAL_HOUR_TO_SECOND,
                INTERVAL_MINUTE_TO_SECOND,
                INTERVAL_YEAR_TO_MONTH).contains(type)) {
            StringBuilder script = new StringBuilder();
            if (column.getColumnSize() == null) {
                script.append(columnType);
            } else {
                String[] split = columnType.split(" ");
                if (split.length == 4) {
                    script.append(split[0]).append(" ").append(split[1]).append(" (").append(column.getColumnSize()).append(") ").append(split[2]).append(" ").append(split[3]);
                }
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
            DMColumnTypeEnum type = COLUMN_TYPE_MAP.get(tableColumn.getColumnType().toUpperCase());
            StringBuilder script = new StringBuilder();
            if(!Objects.equals(tableColumn.getNullable(), tableColumn.getOldColumn().getNullable())){
                if (tableColumn.getOldColumn() != null && tableColumn.getOldColumn().getAutoIncrement() != null && tableColumn.getOldColumn().getAutoIncrement().equals(true)
                        && tableColumn.getAutoIncrement() != null && tableColumn.getAutoIncrement().equals(false)) {
                    script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"")
                            .append(" ").append("drop identity;\n");
                }
                //是否可空
//                script.append(buildDefaultValue(tableColumn, type)).append(" ");
                script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"");
                script.append(" ALTER COLUMN ").append("\"").append(tableColumn.getName()).append("\"").append(" SET ");
                script.append(buildNullable(tableColumn, type)).append(";\n");
            }
            if (tableColumn.getOldColumn() != null && tableColumn.getOldColumn().getAutoIncrement() != null && tableColumn.getOldColumn().getAutoIncrement().equals(true)
                    && tableColumn.getAutoIncrement() != null && tableColumn.getAutoIncrement().equals(false)) {
                script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"")
                        .append(" ").append("drop identity;\n");
            }
            if (!StringUtils.equalsIgnoreCase(tableColumn.getOldName(), tableColumn.getName())) {
                script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"");
                script.append(" ALTER COLUMN ").append("\"").append(tableColumn.getOldName()).append("\"").append(" RENAME ").append(" TO ").append("\"").append(tableColumn.getName()).append("\"");
                script.append(";\n");
            }
            if (tableColumn.getOldColumn() != null && !StringUtils.equalsIgnoreCase(tableColumn.getOldColumn().getDefaultValue(),tableColumn.getDefaultValue())) {
                script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"");
                script.append(" ALTER COLUMN ").append("\"").append(tableColumn.getName()).append("\"");
                script.append(buildDefaultValue(tableColumn, type)).append(";\n");
            }
            boolean ref = true;
            if (StringUtils.equalsIgnoreCase(tableColumn.getOldName(), tableColumn.getName()) && !Objects.equals(tableColumn.getOldColumn().getColumnSize(), tableColumn.getColumnSize())) {
                script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"");
                script.append(" MODIFY ").append(buildCreateColumnSql(tableColumn));
                script.append(";\n");
                ref = false;
            }
            if ((tableColumn.getOldColumn() != null && tableColumn.getOldColumn().getAutoIncrement() != null && tableColumn.getOldColumn().getAutoIncrement().equals(false)
                    && tableColumn.getAutoIncrement() != null && tableColumn.getAutoIncrement().equals(true))) {
                script.append(buildDefaultValue(tableColumn, type)).append(" ");
                script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"");
                script.append(" ALTER COLUMN ").append("\"").append(tableColumn.getName()).append("\"").append(" SET ");
                tableColumn.setNullable(1);
                script.append(buildNullable(tableColumn, type)).append(";\n");
                script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"")
                        .append(" ADD COLUMN ").append("\"").append(tableColumn.getName()).append("\"").append(" identity(1, 1);\n");
            }

            if(!StringUtils.equalsIgnoreCase(tableColumn.getOldColumn().getColumnType(), tableColumn.getColumnType()) && ref) {
                //字段类型修改
                script.append("ALTER TABLE ").append("\"").append(tableColumn.getSchemaName()).append("\".\"").append(tableColumn.getTableName()).append("\"");
                script.append(" MODIFY ").append("\"").append(tableColumn.getName()).append("\"").append(" ");
                if (Arrays.asList(DMColumnTypeEnum.TIMESTAMP, DMColumnTypeEnum.DATETIME).contains(type)) {
                    script.append(tableColumn.getColumnType()).append(";\n");
                } else {
                    script.append(buildDataType(tableColumn, type)).append(";\n");
                }
            }
            return script.toString();

        }
        return "";
    }

    public static List<ColumnType> getTypes() {
        return Arrays.stream(DMColumnTypeEnum.values()).map(columnTypeEnum ->
                columnTypeEnum.getColumnType()
        ).collect(Collectors.toList());
    }
}
