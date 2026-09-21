package com.jd.biz.controller.common.converter;

import com.jd.biz.controller.common.SimpleEnvironmentVO;
import com.jd.biz.domain.api.model.Environment;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * converter
 *
 * @author Jiaju Zhuang
 */
@Slf4j
@Mapper(componentModel = "spring")
public abstract class EnvironmentCommonConverter {


    /**
     * convert
     *
     * @param list
     * @return
     */
    public abstract List<SimpleEnvironmentVO> dto2vo(List<Environment> list);
}
