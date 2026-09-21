package com.jd.spi.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderBy implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 排序字段
     */
    private String columnName;

    /**
     * 排序方式
     */
    private boolean asc;
}
