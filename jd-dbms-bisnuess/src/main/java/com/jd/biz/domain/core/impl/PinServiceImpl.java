package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.param.PinTableParam;
import com.jd.biz.domain.api.service.PinService;
import com.jd.biz.domain.core.converter.PinTableConverter;
import com.jd.biz.domain.repository.entity.PinTableDO;
import com.jd.biz.domain.repository.mapper.PinTableMapper;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.common.util.ContextUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PinServiceImpl implements PinService {

    @Autowired
    private PinTableConverter pinTableConverter;
    @Resource
    private PinTableMapper pinTableMapper;

    @Override
    public ActionResult pinTable(PinTableParam param) {
        PinTableDO entity = pinTableConverter.param2do(param);
        entity.setUserId(ContextUtils.getUserId());
        pinTableMapper.insert(entity);
        return ActionResult.isSuccess();
    }

    @Override
    public ActionResult deletePinTable(PinTableParam param) {
        param.setUserId(ContextUtils.getUserId());
        LambdaUpdateWrapper<PinTableDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(PinTableDO::getUserId, param.getUserId());
        updateWrapper.eq(PinTableDO::getDataSourceId, param.getDataSourceId());
        if (StringUtils.isNotBlank(param.getDatabaseName())) {
            updateWrapper.eq(PinTableDO::getDatabaseName, param.getDatabaseName());
        }
        if (StringUtils.isNotBlank(param.getSchemaName())) {
            updateWrapper.eq(PinTableDO::getSchemaName, param.getSchemaName());
        }
        if (StringUtils.isNotBlank(param.getTableName())) {
            updateWrapper.eq(PinTableDO::getTableName, param.getTableName());
        }
        pinTableMapper.delete(updateWrapper);
        return ActionResult.isSuccess();
    }

    @Override
    public ListResult<String> queryPinTables(PinTableParam param) {
        List<String> result = new ArrayList<>();
        LambdaQueryWrapper<PinTableDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PinTableDO::getUserId, param.getUserId());
        queryWrapper.eq(PinTableDO::getDataSourceId, param.getDataSourceId());
        if (StringUtils.isNotBlank(param.getDatabaseName())) {
            queryWrapper.eq(PinTableDO::getDatabaseName, param.getDatabaseName());
        }
        if (StringUtils.isNotBlank(param.getSchemaName())) {
            queryWrapper.eq(PinTableDO::getSchemaName, param.getSchemaName());
        }
        if (StringUtils.isNotBlank(param.getTableName())) {
            queryWrapper.eq(PinTableDO::getTableName, param.getTableName());
        }
        queryWrapper.orderByDesc(PinTableDO::getGmtModified);
        List<PinTableDO> list = pinTableMapper.selectList(queryWrapper);
        if (!CollectionUtils.isEmpty(list)) {
            result = list.stream().map(pinTableDO -> pinTableDO.getTableName()).collect(Collectors.toList());
        }
        return ListResult.of(result);
    }
}
