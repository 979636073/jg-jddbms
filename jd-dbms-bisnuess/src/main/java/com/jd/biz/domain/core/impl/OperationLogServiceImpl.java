package com.jd.biz.domain.core.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSONObject;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.model.OperationLog;
import com.jd.biz.domain.api.param.operation.OperationLogCreateParam;
import com.jd.biz.domain.api.param.operation.OperationLogPageQueryParam;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.biz.domain.api.service.OperationLogService;
import com.jd.biz.domain.core.converter.OperationLogConverter;
import com.jd.biz.domain.repository.entity.OperationLogDO;
import com.jd.biz.domain.repository.mapper.OperationLogMapper;
import com.jd.common.tools.base.wrapper.param.OrderBy;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.common.model.EasyLambdaQueryWrapper;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.tools.common.util.EasySqlUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author moji
 * @version UserExecutedDdlCoreServiceImpl.java, v 0.1 2022年09月25日 14:07 moji Exp $
 * @date 2022/09/25
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Resource
    private OperationLogMapper operationLogMapper;

    @Autowired
    private OperationLogConverter operationLogConverter;

    @Autowired
    private DataSourceService dataSourceService;

    @Override
    public DataResult<Long> create(OperationLogCreateParam param) {
        OperationLogDO userExecutedDdlDO = operationLogConverter.param2do(param);
        userExecutedDdlDO.setGmtCreate(LocalDateTime.now());
        userExecutedDdlDO.setGmtModified(LocalDateTime.now());
        Long userId = null;
        try {
            if (Objects.nonNull(ContextUtils.getUserId())) {
                userId = ContextUtils.getUserId();
            } else {
                userId = 1L;
            }
        } catch (Exception e) {
            userId = 1L;
        }
        userExecutedDdlDO.setUserId(userId);
        operationLogMapper.insert(userExecutedDdlDO);
        return DataResult.of(userExecutedDdlDO.getId());
    }

    @Override
    public PageResult<OperationLog> queryPage(OperationLogPageQueryParam param) {
        LambdaQueryWrapper<OperationLogDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OperationLogDO::getUserId, param.getUserId());
        if (Objects.nonNull(param.getDataSourceId())) {
            queryWrapper.eq(OperationLogDO::getDataSourceId, param.getDataSourceId());
        }
        if (StringUtils.isNotBlank(param.getDatabaseName())) {
            queryWrapper.eq(OperationLogDO::getDatabaseName, param.getDatabaseName());
        }
        if (StringUtils.isNotBlank(param.getSchemaName())) {
            queryWrapper.eq(OperationLogDO::getSchemaName, param.getSchemaName());
        }
        if (StringUtils.isNotBlank(param.getStatus())) {
            queryWrapper.eq(OperationLogDO::getStatus, param.getStatus());
        }
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.like(OperationLogDO::getDdl, param.getSearchKey());
        }
        if (Objects.nonNull(param.getParams())) {
            if (Objects.nonNull(param.getParams().get("beginTime"))) {
                queryWrapper.ge(OperationLogDO::getGmtCreate, param.getParams().get("beginTime") + " 00:00:00");
            }
            if (Objects.nonNull(param.getParams().get("endTime"))) {
                queryWrapper.le(OperationLogDO::getGmtCreate, param.getParams().get("endTime") + " 23:59:59");
            }
        }
        queryWrapper.orderByDesc(OperationLogDO::getGmtCreate);
        Integer start = param.getPageNo();
        Integer offset = param.getPageSize();
        Page<OperationLogDO> page = new Page<>(start, offset);
        IPage<OperationLogDO> executedDdlDOIPage = operationLogMapper.selectPage(page, queryWrapper);
        List<OperationLog> executedDdlDTOS = operationLogConverter.do2dto(executedDdlDOIPage.getRecords());
        if (CollectionUtils.isEmpty(executedDdlDTOS)) {
            return PageResult.empty(param.getPageNo(), param.getPageSize());
        }
        List<Long> dataSourceIds = executedDdlDTOS.stream().map(OperationLog::getDataSourceId).collect(Collectors.toList());
        ListResult<DataSource> dataSourceListResult = dataSourceService.queryByIds(dataSourceIds);
        Map<Long, DataSource> dataSourceMap = dataSourceListResult.getData().stream().collect(
                Collectors.toMap(DataSource::getId, Function.identity(), (a, b) -> a));
        executedDdlDTOS.forEach(executeDdl -> {
            populateAuditDetails(executeDdl);
            if (dataSourceMap.containsKey(executeDdl.getDataSourceId())) {
                executeDdl.setDataSourceName(dataSourceMap.get(executeDdl.getDataSourceId()).getAlias());
            }
        });
        return PageResult.of(executedDdlDTOS, executedDdlDOIPage.getTotal(), param);
    }

    private void populateAuditDetails(OperationLog operationLog) {
        if (StringUtils.isBlank(operationLog.getExtendInfo())) {
            return;
        }
        try {
            JSONObject extendInfo = JSONObject.parseObject(operationLog.getExtendInfo());
            operationLog.setSqlType(extendInfo.getString("sqlType"));
            operationLog.setErrorMessage(extendInfo.getString("errorMessage"));
        } catch (Exception e) {
            // 兼容历史上写入的非 JSON 扩展信息。
        }
    }
}
