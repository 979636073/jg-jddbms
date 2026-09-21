package com.jd.biz.domain.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jd.biz.domain.repository.entity.TaskDO;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * TASK TABLE Mapper 接口
 * </p>
 *
 * @author chat2db
 * @since 2024-01-25
 */
public interface TaskMapper extends BaseMapper<TaskDO> {

    IPage<TaskDO> pageQuery(IPage<TaskDO> page, @Param("userId") Long userId,
                            @Param("deleted") String deleted, @Param("taskStatus") String taskStatus);
}
