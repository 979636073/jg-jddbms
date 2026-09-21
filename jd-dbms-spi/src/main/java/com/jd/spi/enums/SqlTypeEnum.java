package com.jd.spi.enums;

import com.jd.common.tools.base.enums.BaseEnum;
import lombok.Getter;

/**
 * sq类型
 *
 * @author Jiaju Zhuang
 */
@Getter
public enum SqlTypeEnum implements BaseEnum<String> {
    /**
     * 查询语句
     */
    SELECT("查询语句"),

    /**
     * 未知
     */
    UNKNOWN("未知"),

    ;

    final String description;

    SqlTypeEnum(String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.name();
    }
}
