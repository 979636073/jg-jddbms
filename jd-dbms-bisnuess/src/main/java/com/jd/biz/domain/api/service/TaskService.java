package com.jd.biz.domain.api.service;


import com.jd.biz.domain.api.model.Task;
import com.jd.biz.domain.api.param.TaskCreateParam;
import com.jd.biz.domain.api.param.TaskPageParam;
import com.jd.biz.domain.api.param.TaskUpdateParam;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.PageResult;

public interface TaskService {

    /**
     * create task
     *
     * @param param task param
     * @return task id
     */
    DataResult<Long> create(TaskCreateParam param);

    /**
     * update task status
     *
     * @param param task param
     * @return action result
     */
    ActionResult updateStatus(TaskUpdateParam param);


    /**
     * get task list
     *
     * @param param task id
     * @return task
     */
    PageResult<Task> page(TaskPageParam param);

    /**
     * get task
     *
     * @param id task id
     * @return task
     */
    DataResult<Task> get(Long id);

    /**
     * 清除用户已结束的任务记录。
     */
    ActionResult clear(Long id, Long userId);

    /**
     * 清除用户全部已结束的任务记录。
     */
    ActionResult clearFinished(Long userId);
}
