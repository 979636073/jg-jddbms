package com.jd.biz.domain.api.param.operation;

import com.jd.common.tools.base.wrapper.param.PageQueryParam;
import lombok.Data;

import java.util.Map;

/**
 * @author moji
 * @version UserExecutedDdlPageQueryParam.java, v 0.1 2022年09月25日 14:05 moji Exp $
 * @date 2022/09/25
 */
@Data
public class OperationLogPageQueryParam extends PageQueryParam {

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 搜索关键词
     */
    private String searchKey;

    /**
     * 执行状态
     */
    private String status;

    private Map<String, Object> params;

    /**
     * 数据源id
     */
    private Long dataSourceId;

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * schema名称
     */
    private String schemaName;
}
