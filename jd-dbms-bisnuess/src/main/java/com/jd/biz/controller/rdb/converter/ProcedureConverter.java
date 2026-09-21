package com.jd.biz.controller.rdb.converter;

import com.jd.biz.controller.rdb.request.ProcedureUpdateRequest;
import com.jd.spi.model.Procedure;
import org.mapstruct.Mapper;

/**
 * @author: zgq
 * @date: February 24, 2024 13:39
 */
@Mapper(componentModel = "spring")
public abstract class ProcedureConverter {

    public abstract Procedure request2param(ProcedureUpdateRequest request);
}
