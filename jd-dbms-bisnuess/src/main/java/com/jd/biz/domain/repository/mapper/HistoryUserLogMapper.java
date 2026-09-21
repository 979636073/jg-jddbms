package com.jd.biz.domain.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jd.biz.domain.repository.entity.HistoryUserLogDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;


public interface HistoryUserLogMapper extends BaseMapper<HistoryUserLogDO> {

    @Update("update HISTORY_USER_LOG set STATUS = #{status}, GMT_MODIFIED = now() where DATA_SOURCE_ID = #{sourceId} and USER_ID = #{userId}")
    int updateByUserIdAndSourceId(@Param("sourceId") Long sourceId, @Param("userId") Long userId, @Param("status") Boolean status);

    @Update("update HISTORY_USER_LOG set STATUS = FALSE, GMT_MODIFIED = now() where USER_ID = #{userId}")
    void updateStatus(@Param("userId") Long userId);
}
