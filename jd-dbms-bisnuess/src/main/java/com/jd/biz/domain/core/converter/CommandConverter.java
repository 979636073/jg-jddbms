package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.spi.model.Command;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(componentModel = "spring")
public abstract class CommandConverter {

    @Mappings({
            @Mapping(target = "script", source = "sql")
    })
    public abstract Command param2model(DlExecuteParam param);
}
