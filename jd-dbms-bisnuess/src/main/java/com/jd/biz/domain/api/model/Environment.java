package com.jd.biz.domain.api.model;

import com.jd.common.tools.base.constant.EasyToolsConstant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * Environment
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Environment implements Serializable {

    private static final long serialVersionUID = EasyToolsConstant.SERIAL_VERSION_UID;

    /**
     * 主键
     */
    private Long id;

    /**
     * 环境名称
     */
    private String name;

    /**
     * 环境缩写
     */
    private String shortName;

    /**
     * 颜色
     */
    private String color;
}
