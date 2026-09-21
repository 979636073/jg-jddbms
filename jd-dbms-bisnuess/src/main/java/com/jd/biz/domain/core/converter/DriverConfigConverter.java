package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.repository.entity.JdbcDriverDO;
import com.jd.spi.config.DriverConfig;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class DriverConfigConverter {
    public abstract DriverConfig do2Config(JdbcDriverDO driverDO);

}
