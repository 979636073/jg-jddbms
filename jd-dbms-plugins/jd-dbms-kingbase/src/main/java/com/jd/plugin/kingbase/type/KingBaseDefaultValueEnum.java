package com.jd.plugin.kingbase.type;

import com.jd.spi.model.DefaultValue;

import java.util.Arrays;
import java.util.List;

public enum KingBaseDefaultValueEnum {
    EMPTY_STRING("EMPTY_STRING"),
    NULL("NULL"),
    ;
    private DefaultValue defaultValue;

    KingBaseDefaultValueEnum(String defaultValue) {
        this.defaultValue = new DefaultValue(defaultValue);
    }


    public DefaultValue getDefaultValue() {
        return defaultValue;
    }

    public static List<DefaultValue> getDefaultValues() {
        return Arrays.stream(KingBaseDefaultValueEnum.values()).map(KingBaseDefaultValueEnum::getDefaultValue).collect(java.util.stream.Collectors.toList());
    }

}
