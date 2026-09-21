package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.api.model.Environment;
import com.jd.biz.domain.api.service.EnvironmentService;
import com.jd.biz.domain.repository.entity.EnvironmentDO;
import com.jd.common.tools.common.util.EasyCollectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.springframework.context.annotation.Lazy;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * converter
 *
 * @author Jiaju Zhuang
 */
@Slf4j
@Mapper(componentModel = "spring")
public abstract class EnvironmentConverter {

    @Resource
    @Lazy
    private EnvironmentService environmentService;

    /**
     * convert
     *
     * @param list
     * @return
     */
    public abstract List<Environment> do2dto(List<EnvironmentDO> list);

    /**
     * Fill in detailed information
     *
     * @param list
     */
    public void fillDetail(List<Environment> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        List<Long> idList = EasyCollectionUtils.toList(list, Environment::getId);
        List<Environment> queryList = environmentService.listQuery(idList).getData();
        Map<Long, Environment> queryMap = EasyCollectionUtils.toIdentityMap(queryList, Environment::getId);
        for (Environment data : list) {
            if (data == null || data.getId() == null) {
                continue;
            }
            Environment query = queryMap.get(data.getId());
            add(data, query);
        }
    }

    @Mappings({
        @Mapping(target = "id", ignore = true),
    })
    public abstract void add(@MappingTarget Environment target, Environment source);
}
