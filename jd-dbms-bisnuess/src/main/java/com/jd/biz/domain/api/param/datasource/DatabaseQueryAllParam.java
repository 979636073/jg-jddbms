package com.jd.biz.domain.api.param.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import java.sql.Connection;

/**
 * 展示数据库信息
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseQueryAllParam {
    /**
     * 对应数据库存储的来源id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * if true, refresh the cache
     */
    private boolean refresh;

    /**
     * Can be null, if null, use the default connection
     */
    private Connection connection;

    /**
     * Can be null, if null, use the default dbType
     */
    private String dbType;
}
