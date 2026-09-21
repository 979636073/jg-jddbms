package com.jd.biz.controller.data.source.request;


import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @author moji
 * @version ConnectionCreateRequest.java, v 0.1 2022年09月16日 14:23 moji Exp $
 * @date 2022/09/16
 */
@Data
public class DataSourceCloseRequest {

    /**
     * 主键id
     */
    @NotNull
    private Long id;

}
