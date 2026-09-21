package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.api.param.TableVectorParam;
import com.jd.biz.domain.repository.entity.TableVectorMappingDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class TableConverter {

    /**
     * TableVectorParam to TableVectorMappingDO
     *
     * @param param
     * @return
     */
    public abstract TableVectorMappingDO toTableVectorMappingDO(TableVectorParam param);
}
