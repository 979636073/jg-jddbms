package com.jd.spi.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 执行结果
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否成功标志位
     */
    private Boolean success;

    /**
     * 失败消息提示
     * 只有失败的情况下会有
     */
    private String message;

    /**
     * 整体描述
     */
    private String allMessage;

    /**
     * sessionId
     */
    private String sessionId;

    /**
     * 执行的sql
     */
    private String sql;

    /**
     * Original SQL without pagination
     */
    private String originalSql;

    /**
     * 描述
     */
    private String description;

    /**
     * 修改行数 查询sql不会返回
     */
    private Integer updateCount;

    /**
     * 展示头的列表
     */
    private List<Header> headerList;

    /**
     * 数据的列表
     */
    private List<List<String>> dataList;

    /**
     * sql 类型
     *
     * @see com.jd.spi.enums.SqlTypeEnum
     */
    private String sqlType;

    /**
     * 是否存在下一页
     * 只有select语句才有
     */
    private Boolean hasNextPage;

    /**
     * Whether the returned total is exact or only a lower bound.
     */
    private Boolean totalExact;

    /**
     * Whether the execution was cancelled by the user.
     */
    private Boolean cancelled;

    /**
     * Whether the execution reached its configured timeout.
     */
    private Boolean timedOut;

    /**
     * 分页编码
     * 只有select语句才有
     */
    private Integer pageNo;

    /**
     * 分页大小
     * 只有select语句才有
     */
    private Integer pageSize;

    /**
     * Total number of fuzzy rows
     * Only select statements have
     */
    private String fuzzyTotal;

    /**
     * 执行持续时间
     */
    private long duration;


    /**
     * 返回结果是否可以编辑
     */
    private boolean canEdit;



    /**
     * 返回结果可以提交或回滚
     */
    private Boolean sign;

    /**
     * 表名
     */
    private String tableName;

    /**
     * Extra information that can be used by the plugin
     */
    private Map<String,Object> extra;

    private String total;
}
