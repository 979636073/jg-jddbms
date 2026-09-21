package com.jd.biz.domain.core.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jd.biz.domain.api.model.Environment;
import com.jd.biz.domain.api.param.EnvironmentPageQueryParam;
import com.jd.biz.domain.api.service.EnvironmentService;
import com.jd.biz.domain.core.converter.EnvironmentConverter;
import com.jd.biz.domain.repository.entity.EnvironmentDO;
import com.jd.biz.domain.repository.mapper.EnvironmentMapper;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * environment
 *
 * @author Jiaju Zhuang
 */
@Slf4j
@Service
public class EnvironmentServiceImpl implements EnvironmentService {


    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private EnvironmentConverter environmentConverter;

    @Override
    public ListResult<Environment> listQuery(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return ListResult.empty();
        }
        LambdaQueryWrapper<EnvironmentDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(EnvironmentDO::getId, idList);
        List<EnvironmentDO> dataList = environmentMapper.selectList(queryWrapper);
        List<Environment> list = environmentConverter.do2dto(dataList);
        return ListResult.of(list);
    }

    @Override
    public ListResult<Environment> pageQuery(EnvironmentPageQueryParam param) {
        LambdaQueryWrapper<EnvironmentDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(EnvironmentDO::getName, "%" + param.getSearchKey() + "%")
                .or()
                .like(EnvironmentDO::getShortName, "%" + param.getSearchKey() + "%"));
        }
        List<EnvironmentDO> dataList = environmentMapper.selectList(queryWrapper);
        List<Environment> list = environmentConverter.do2dto(dataList);
        return ListResult.of(list);
    }
}
