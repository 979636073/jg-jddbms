package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.request.DataSourceConsoleRequestInfo;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 统计数量
 *
 * @author Jiaju Zhuang
 */
@Data
public class DdlCountRequest extends DataSourceBaseRequest implements DataSourceConsoleRequestInfo {

    /**
     * sql语句
     */
    @NotNull
    private String sql;

    /**
     * 控制台id
     */
    @NotNull
    private Long consoleId;
}
