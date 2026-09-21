
package com.jd.biz.domain.api.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import java.sql.Connection;

/**
 * @author jipengfei
 * @version : SchemaQueryParam.java
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaQueryParam {

    @NotNull
    private Long dataSourceId;

    private String dataBaseName;



    /**
     * if true, refresh the cache
     */
    private Boolean refresh;

    /**
     * Can be null, if null, use the default connection
     */
    private Connection connection;
}