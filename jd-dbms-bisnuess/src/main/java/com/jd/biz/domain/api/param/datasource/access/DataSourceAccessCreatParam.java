package com.jd.biz.domain.api.param.datasource.access;

import com.jd.biz.domain.api.enums.AccessObjectTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

/**
 * Data Source Access
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceAccessCreatParam  {
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
