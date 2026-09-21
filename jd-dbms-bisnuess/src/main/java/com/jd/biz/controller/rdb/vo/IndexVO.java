package com.jd.biz.controller.rdb.vo;

import com.jd.spi.enums.IndexTypeEnum;
import lombok.Data;

import java.util.List;

/**
 * @author moji
 * @version IndexVO.java, v 0.1 2022年09月16日 17:47 moji Exp $
 * @date 2022/09/16
 */
@Data
public class IndexVO {

    /**
     * 包含列
     */
    private String columns;

    /**
     * 索引名称
     */
    private String name;

    /**
     * 所以类型
     *
     * @see IndexTypeEnum
     */
    private String type;

    /**
     * 注释
     */
    private String comment;

    /**
     * 索引包含的列
     */
    private List<IndexColumnVO> columnList;
}
