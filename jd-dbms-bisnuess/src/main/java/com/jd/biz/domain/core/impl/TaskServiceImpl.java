package com.jd.biz.domain.core.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.jd.biz.domain.api.enums.DeletedTypeEnum;
import com.jd.biz.domain.api.enums.TaskStatusEnum;
import com.jd.biz.domain.api.model.Task;
import com.jd.biz.domain.api.param.TaskCreateParam;
import com.jd.biz.domain.api.param.TaskPageParam;
import com.jd.biz.domain.api.param.TaskUpdateParam;
import com.jd.biz.domain.api.service.TaskService;
import com.jd.biz.domain.core.converter.TaskConverter;
import com.jd.biz.domain.repository.entity.TaskDO;
import com.jd.biz.domain.repository.mapper.TaskMapper;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class TaskServiceImpl implements TaskService {

    /**
     * task converter
     */
    @Autowired
    private TaskConverter taskConverter;
    @Resource
    private TaskMapper taskMapper;

    @Override
    public DataResult<Long> create(TaskCreateParam param) {
        TaskDO taskDO = taskConverter.todo(param);
        taskDO.setDeleted(DeletedTypeEnum.N.name());
        taskDO.setTaskStatus(TaskStatusEnum.INIT.name());
        taskMapper.insert(taskDO);
        return DataResult.of(taskDO.getId());
    }

    @Override
    public ActionResult updateStatus(TaskUpdateParam param) {
        TaskDO taskDO = new TaskDO();
        taskDO.setId(param.getId());
        taskDO.setTaskStatus(param.getTaskStatus());
        taskDO.setTaskProgress(param.getTaskProgress());
        taskDO.setDownloadUrl(param.getDownloadUrl());
        taskDO.setContent(param.getContent());
        taskMapper.updateById(taskDO);
        return ActionResult.isSuccess();
    }

    @Override
    public PageResult<Task> page(TaskPageParam param) {
        Page<TaskDO> page = new Page<>();
        page.setCurrent(param.getPageNo());
        page.setSize(param.getPageSize());
        page.setOrders(Lists.newArrayList(OrderItem.desc("gmt_create")));
        IPage<TaskDO> iPage = taskMapper.pageQuery(page, param.getUserId(), DeletedTypeEnum.N.name(), param.getTaskStatus());
        if (iPage != null) {
            return PageResult.of(taskConverter.toModel(iPage.getRecords()), iPage.getTotal(), param);
        }
        return PageResult.empty(param.getPageNo(), param.getPageSize());
    }

    @Override
    public DataResult<Task> get(Long id) {
        TaskDO task = taskMapper.selectById(id);
        return DataResult.of(taskConverter.toModel(task));
    }

    @Override
    public ActionResult clear(Long id, Long userId) {
        TaskDO task = taskMapper.selectById(id);
        if (task == null || !userId.equals(task.getUserId())) {
            return ActionResult.fail("任务不存在");
        }
        if (!TaskStatusEnum.FINISH.name().equals(task.getTaskStatus())
                && !TaskStatusEnum.ERROR.name().equals(task.getTaskStatus())) {
            return ActionResult.fail("执行中的任务不能清除");
        }
        task.setDeleted(DeletedTypeEnum.Y.name());
        taskMapper.updateById(task);
        return ActionResult.isSuccess();
    }

    @Override
    public ActionResult clearFinished(Long userId) {
        LambdaUpdateWrapper<TaskDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TaskDO::getUserId, userId)
                .eq(TaskDO::getDeleted, DeletedTypeEnum.N.name())
                .in(TaskDO::getTaskStatus, TaskStatusEnum.FINISH.name(), TaskStatusEnum.ERROR.name())
                .set(TaskDO::getDeleted, DeletedTypeEnum.Y.name());
        taskMapper.update(null, wrapper);
        return ActionResult.isSuccess();
    }
}
