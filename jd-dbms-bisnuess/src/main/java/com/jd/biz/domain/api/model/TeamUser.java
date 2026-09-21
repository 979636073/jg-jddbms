package com.jd.biz.domain.api.model;

import com.jd.common.tools.base.constant.EasyToolsConstant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * Team user
 *
 * @author Jiaju Zhuang
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TeamUser implements Serializable {

    private static final long serialVersionUID = EasyToolsConstant.SERIAL_VERSION_UID;

    /**
     * 主键
     */
    @NotNull
    private Long id;

    /**
     * 团队id
     */
    @NotNull
    private Long teamId;

    /**
     * 团队
     */
    @NotNull
    private Team team;

    /**
     * 用户id
     */
    @NotNull
    private Long userId;

    /**
     * 用户
     */
    @NotNull
    private User user;

}
