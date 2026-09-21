package com.jd.biz.controller.data.source.vo;

import com.jd.biz.domain.api.model.DataSource;
import lombok.Data;

@Data
public class DataSourceConnectVo {
    /**
     * 数据源ID
     */
    private Long dateSourceId;
    /**
     * 数据源名称
     */
    private String dateSourceName;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 连接数据源对象
     */
    private DataSource dataSource;
}
