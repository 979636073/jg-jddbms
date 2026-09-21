package com.jd.biz.controller.pin.converter;

import com.jd.biz.domain.api.param.PinTableParam;
import com.jd.biz.controller.pin.request.PinTableRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class PinWebConverter {


    public abstract PinTableParam req2param(PinTableRequest request);
}
