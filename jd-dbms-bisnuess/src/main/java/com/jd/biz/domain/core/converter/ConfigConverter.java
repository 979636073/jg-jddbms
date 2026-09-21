
package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.api.model.Config;
import com.jd.biz.domain.api.param.SystemConfigParam;
import com.jd.biz.domain.repository.entity.SystemConfigDO;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;

/**
 * @author jipengfei
 * @version : ConfigConverter.java
 */
@Slf4j
@Mapper(componentModel = "spring")
public abstract class ConfigConverter {

    public abstract SystemConfigDO param2do(SystemConfigParam param);

    public abstract Config do2model(SystemConfigDO systemConfigDO);
}