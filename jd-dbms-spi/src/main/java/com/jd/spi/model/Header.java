package com.jd.spi.model;

import com.jd.spi.enums.DataTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * 单元格头
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Header implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 单元格类型
     *
     * @see DataTypeEnum
     */
    private String dataType;

    /**
     * 展示的名字
     */
    private String name;


    private Boolean primaryKey;


    private String comment;

    private String defaultValue;

    private Integer autoIncrement = 0;

    private Integer nullable;

    private Integer columnSize;

    private Integer decimalDigits;

}
