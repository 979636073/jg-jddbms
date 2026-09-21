package com.jd.biz.controller.operation.saved.request;

import com.jd.common.tools.base.enums.StatusEnum;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author moji
 * @version DdlCreateRequest.java, v 0.1 2022年09月18日 11:13 moji Exp $
 * @date 2022/09/18
 */
@Data
public class OperationCreateRequest extends DataSourceBaseRequest {

    /**
     * 文件别名
     */
    private String name;

    /**
     * 保存状态
     * @see StatusEnum
     */
    private String status;

    /**
     * DB TYPE
     */
    private String type;

    /**
     * ddl内容
     */
    private String ddl;

    /**
     * 是否在tab中被打开,y表示打开,n表示未打开
     */
    private String tabOpened;

    /**
     * operation type
     */
    private String operationType;
}
