package com.jd.biz.controller.rdb.request;

import com.jd.biz.domain.api.param.SelectResultOperation;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.request.DataSourceConsoleRequestInfo;
import com.jd.spi.model.Header;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SelectResultUpdateRequest extends DataSourceBaseRequest implements DataSourceConsoleRequestInfo {

    /**
     * 展示头的列表
     */
    private List<Header> headerList;

    /**
     * 修改后数据的列表
     */
    @NotEmpty
    private List<SelectResultOperation> operations;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 控制台id
     */
    @NotNull
    private Long consoleId;
    @Override
    public Long getConsoleId() {
        return consoleId;
    }

    /**
     * 是否是视图新增
     */
    private Boolean isView = Boolean.FALSE;

}
