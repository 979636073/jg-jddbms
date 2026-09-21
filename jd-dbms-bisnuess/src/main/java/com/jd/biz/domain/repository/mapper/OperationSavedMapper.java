package com.jd.biz.domain.repository.mapper;

import com.jd.biz.domain.repository.entity.OperationSavedDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 我的保存表 Mapper 接口
 * </p>
 *
 * @author ali-dbhub
 * @since 2022-12-28
 */
public interface OperationSavedMapper extends BaseMapper<OperationSavedDO> {

    void updateOperationSavedDO(OperationSavedDO userSavedDdlDO);
}
