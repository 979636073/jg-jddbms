package com.jd.plugin.mysql.type;

import com.jd.spi.model.DefaultValue;

import java.util.Arrays;
import java.util.List;

public enum OscarDefaultValueEnum {

    EMPTY_STRING("EMPTY_STRING"),
    NULL("NULL"),
    CURRENT_TIMESTAMP("CURRENT_TIMESTAMP"),
    ;
    private DefaultValue defaultValue;

    OscarDefaultValueEnum(String defaultValue) {
        this.defaultValue = new DefaultValue(defaultValue);
    }


    public DefaultValue getDefaultValue() {
        return defaultValue;
    }

    public static List<DefaultValue> getDefaultValues() {
        return Arrays.stream(OscarDefaultValueEnum.values()).map(OscarDefaultValueEnum::getDefaultValue).collect(java.util.stream.Collectors.toList());
    }

}
