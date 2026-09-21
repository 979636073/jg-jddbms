package com.jd.biz.domain.api.param.datasource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author: zgq
 * @date: 2024年02月27日 22:08
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DatabaseExportParam {
    /**
     * DB name
     */
    private String databaseName;

    private String schemaName;

    private Boolean containData;

    private List<String> tableList;

}
