package com.jd.biz.domain.api.param;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class PinTableParam {

    @NotNull
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
     * tableName
     */
    private String tableName;

    /**
     * pin userId
     */
    private Long userId;
}
