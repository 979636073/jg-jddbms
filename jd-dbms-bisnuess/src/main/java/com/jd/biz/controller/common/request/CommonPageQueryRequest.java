
package com.jd.biz.controller.common.request;

import com.jd.common.tools.base.wrapper.request.PageQueryRequest;
import lombok.Data;

/**
 * Common pagination query
 *
 * @author Jiaju Zhuang
 */
@Data
public class CommonPageQueryRequest extends PageQueryRequest {


    /**
     * searchKey
     */
    private String searchKey;
}
