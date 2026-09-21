package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * @author: wangjinlong
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TableImportRequest extends DataSourceBaseRequest {
    /**
     * 导入文件路径
     */
    //@NotNull(message = "导入文件路径不能为空")
    private String importUrl;
    /**
     * 表名
     */
    @NotNull(message = "表名不能为空")
    private String tableName;

    /**
     * 是否有表头（true:有;false:无）
     */
    private Boolean haveTitle;
    /**
     * 从第几行开始导入（默认从第一行开始）
     */
    private Integer startRow;
    /**
     * 是否错误停止（true:停止;false:不停止）
     */
    private Boolean errorStop;
    /**
     * 是否错误回滚（true:回滚;false:不回滚）
     */
    private Boolean errorRollback;
}
