package com.jd.biz.controller.operation.log.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jd.common.tools.base.wrapper.request.PageQueryRequest;
import lombok.Data;

import java.util.Map;

/**
 * @author moji
 * @version DdlCreateRequest.java, v 0.1 2022年09月18日 11:13 moji Exp $
 * @date 2022/09/18
 */
@Data
public class OperationLogQueryRequest extends PageQueryRequest {

    /** 请求参数 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> params;

    /**
     * 模糊词搜索
     */
    private String searchKey;

    /**
     * 执行状态
     */
    private String status;

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
