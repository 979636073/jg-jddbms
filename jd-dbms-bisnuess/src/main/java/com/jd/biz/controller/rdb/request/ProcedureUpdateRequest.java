package com.jd.biz.controller.rdb.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * @author: zgq
 * @date: February 24, 2024 13:21
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcedureUpdateRequest extends DataSourceBaseRequest {

    @NotBlank
    private String procedureName;
    @NotBlank
    private String procedureBody;

}
