package com.jd.biz.domain.api.param.team;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * create
 *
 * @author Jiaju Zhuang
 */
@Data
public class TeamCreateParam {
    /**
     * 团队编码
     */
    @NotNull
    private String code;

    /**
     * 团队名称
     */
    @NotNull
    private String name;

    /**
     * 团队状态
     *
     * @see com.jd.biz.domain.api.enums.ValidStatusEnum
     */
    @NotNull
    private String status;


    /**
     * 角色编码
     *
     * @see com.jd.biz.domain.api.enums.RoleCodeEnum
     */
    @NotNull
    private String roleCode;

    /**
     * 团队描述
     */
    private String description;
}
