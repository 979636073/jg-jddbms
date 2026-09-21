package com.jd.biz.domain.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 数据源历史连接信息及及状态
 * </p>
 */
@Getter
@Setter
@TableName("HISTORY_USER_LOG")
public class HistoryUserLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间
     */
    private Date gmtCreate;

    /**
     * 修改时间
     */
    private Date gmtModified;

    /**
     * 登陆状态 1已登录 0未登录
     */

    private Boolean status;

    /**
     * 用户ID
     */

    private Long userId;

    /**
     * 数据源ID
     */

    private Long dataSourceId;
}
