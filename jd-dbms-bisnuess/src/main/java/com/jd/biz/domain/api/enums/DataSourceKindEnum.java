package com.jd.biz.domain.api.enums;

import com.jd.common.tools.base.enums.BaseEnum;
import lombok.Getter;

/**
 * Data Source Kind
 *
 * @author Jiaju Zhuang
 */
@Getter
public enum DataSourceKindEnum implements BaseEnum<String> {
    /**
     * PRIVATE
     */
    PRIVATE("PRIVATE"),

    /**
     * SHARED
     */
    SHARED("SHARED"),

    ;

    final String description;

    DataSourceKindEnum(String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.name();
    }

}
