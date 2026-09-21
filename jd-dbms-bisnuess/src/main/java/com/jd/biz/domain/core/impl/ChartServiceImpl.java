package com.jd.biz.domain.core.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.jd.biz.domain.api.chart.ChartCreateParam;
import com.jd.biz.domain.api.chart.ChartListQueryParam;
import com.jd.biz.domain.api.chart.ChartQueryParam;
import com.jd.biz.domain.api.chart.ChartUpdateParam;
import com.jd.biz.domain.api.model.Chart;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.service.ChartService;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.biz.domain.core.converter.ChartConverter;
import com.jd.biz.domain.core.util.PermissionUtils;
import com.jd.biz.domain.repository.entity.ChartDO;
import com.jd.biz.domain.repository.entity.DashboardChartRelationDO;
import com.jd.biz.domain.repository.mapper.ChartMapper;
import com.jd.biz.domain.repository.mapper.DashboardChartRelationMapper;
import com.jd.common.tools.base.enums.YesOrNoEnum;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.common.exception.DataNotFoundException;
import com.jd.common.tools.common.model.EasyLambdaQueryWrapper;
import com.jd.common.tools.common.util.ContextUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author moji
 * @version ChartServiceImpl.java, v 0.1 2023年06月09日 16:06 moji Exp $
 * @date 2023/06/09
 */
@Service
public class ChartServiceImpl implements ChartService {

    @Autowired
    private DataSourceService dataSourceService;
    @Resource
    private DashboardChartRelationMapper dashboardChartRelationMapper;
    @Resource
    private ChartMapper chartMapper;

    @Autowired
    private ChartConverter chartConverter;

    @Override
    public DataResult<Long> createWithPermission(ChartCreateParam param) {
        param.setGmtCreate(LocalDateTime.now());
        param.setGmtModified(LocalDateTime.now());
        param.setDeleted(YesOrNoEnum.NO.getLetter());
        param.setUserId(ContextUtils.getUserId());
        ChartDO chartDO = chartConverter.param2do(param);
        chartMapper.insert(chartDO);
        return DataResult.of(chartDO.getId());
    }

    @Override
    public ActionResult updateWithPermission(ChartUpdateParam param) {
        Chart data = queryExistent(param.getId()).getData();
        PermissionUtils.checkOperationPermission(data.getUserId());

        param.setGmtModified(LocalDateTime.now());
        ChartDO chartDO = chartConverter.updateParam2do(param);
        chartMapper.updateById(chartDO);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<Chart> find(Long id) {
        ChartDO chartDO = chartMapper.selectById(id);
        if (YesOrNoEnum.YES.getLetter().equals(chartDO.getDeleted())) {
            return DataResult.empty();
        }
        Chart chart = chartConverter.do2model(chartDO);
        setDataSourceInfo(Lists.newArrayList(chart));
        return DataResult.of(chart);
    }

    @Override
    public DataResult<Chart> queryExistent(ChartQueryParam param) {
        EasyLambdaQueryWrapper<ChartDO> queryWrapper = new EasyLambdaQueryWrapper<>();
        queryWrapper
            .eq(ChartDO::getDeleted, YesOrNoEnum.NO.getLetter())
            .eqWhenPresent(ChartDO::getId, param.getId())
            .eqWhenPresent(ChartDO::getUserId, param.getUserId());
        IPage<ChartDO> page = chartMapper.selectPage(new Page<>(1, 1), queryWrapper);
        if (CollectionUtils.isEmpty(page.getRecords())) {
            throw new DataNotFoundException();
        }
        Chart data = chartConverter.do2model(page.getRecords().get(0));
        setDataSourceInfo(Lists.newArrayList(data));
        return DataResult.of(data);
    }

    @Override
    public DataResult<Chart> queryExistent(Long id) {
        DataResult<Chart> dataResult = find(id);
        if (dataResult.getData() == null) {
            throw new DataNotFoundException();
        }
        return dataResult;
    }

    @Override
    public ListResult<Chart> listQuery(ChartListQueryParam param) {
        EasyLambdaQueryWrapper<ChartDO> queryWrapper = new EasyLambdaQueryWrapper<>();
        queryWrapper
            .eq(ChartDO::getDeleted, YesOrNoEnum.NO.getLetter())
            .inWhenPresent(ChartDO::getId, param.getIdList())
            .eqWhenPresent(ChartDO::getUserId, param.getUserId());
        List<ChartDO> queryList = chartMapper.selectList(queryWrapper);
        List<Chart> list = chartConverter.do2model(queryList);
        setDataSourceInfo(list);
        return ListResult.of(list);
    }

    @Override
    public ActionResult deleteWithPermission(Long id) {
        Chart data = queryExistent(id).getData();
        PermissionUtils.checkOperationPermission(data.getUserId());

        ChartDO chartDO = new ChartDO();
        chartDO.setId(id);
        chartDO.setDeleted(YesOrNoEnum.YES.getLetter());
        chartMapper.updateById(chartDO);
        LambdaQueryWrapper<DashboardChartRelationDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DashboardChartRelationDO::getChartId, id);
        List<DashboardChartRelationDO> relationDO = dashboardChartRelationMapper.selectList(queryWrapper);
        List<Long> relationIds = relationDO.stream().map(DashboardChartRelationDO::getId).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(relationIds)) {
            dashboardChartRelationMapper.deleteBatchIds(relationIds);
        }
        return ActionResult.isSuccess();
    }

    @Override
    public ListResult<Chart> queryByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return ListResult.empty();
        }
        List<ChartDO> chartDOS = chartMapper.selectBatchIds(ids);
        List<Chart> charts = chartConverter.do2model(chartDOS);
        List<Chart> result = charts.stream().filter(o -> YesOrNoEnum.NO.getLetter().equals(o.getDeleted())).collect(Collectors.toList());
        setDataSourceInfo(result);
        return ListResult.of(result);
    }

    /**
     * 回填数据源信息
     *
     * @param result
     */
    private void setDataSourceInfo(List<Chart> result) {
        List<Long> dataSourceIds = result.stream().map(Chart::getDataSourceId).collect(Collectors.toList());
        ListResult<DataSource> dataSourceListResult = dataSourceService.queryByIds(dataSourceIds);
        Map<Long, DataSource> dataSourceMap = dataSourceListResult.getData().stream().collect(
            Collectors.toMap(DataSource::getId, Function.identity(), (a, b) -> a));
        result.forEach(o -> {
            if (dataSourceMap.containsKey(o.getDataSourceId())) {
                o.setDataSourceName(dataSourceMap.get(o.getDataSourceId()).getAlias());
            }
        });
    }
}
