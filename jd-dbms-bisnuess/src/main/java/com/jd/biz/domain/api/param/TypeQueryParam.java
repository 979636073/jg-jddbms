package com.jd.biz.domain.api.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;


@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TypeQueryParam {

    /**
     * 对应数据库存储的来源id
     */
    @NotNull
    private Long dataSourceId;

}
