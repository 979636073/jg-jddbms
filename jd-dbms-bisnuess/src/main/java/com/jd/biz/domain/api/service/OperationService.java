package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.model.Operation;
import com.jd.biz.domain.api.param.operation.OperationPageQueryParam;
import com.jd.biz.domain.api.param.operation.OperationQueryParam;
import com.jd.biz.domain.api.param.operation.OperationSavedParam;
import com.jd.biz.domain.api.param.operation.OperationUpdateParam;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.PageResult;

import javax.validation.constraints.NotNull;

/**
 * 用户保存ddl
 *
 * @author moji
 * @version UserSavedDdlCoreService.java, v 0.1 2022年09月23日 17:35 moji Exp $
 * @date 2022/09/23
 */
public interface OperationService {

    /**
     * 保存用户的ddl
     *
     * @param param
     * @return
     */
    DataResult<Long> createWithPermission(OperationSavedParam param);

    /**
     * 更新用户的ddl
     *
     * @param param
     * @return
     */
    ActionResult updateWithPermission(OperationUpdateParam param);

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    DataResult<Operation> find(@NotNull Long id);

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    DataResult<Operation> queryExistent(@NotNull Long id);
    /**
     * 查询一条数据
     *
     * @param param
     * @return
     */
    DataResult<Operation> queryExistent(@NotNull OperationQueryParam param);
    /**
     * 删除
     *
     * @param id
     * @return
     */
    ActionResult deleteWithPermission(@NotNull Long id);

    /**
     * 查询用户执行的ddl记录
     *
     * @param param
     * @return
     */
    PageResult<Operation> queryPage(OperationPageQueryParam param);
}
