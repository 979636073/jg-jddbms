package com.jd.biz.domain.api.enums;

import com.jd.common.tools.base.enums.BaseEnum;
import lombok.Getter;

/**
 * export type
 *
 * @author Jiaju Zhuang
 */
@Getter
public enum ExportTypeEnum implements BaseEnum<String> {
    /**
     * CSV
     */
    CSV("CSV"),

    /**
     * INSERT
     */
    INSERT("INSERT"),

    /**
     * WORD
     */
    WORD("WORD"),

    /**
     * EXCEL
     */
    EXCEL("EXCEL"),

    /**
     * HTML
     */
    HTML("HTML"),

    /**
     * MARKDOWN
     */
    MARKDOWN("MARKDOWN"),

    /**
     * PDF
     */
    PDF("PDF");

    final String description;

    ExportTypeEnum(String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.name();
    }

}
