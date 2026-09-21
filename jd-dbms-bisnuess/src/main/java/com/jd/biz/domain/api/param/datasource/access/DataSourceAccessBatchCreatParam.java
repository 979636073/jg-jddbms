package com.jd.biz.domain.api.param.datasource.access;

import com.jd.common.tools.base.wrapper.param.PageQueryParam;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Data Source Access
 *
 * @author Jiaju Zhuang
 */
@Data
public class DataSourceAccessBatchCreatParam extends PageQueryParam {
    /**
     * 数据源id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * DataSource Access Object
     */
    @NotNull
    @NotEmpty
    private List<DataSourceAccessObjectParam> accessObjectList;
}
