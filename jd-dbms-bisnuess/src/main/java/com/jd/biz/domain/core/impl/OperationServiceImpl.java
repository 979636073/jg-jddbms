package com.jd.biz.domain.core.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.model.Operation;
import com.jd.biz.domain.api.param.operation.OperationPageQueryParam;
import com.jd.biz.domain.api.param.operation.OperationQueryParam;
import com.jd.biz.domain.api.param.operation.OperationSavedParam;
import com.jd.biz.domain.api.param.operation.OperationUpdateParam;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.biz.domain.api.service.OperationService;
import com.jd.biz.domain.core.converter.OperationConverter;
import com.jd.biz.domain.core.util.PermissionUtils;
import com.jd.biz.domain.repository.entity.OperationSavedDO;
import com.jd.biz.domain.repository.mapper.OperationSavedMapper;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.common.exception.DataNotFoundException;
import com.jd.common.tools.common.model.EasyLambdaQueryWrapper;
import com.jd.common.tools.common.util.ContextUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author moji
 * @version UserSavedDdlCoreServiceImpl.java, v 0.1 2022年09月25日 15:50 moji Exp $
 * @date 2022/09/25
 */
@Service
public class OperationServiceImpl implements OperationService {

    @Resource
    private OperationSavedMapper operationSavedMapper;

    @Autowired
    private OperationConverter operationConverter;

    @Autowired
    private DataSourceService dataSourceService;

    @Override
    public DataResult<Long> createWithPermission(OperationSavedParam param) {
        OperationSavedDO userSavedDdlDO = operationConverter.param2do(param);
        userSavedDdlDO.setGmtCreate(LocalDateTime.now());
        userSavedDdlDO.setGmtModified(LocalDateTime.now());
        userSavedDdlDO.setUserId(ContextUtils.getUserId());
        operationSavedMapper.insert(userSavedDdlDO);
        return DataResult.of(userSavedDdlDO.getId());
    }

    @Override
    public ActionResult updateWithPermission(OperationUpdateParam param) {
        Operation data = queryExistent(param.getId()).getData();
        PermissionUtils.checkOperationPermission(data.getUserId());

        OperationSavedDO userSavedDdlDO = operationConverter.param2do(param);
        userSavedDdlDO.setGmtModified(LocalDateTime.now());
        operationSavedMapper.updateOperationSavedDO(userSavedDdlDO);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<Operation> find(Long id) {
        OperationSavedDO operationSavedDO = operationSavedMapper.selectById(id);
        List<Long> dataSourceIds = Lists.newArrayList(operationSavedDO.getDataSourceId());
        Map<Long, DataSource> dataSourceMap = getDataSourceInfo(dataSourceIds);
        Operation operation = operationConverter.do2dto(operationSavedDO);
        operation.setDataSourceName(dataSourceMap.containsKey(operation.getDataSourceId()) ? dataSourceMap.get(
            operation.getDataSourceId()).getAlias() : null);
        return DataResult.of(operation);
    }

    @Override
    public DataResult<Operation> queryExistent(Long id) {
        DataResult<Operation> dataResult = find(id);
        if (dataResult.getData() == null) {
            throw new DataNotFoundException();
        }
        return dataResult;
    }

    @Override
    public DataResult<Operation> queryExistent(OperationQueryParam param) {
        EasyLambdaQueryWrapper<OperationSavedDO> queryWrapper = new EasyLambdaQueryWrapper<>();
        queryWrapper.eqWhenPresent(OperationSavedDO::getId, param.getId())
            .eqWhenPresent(OperationSavedDO::getUserId, param.getUserId());
        IPage<OperationSavedDO> page = operationSavedMapper.selectPage(new Page<>(1, 1), queryWrapper);
        if (CollectionUtils.isEmpty(page.getRecords())) {
            throw new DataNotFoundException();
        }
        return DataResult.of(operationConverter.do2dto(page.getRecords().get(0)));
    }

    @Override
    public ActionResult deleteWithPermission(Long id) {
        Operation data = queryExistent(id).getData();
        PermissionUtils.checkOperationPermission(data.getUserId());

        operationSavedMapper.deleteById(id);
        return ActionResult.isSuccess();
    }

    @Override
    public PageResult<Operation> queryPage(OperationPageQueryParam param) {
        QueryWrapper<OperationSavedDO> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.like("name", param.getSearchKey());
        }
        if (Objects.nonNull(param.getDataSourceId())) {
            queryWrapper.eq("data_source_id", param.getDataSourceId());
        }
        if (StringUtils.isNotBlank(param.getDatabaseName())) {
            queryWrapper.eq("database_name", param.getDatabaseName());
        }
        if (StringUtils.isNotBlank(param.getStatus())) {
            queryWrapper.eq("status", param.getStatus());
        }
        if (StringUtils.isNotBlank(param.getTabOpened())) {
            queryWrapper.eq("tab_opened", param.getTabOpened());
        }
        if (StringUtils.isNotBlank(param.getOperationType())) {
            queryWrapper.eq("operation_type", param.getOperationType());
        }
        if (param.getUserId() != null) {
            queryWrapper.eq("user_id", param.getUserId());
        }
        Integer start = param.getPageNo();
        Integer offset = param.getPageSize();
        Page<OperationSavedDO> page = new Page<>(start, offset);
        page.setOptimizeCountSql(false);
        if (Objects.nonNull(param.getOrderByDesc()) && param.getOrderByDesc()) {
            queryWrapper.orderByDesc("gmt_modified");
        }
        if (Objects.nonNull(param.getOrderByCreateDesc()) && param.getOrderByCreateDesc()) {
            queryWrapper.orderByDesc("gmt_create");
        }
        IPage<OperationSavedDO> iPage = operationSavedMapper.selectPage(page, queryWrapper);
        List<Operation> userSavedDdlDOS = operationConverter.do2dto(iPage.getRecords());
        if (CollectionUtils.isEmpty(userSavedDdlDOS)) {
            return PageResult.empty(param.getPageNo(), param.getPageSize());
        }
        List<Long> dataSourceIds = userSavedDdlDOS.stream().map(Operation::getDataSourceId).collect(Collectors.toList());
        Map<Long, DataSource> dataSourceMap = getDataSourceInfo(dataSourceIds);
        userSavedDdlDOS.forEach(userSavedDdl -> userSavedDdl.setDataSourceName(
            dataSourceMap.containsKey(userSavedDdl.getDataSourceId()) ? dataSourceMap.get(
                userSavedDdl.getDataSourceId()).getAlias() : null));
        return PageResult.of(userSavedDdlDOS, iPage.getTotal(), param);
    }

    /**
     * 查询数据源信息
     *
     * @param dataSourceIds
     * @return
     */
    private Map<Long, DataSource> getDataSourceInfo(List<Long> dataSourceIds) {
        if (CollectionUtils.isEmpty(dataSourceIds)) {
            return Maps.newHashMap();
        }
        ListResult<DataSource> dataSourceListResult = dataSourceService.queryByIds(dataSourceIds);
        Map<Long, DataSource> dataSourceMap = dataSourceListResult.getData().stream().collect(
            Collectors.toMap(DataSource::getId, Function.identity(), (a, b) -> a));
        return dataSourceMap;
    }
}

