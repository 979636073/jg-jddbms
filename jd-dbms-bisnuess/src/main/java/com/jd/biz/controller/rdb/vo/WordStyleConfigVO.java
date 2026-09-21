package com.jd.biz.controller.rdb.vo;

import lombok.Data;
import java.io.Serializable;

/**
 * @Author: corn
 * @CreateTime: 2024-12-03
 * @Description: word样式信息表
 * @Version: 1.0
 */
@Data
public class WordStyleConfigVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 字体样式
     */
    private String fontStyle;

    /**
     * 样式名称
     */
    private String name;

    /**
     * 对齐样式 (1:居中 2:左侧 3:右侧)
     */
    private String alignStyle;

    /**
     * 字体大小
     */
    private String fontSize;

    /**
     * 行间距
     */
    private Integer lineSpace;

    /**
     * 缩进类型 (1:首行 2:左侧 3:右侧)
     */
    private Integer lineIndentationType;

    /**
     * 缩进大小
     */
    private Integer lineIndentationSize;

    /**
     * 是否是标题(0:否，1:是)
     */
    private Integer isTitle;

    /**
     * 标题类型（一， 二，三  ... 级标题）
     */
    private Integer titleType;

}
