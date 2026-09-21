package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.api.model.Team;
import com.jd.biz.domain.api.param.team.TeamCreateParam;
import com.jd.biz.domain.api.param.team.TeamUpdateParam;
import com.jd.biz.domain.api.service.TeamService;
import com.jd.biz.domain.repository.entity.TeamDO;
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
public abstract class TeamConverter {

    @Resource
    @Lazy
    private TeamService teamService;

    /**
     * convert
     *
     * @param list
     * @return
     */
    public abstract List<Team> do2dto(List<TeamDO> list);

    /**
     * convert
     *
     * @param data
     * @return
     */
    @Mappings({
        @Mapping(target = "modifiedUser.id", source = "modifiedUserId"),
    })
    public abstract Team do2dto(TeamDO data);

    /**
     * convert
     *
     * @param param
     * @return
     */
    @Mappings({
        @Mapping(target = "createUserId", source = "userId"),
        @Mapping(target = "modifiedUserId", source = "userId"),
    })
    public abstract TeamDO param2do(TeamCreateParam param, Long userId);

    /**
     * convert
     *
     * @param param
     * @return
     */
    @Mappings({
        @Mapping(target = "modifiedUserId", source = "userId"),
    })
    public abstract TeamDO param2do(TeamUpdateParam param, Long userId);

    /**
     * Fill in detailed information
     *
     * @param list
     */
    public void fillDetail(List<Team> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        List<Long> idList = EasyCollectionUtils.toList(list, Team::getId);
        List<Team> queryList = teamService.listQuery(idList).getData();
        Map<Long, Team> queryMap = EasyCollectionUtils.toIdentityMap(queryList, Team::getId);
        for (Team data : list) {
            if (data == null || data.getId() == null) {
                continue;
            }
            Team query = queryMap.get(data.getId());
            add(data, query);
        }
    }

    @Mappings({
        @Mapping(target = "id", ignore = true),
    })
    public abstract void add(@MappingTarget Team target, Team source);
}
