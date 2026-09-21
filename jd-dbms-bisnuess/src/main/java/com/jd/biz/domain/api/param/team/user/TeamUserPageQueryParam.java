package com.jd.biz.domain.api.param.team.user;

import com.jd.common.tools.base.wrapper.param.PageQueryParam;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Team User
 *
 * @author Jiaju Zhuang
 */
@Data
public class TeamUserPageQueryParam extends PageQueryParam {

    /**
     * 团队id
     */
    @NotNull
    private Long teamId;

    /**
     * 用户id
     */
    @NotNull
    private Long userId;

}
