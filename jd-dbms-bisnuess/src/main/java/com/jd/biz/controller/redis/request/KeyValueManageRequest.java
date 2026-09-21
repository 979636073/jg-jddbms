package com.jd.biz.controller.redis.request;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author moji
 * @version TableManageRequest.java, v 0.1 2022年09月16日 17:55 moji Exp $
 * @date 2022/09/16
 */
@Data
public class KeyValueManageRequest extends DataSourceBaseRequest {

    /**
     * redis ddl语句
     */
    @NotNull
    private String ddl;
}
