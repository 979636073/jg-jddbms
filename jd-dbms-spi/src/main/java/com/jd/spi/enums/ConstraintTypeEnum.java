package com.jd.spi.enums;

public enum ConstraintTypeEnum {

    PRIMARY_KEY("Primary", "PRIMARY KEY"),

    UNIQUE("Unique", "UNIQUE INDEX"),

    CHECK("CHECK", "CHECK"),

    NOT_NULL("NOT_NULL", "NOT_NULL"),
    /**
     * 外键
     */
    VIRTUAL("VIRTUAL", "VIRTUAL INDEX"),

    /**
     * 外键显示使用
     */
    FOREIGN_KEY("FOREIGN_KEY", "FOREIGN_KEY INDEX");

    public String getName() {
        return name;
    }

    private String name;


    public String getKeyword() {
        return keyword;
    }

    private String keyword;

    ConstraintTypeEnum(String name, String keyword) {
        this.name = name;
        this.keyword = keyword;
    }


    public static ConstraintTypeEnum getByType(String type) {
        for (ConstraintTypeEnum value : ConstraintTypeEnum.values()) {
            if (value.name.equalsIgnoreCase(type)) {
                return value;
            }
        }
        return null;
    }
}
