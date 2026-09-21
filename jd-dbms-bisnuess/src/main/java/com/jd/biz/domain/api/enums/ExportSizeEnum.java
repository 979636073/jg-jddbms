package com.jd.biz.domain.api.enums;

import com.jd.common.tools.base.enums.BaseEnum;
import lombok.Getter;

/**
 * How much data is currently needed at the beginning
 *
 * @author Jiaju Zhuang
 */
@Getter
public enum ExportSizeEnum implements BaseEnum<String> {
    /**
     * CURRENT_PAGE
     */
    CURRENT_PAGE("CURRENT_PAGE"),

    /**
     * ALL
     */
    ALL("ALL"),


    CHOOSE("CHOOSE")

    ;

    final String description;

    ExportSizeEnum(String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.name();
    }

}
