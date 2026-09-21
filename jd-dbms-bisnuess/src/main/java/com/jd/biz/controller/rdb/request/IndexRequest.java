package com.jd.biz.controller.rdb.request;

import com.jd.spi.enums.IndexTypeEnum;
import com.jd.spi.model.TableIndexColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 索引
 *
 * @author 是仪
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class IndexRequest {

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
    private List<TableIndexColumn> columnList;


    private String editStatus;

}
