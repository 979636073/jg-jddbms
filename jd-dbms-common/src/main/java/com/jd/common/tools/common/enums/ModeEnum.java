package com.jd.common.tools.common.enums;

import com.jd.common.tools.base.enums.BaseEnum;
import lombok.Getter;

/**
 * model
 *
 * @author Jiaju Zhuang
 */
@Getter
public enum ModeEnum implements BaseEnum<String> {
    /**
     * DESKTOP
     */
    DESKTOP("DESKTOP"),

    /**
     * WEB
     */
    WEB("WEB"),

    ;
    final String description;

    ModeEnum(String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.name();
    }
}
