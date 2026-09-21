package com.jd.biz.controller.rdb.vo;

import lombok.Data;

@Data
public class ExcelDataHighVO {

    private String oldValue;

    private String newValue;

    /**
     * 0 不变 1修改 2唯一 3非空 4外键
     */
    private Integer viewType;
}
