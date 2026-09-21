package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.api.param.PinTableParam;
import com.jd.biz.domain.api.param.TablePageQueryParam;
import com.jd.biz.domain.repository.entity.PinTableDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class PinTableConverter {

    /**
     *
     * @param param
     * @return
     */
    public abstract PinTableDO param2do(PinTableParam param);



    public abstract PinTableParam toPinTableParam (TablePageQueryParam param);
}
