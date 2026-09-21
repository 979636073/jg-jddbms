package com.jd.biz.domain.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 自定义报表表
 * </p>
 *
 * @author ali-dbhub
 * @since 2023-06-09
 */
@Getter
@Setter
@TableName("DASHBOARD_CHART_RELATION")
public class DashboardChartRelationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间
     */
    private LocalDateTime gmtCreate;

    /**
     * 修改时间
     */
    private LocalDateTime gmtModified;

    /**
     * 报表id
     */
    private Long dashboardId;

    /**
     * 图表id
     */
    private Long chartId;
}
