package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.param.PinTableParam;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.ListResult;

public interface PinService {

    /**
     * User pin table
     * @param param
     * @return
     */
    ActionResult pinTable(PinTableParam param);


    /**
     * Delete pin table
     * @param param
     * @return
     */
    ActionResult deletePinTable(PinTableParam param);


    /**
     * Query user pin tables
     * @param param
     * @return
     */
    ListResult<String> queryPinTables(PinTableParam param);
}
