package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.request.DataSourceConsoleRequestInfo;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author moji
 * @version TableManageRequest.java, v 0.1 2022年09月16日 17:55 moji Exp $
 * @date 2022/09/16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DmlTableRequest extends DataSourceBaseRequest implements DataSourceConsoleRequestInfo {

    /**
     * 表的名词
     */
    @NotNull
    private String tableName;

    /**
     * 控制台id
     */
    @NotNull
    private Long consoleId;

    /**
     * 分页编码
     * 只有select语句才有
     */
    private Integer pageNo;

    /**
     * 分页大小
     * 只有select语句才有
     */
    private Integer pageSize;

    /**
     * 返回全部数据
     * 只有select语句才有
     */
    private Boolean pageSizeAll;

    /**
     * 排序升序降序 true升序
     */
    private Boolean isAsc;

    /**
     * 排序字段
     */
    private String orderByColumn;

}
