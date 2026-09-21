package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.request.DataSourceConsoleRequestInfo;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @author moji
 * @version TableManageRequest.java, v 0.1 2022年09月16日 17:55 moji Exp $
 * @date 2022/09/16
 */
@Data
public class GetBlobRequest extends DataSourceBaseRequest {

    /**
     * blob字段
     */
    @NotBlank
   private String blobColumn;

    /**
     * 表名
     */
    @NotBlank
   private String tableName;

    /**
     * 模式名
     */
    @NotBlank
   private String schemaName;

    /**
     * rowId
     */
    @NotBlank
   private String rowId;
}
