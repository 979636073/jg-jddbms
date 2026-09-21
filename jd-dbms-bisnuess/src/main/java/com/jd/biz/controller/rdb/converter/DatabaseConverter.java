package com.jd.biz.controller.rdb.converter;

import com.jd.biz.controller.rdb.request.DatabaseCreateRequest;
import com.jd.biz.controller.rdb.request.DatabaseExportRequest;
import com.jd.biz.domain.api.param.datasource.DatabaseExportParam;
import com.jd.spi.model.Database;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class DatabaseConverter {

    public abstract Database request2param(DatabaseCreateRequest request);

    public abstract DatabaseExportParam request2param(DatabaseExportRequest request);
}
