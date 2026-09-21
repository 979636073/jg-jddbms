package com.jd.plugin.dm.type;

public enum DMColumnDefaultEnum {
    
    BFILE("BFILE", "512"),

    BIGINT("BIGINT","19" ),

    BINARY("BINARY","10" ),

    BIT("BIT","1"),

    BLOB("BLOB",""),

    CHAR("CHAR","10"),

    CLOB("CLOB",""),

    DATE("DATE","13" ),

    DECIMAL("DECIMAL","22"),

    DOUBLE("DOUBLE", "53"),

    FLOAT("FLOAT", "53"),

    INT("INT","10"),

    INTEGER("INTEGER","10"),

    INTERVAL_DAY("INTERVAL DAY","2"),

    INTERVAL_DAY_TO_HOUR("INTERVAL DAY TO HOUR", "2"),

    INTERVAL_DAY_TO_MINUTE("INTERVAL DAY TO MINUTE", "2"),

    INTERVAL_DAY_TO_SECOND("INTERVAL DAY TO SECOND","2"),

    INTERVAL_HOUR("INTERVAL HOUR","2"),

    INTERVAL_HOUR_TO_MINUTE("INTERVAL HOUR TO MINUTE","2"),

    INTERVAL_HOUR_TO_SECOND("INTERVAL HOUR TO SECOND","2"),

    INTERVAL_MINUTE("INTERVAL MINUTE","2" ),

    INTERVAL_MINUTE_TO_SECOND("INTERVAL MINUTE TO SECOND", "2" ),

    INTERVAL_MONTH("INTERVAL MONTH","2" ),

    INTERVAL_SECOND("INTERVAL SECOND","2" ),

    INTERVAL_YEAR("INTERVAL YEAR","2"),

    INTERVAL_YEAR_TO_MONTH("INTERVAL YEAR TO MONTH","2" ),

    LONGVARBINARY("LONGVARBINARY",""),

    LONGVARCHAR("LONGVARCHAR",""),

    NUMERIC("NUMERIC", "22"),

    NUMBER("NUMBER", "22"),

    REAL("REAL", "24" ),

    SMALLINT("SMALLINT", "5" ),

    TIME("TIME", "15" ),

    TIME_WITH_TIME_ZONE("TIME WITH TIME ZONE", "7"),

    TIMESTAMP("TIMESTAMP", "36"),

    TIMESTAMP_WITH_TIME_ZONE("TIMESTAMP WITH TIME ZONE", "10"),

    TINYINT("TINYINT", "3"),

    VARBINARY("VARBINARY", "50"),

    VARCHAR("VARCHAR", "50"),

    VARCHAR2("VARCHAR2", "50"),

    DATETIME("DATETIME", "36"),

    TEXT("TEXT", "");

    private String defaultValue;

    private String dataTypeName;

    public String getDefaultValue() {
        return defaultValue;
    }

    DMColumnDefaultEnum(String dataTypeName, String defaultValue) {
        this.dataTypeName = dataTypeName;
        this.defaultValue = defaultValue;
    }

    public static DMColumnDefaultEnum getByName(String name) {
        for (DMColumnDefaultEnum value : DMColumnDefaultEnum.values()) {
            if (value.dataTypeName.equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }
}
