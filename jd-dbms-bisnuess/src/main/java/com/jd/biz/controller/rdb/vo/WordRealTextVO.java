package com.jd.biz.controller.rdb.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Author: corn
 * @CreateTime: 2024-12-03
 * @Description: word文本真实信息表
 * @Version: 1.0
 */
@Data
public class WordRealTextVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 占位SQL
     */
    private String reserveSql;

    /**
     * 占位文本（,分割）
     */
    private String reserveText;

    /**
     * 段落内容
     */
    private String paragraphText;

    /**
     * 数据源ID
     */
    private Long dataSourceId;

    /**
     * 模版名称
     */
    private String name;

    /**
     * 文本内容
     */
    private String textInfo;

    /**
     * 前置内容
     */
    private String headerInfo;

    /**
     * 表头数据
     */
    private List<String> headerList;

    /**
     * 行数据
     */
    private List<List<String>> dataList;

}
