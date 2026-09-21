package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImportPigenholeRequest extends DataSourceBaseRequest {


    @NotNull
    private String id;
    @NotNull
    private String pigenholeId;


    private String filters;

    private String filePath;


}
