package com.jd.spi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DmpImportVo implements Serializable {
    private static final long serialVersionUID = 8377874564143215L;


    /**
     * 数据源id
     */
    private Long dataSourceId;

    /**
     * DB名称
     */
    private String databaseName;

    /**
     * 表所在空间
     */
    private String schemaName;
    /**
     * 执行状态
     */
    private String status;

    /**
     * log地址
     */
    private String logUrl;

    private Long userId;

    private String msg;

}
