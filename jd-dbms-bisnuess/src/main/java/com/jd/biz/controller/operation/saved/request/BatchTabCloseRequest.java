package com.jd.biz.controller.operation.saved.request;

import javax.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * close tab
 * @author Jiaju Zhuang
 */
@Data
public class BatchTabCloseRequest {

    /**
     * 主键
     */
    @NotNull
    private List<Long> idList;

}
