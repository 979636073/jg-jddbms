package com.jd.biz.domain.api.param.datasource.access;

import com.jd.biz.domain.api.enums.AccessObjectTypeEnum;
import com.jd.common.tools.base.wrapper.param.PageQueryParam;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Data Source Access
 *
 * @author Jiaju Zhuang
 */
@Data
public class DataSourceAccessPageQueryParam extends PageQueryParam {
    /**
     * 数据源id
     */
    @NotNull
    private Long dataSourceId;

    /**
     * 授权类型
     *
     * @see AccessObjectTypeEnum
     */
    @NotNull
    private String accessObjectType;

    /**
     * 授权id,根据类型区分是用户还是团队
     */
    @NotNull
    private Long accessObjectId;
}
