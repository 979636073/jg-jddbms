package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ExportWordRequest extends DataSourceBaseRequest {

    @NotBlank
    private String wordSql;

}
