package com.jd.plugin.gbase.type;

import com.jd.spi.model.DefaultValue;

import java.util.Arrays;
import java.util.List;

public enum GbaseDefaultValueEnum {

    EMPTY_STRING("EMPTY_STRING"),
    NULL("NULL"),
    CURRENT_TIMESTAMP("CURRENT_TIMESTAMP"),
    ;
    private DefaultValue defaultValue;

    GbaseDefaultValueEnum(String defaultValue) {
        this.defaultValue = new DefaultValue(defaultValue);
    }


    public DefaultValue getDefaultValue() {
        return defaultValue;
    }

    public static List<DefaultValue> getDefaultValues() {
        return Arrays.stream(GbaseDefaultValueEnum.values()).map(GbaseDefaultValueEnum::getDefaultValue).collect(java.util.stream.Collectors.toList());
    }

}
