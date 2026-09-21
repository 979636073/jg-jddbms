package com.jd.biz.domain.api.param.team.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

/**
 * Team User
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TeamUserCreatParam {
    /**
     * team id
     */
    @NotNull
    private Long teamId;

    /**
     * user id
     */
    private Long userId;
}
