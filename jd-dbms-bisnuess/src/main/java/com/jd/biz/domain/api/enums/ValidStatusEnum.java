package com.jd.biz.domain.api.enums;

import com.jd.common.tools.base.enums.BaseEnum;
import lombok.Getter;

/**
 * Is it a valid enumeration
 *
 * @author Jiaju Zhuang
 */
@Getter
public enum ValidStatusEnum implements BaseEnum<String> {
    /**
     * VALID
     */
    VALID("VALID"),

    /**
     * INVALID
     */
    INVALID("INVALID"),

    ;
    final String description;

    ValidStatusEnum(String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.name();
    }
}
