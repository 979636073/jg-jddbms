package com.jd.biz.domain.api.service;

import com.jd.biz.controller.data.source.vo.DataSourceConnectVo;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.param.datasource.*;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.common.exception.PermissionDeniedBusinessException;
import com.jd.spi.model.Database;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 数据源管理服务
 *
 * @author moji
 * @version DataSourceCoreService.java, v 0.1 2022年09月23日 15:22 moji Exp $
 * @date 2022/09/23
 */
public interface DataSourceService {

    /**
     * 创建数据源连接
     *
     * @param param
     * @return
     */
    DataResult<DataSourceConnectVo> createWithPermission(DataSourceCreateParam param);

    /**
     * 更新数据源连接
     *
     * @param param
     * @return
     */
    DataResult<Long> updateWithPermission(DataSourceUpdateParam param);

    /**
     * 删除数据源连接
     *
     * @param id
     * @return
     */
    ActionResult deleteWithPermission(@NotNull Long id);

    /**
     * 根据id查询数据源连接详情
     *
     * @param id
     * @return
     */
    DataResult<DataSource> queryById(@NotNull Long id);

    /**
     * 根据id查询数据源连接详情
     *
     * @param id
     * @return
     * @throws com.jd.common.tools.common.exception.DataNotFoundException
     */
    DataResult<DataSource> queryExistent(@NotNull Long id, DataSourceSelector selector);

    /**
     * 克隆连接
     *
     * @param id
     * @return
     */
    DataResult<Long> copyByIdWithPermission(@NotNull Long id);

    /**
     * 分页查询数据源列表
     *
     * @param param
     * @param selector
     * @return
     */
    PageResult<DataSource> queryPage(DataSourcePageQueryParam param, DataSourceSelector selector);

    /**
     * 分页查询数据源列表
     * Need to determine permissions
     *
     * @param param
     * @param selector
     * @return
     * @throws PermissionDeniedBusinessException
     */
    PageResult<DataSource> queryPageWithPermission(DataSourcePageQueryParam param, DataSourceSelector selector);

    /**
     * 通过ID列表查询数据源
     *
     * @param ids
     * @return
     * @deprecated Use {@link #listQuery(List, DataSourceSelector)}
     */
    ListResult<DataSource> queryByIds(List<Long> ids);

    /**
     * 通过ID列表查询数据源
     *
     * @param idList
     * @return
     */
    ListResult<DataSource> listQuery(List<Long> idList, DataSourceSelector selector);

    /**
     * 数据源连接测试
     *
     * @param param
     * @return
     */
    ActionResult preConnect(DataSourcePreConnectParam param);

    /**
     * 连接数据源
     *
     * @param id
     * @return
     */
    ListResult<Database> connect(Long id);

    /**
     * 关闭数据源连接
     *
     * @param id
     * @return
     */
    ActionResult close(Long id);

}
