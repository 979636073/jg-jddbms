
package com.jd.biz.domain.core.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jd.biz.domain.api.model.Config;
import com.jd.biz.domain.api.param.SystemConfigParam;
import com.jd.biz.domain.api.service.ConfigService;
import com.jd.biz.domain.core.converter.ConfigConverter;
import com.jd.biz.domain.repository.entity.SystemConfigDO;
import com.jd.biz.domain.repository.mapper.SystemConfigMapper;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * @author jipengfei
 * @version : ConfigServiceImpl.java
 */
@Service
public class ConfigServiceImpl implements ConfigService {

    @Resource
    private SystemConfigMapper systemConfigMapper;

    @Autowired
    private ConfigConverter configConverter;

    @Override
    public ActionResult create(SystemConfigParam param) {
        SystemConfigDO systemConfigDO = configConverter.param2do(param);
        systemConfigDO.setGmtCreate(LocalDateTime.now());
        systemConfigDO.setGmtModified(LocalDateTime.now());
        systemConfigMapper.insert(systemConfigDO);
        return ActionResult.isSuccess();
    }

    @Override
    public ActionResult update(SystemConfigParam param) {
        SystemConfigDO systemConfigDO = configConverter.param2do(param);
        UpdateWrapper<SystemConfigDO> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("code", param.getCode());
        systemConfigMapper.update(systemConfigDO, updateWrapper);
        return ActionResult.isSuccess();
    }

    @Override
    public ActionResult createOrUpdate(SystemConfigParam param) {
        SystemConfigDO systemConfigDO = systemConfigMapper.selectOne(
            new UpdateWrapper<SystemConfigDO>().eq("code", param.getCode()));
        if (systemConfigDO == null) {
            return create(param);
        } else {
            return update(param);
        }
    }

    @Override
    public DataResult<Config> find(String code) {
        SystemConfigDO systemConfigDO = systemConfigMapper.selectOne(
            new UpdateWrapper<SystemConfigDO>().eq("code", code));
        return DataResult.of(configConverter.do2model(systemConfigDO));
    }

    @Override
    public ActionResult delete(String code) {
        systemConfigMapper.delete(new UpdateWrapper<SystemConfigDO>().eq("code", code));
        return ActionResult.isSuccess();
    }
}