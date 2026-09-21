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
public class MetaDataQueryParam {

    @NotNull
    private Long dataSourceId;


    /**
     * if true, refresh the cache
     */
    private boolean refresh;
}
