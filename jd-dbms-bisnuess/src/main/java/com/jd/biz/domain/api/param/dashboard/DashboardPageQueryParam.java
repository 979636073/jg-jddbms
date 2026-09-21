package com.jd.biz.domain.api.param.dashboard;

import com.jd.common.tools.base.wrapper.param.PageQueryParam;
import lombok.Data;

/**
 * @author moji
 * @version UserSavedDdlPageQueryParam.java, v 0.1 2022年09月25日 14:05 moji Exp $
 * @date 2022/09/25
 */
@Data
public class DashboardPageQueryParam extends PageQueryParam {

    /**
     * 搜索关键词
     */
    private String searchKey;

    /**
     * 用户id
     */
    private Long userId;

}
