package com.jd.plugin.oracle.type;

public enum OracleColumnDefaultValueEnum {
    BFILE("BFILE", "0"),

    BIT("BIT", "1"),

    BLOB("BLOB","0"),

    CHAR("CHAR", "1"),

    CLOB("CLOB", "0"),

    NCLOB("NCLOB", "0"),

    LONG("LONG", "0"),

    LONG_RAW("LONG RAW","0"),

    DATE("DATE", "0"),

    DECIMAL("DECIMAL", "0"),

    DOUBLE_PRECISION("DOUBLE PRECISION", "0"),

    FLOAT("FLOAT", "0"),

    INT("INT", "0"),

    INTEGER("INTEGER", "0"),

    NUMERIC("NUMERIC", "0"),

    NUMBER("NUMBER", "0"),

    REAL("REAL", "0"),

    SMALLINT("SMALLINT", "0"),

    TINYINT("TINYINT", "3"),

    RAW("RAW", "1"),

    ROWID("ROWID", "0"),

    UROWID("UROWID", "0"),

    VARBINARY("VARBINARY", "1"),

    VARCHAR("VARCHAR", "1"),

    MLSLABLE("MLSLABLE","0"),

    NCHAR("NCHAR", "1"),

    VARCHAR2("VARCHAR2", "1"),

    NATIONAL_CHAR("NATIONAL CHAR", "1"),

    NATIONAL_CHAR_VARYING("NATIONAL CHAR VARYING", "1"),

    NATIONAL_CHARACTER("NATIONAL CHARACTER", "1"),

    NATIONAL_CHARACTER_VARYING("NATIONAL CHARACTER VARYING", "1"),

    CHAR_VARYING("CHAR VARYING","1"),

    CHARACTER("CHARACTER","1"),

    CHARACTER_VARYING("CHARACTER VARYING", "1"),

    NVARCHAR2("NVARCHAR2","1"),

    BINARY_DOUBLE("BINARY DOUBLE", "0"),

    BINARY_FLOAT("BINARY FLOAT","0"),

    URITYPE("URITYPE","0");


    private String defaultValue;

    private String dataTypeName;

    public String getDefaultValue() {
        return defaultValue;
    }

    OracleColumnDefaultValueEnum(String dataTypeName, String defaultValue) {
        this.dataTypeName = dataTypeName;
        this.defaultValue = defaultValue;
    }

    public static OracleColumnDefaultValueEnum getByName(String name) {
        for (OracleColumnDefaultValueEnum value : OracleColumnDefaultValueEnum.values()) {
            if (value.dataTypeName.equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }
}
