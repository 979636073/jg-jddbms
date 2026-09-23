package com.jd.spi.enums;

import com.jd.common.tools.base.enums.BaseEnum;
import lombok.Getter;

/**
 * 驱动类枚举
 *
 * @author Jiaju Zhuang
 */
@Getter
public enum DataTypeEnum implements BaseEnum<String> {
    /**
     * 布尔值
     */
    BOOLEAN("布尔值"),

    /**
     * 数字
     */
    NUMERIC("数字"),

    /**
     * 字符串
     */
    STRING("字符串"),

    /**
     * 日期
     */
    DATETIME("日期"),

    /**
     * 二进制
     */
    BINARY("空数据"),

    /**
     * 内容
     */
    CONTENT("内容"),

    /**
     * 二进制
     */
    BYTE("二进制"),

    /**
     * 结构
     */
    STRUCT("结构"),

    /**
     * 文档
     */
    DOCUMENT("文档"),

    /**
     * 数组
     */
    ARRAY("数组"),

    /**
     * 对象
     */
    OBJECT("对象"),

    /**
     * 引用
     */
    REFERENCE("引用"),

    /**
     * 行号
     */
    ROWID("行号"),

    /**
     * 任何
     */
    ANY("任何"),

    /**
     * 未知
     */
    UNKNOWN("未知"),

    /**
     * 行号
     */
    CHAT2DB_ROW_NUMBER("行号"),
    ;

    final String description;

    DataTypeEnum(String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.name();
    }

    public static DataTypeEnum getByCode(String code) {
        for (DataTypeEnum value : DataTypeEnum.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return DataTypeEnum.UNKNOWN;
    }

    public String getSqlValue(String value) {
        if (this == DataTypeEnum.BOOLEAN) {
            if("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                if ("true".equalsIgnoreCase(value)) {
                    return "1";
                } else {
                    return "0";
                }
            } else {
                return quote(value);
            }
//            if("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)){
//                return value;
//            }else {
//                return "'" + value + "'";
//            }
        }
        if (this == DataTypeEnum.NUMERIC) {
            return value;
        }
        if (this == DataTypeEnum.STRING) {
            return quote(value);
        }
        if (this == DataTypeEnum.DATETIME) {
            return quote(value);
        }
        if (this == DataTypeEnum.BINARY) {
            return "''";
        }
        if (this == DataTypeEnum.CONTENT) {
            return quote(value);
        }
        if (this == DataTypeEnum.BYTE) {
            return "?";
        }
        if (this == DataTypeEnum.STRUCT) {
            return quote(value);
        }
        if (this == DataTypeEnum.DOCUMENT) {
            return quote(value);
        }
        if (this == DataTypeEnum.ARRAY) {
            return quote(value);
        }
        if (this == DataTypeEnum.OBJECT) {
            return quote(value);
        }
        if (this == DataTypeEnum.REFERENCE) {
            return quote(value);
        }
        if (this == DataTypeEnum.ROWID) {
            return quote(value);
        }
        if (this == DataTypeEnum.ANY) {
            return quote(value);
        }
        if (this == DataTypeEnum.UNKNOWN) {
            return quote(value);
        }
        return quote(value);
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
