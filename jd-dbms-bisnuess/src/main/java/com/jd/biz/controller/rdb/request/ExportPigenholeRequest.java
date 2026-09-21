package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExportPigenholeRequest  extends DataSourceBaseRequest {

    @NotNull
    private List<String> filterValue;


    private String id;

//    @NotNull
    private String pigenholeId;

    private String filters;

    private String filePath;

    private String maxSize;

    private String uploadUrl;


}
