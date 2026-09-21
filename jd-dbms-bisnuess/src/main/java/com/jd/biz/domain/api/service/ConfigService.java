
package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.model.Config;
import com.jd.biz.domain.api.param.SystemConfigParam;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;

import javax.validation.constraints.NotNull;

/**
 * @author jipengfei
 * @version : SystemConfigService.java
 */
public interface ConfigService {

    /**
     * 创建配置
     *
     * @param param
     * @return
     */
    ActionResult create(SystemConfigParam param);

    /**
     * 修改配置
     *
     * @param param
     * @return
     */
    ActionResult update(SystemConfigParam param);

    /**
     * 插入或者更新
     * @param param
     * @return
     */
    ActionResult createOrUpdate(SystemConfigParam param);

    /**
     * 根据code查询
     *
     * @param code
     * @return
     */
    DataResult<Config> find(@NotNull String code);

    /**
     * 删除
     *
     * @param code
     * @return
     */
    ActionResult delete(@NotNull String code);
}