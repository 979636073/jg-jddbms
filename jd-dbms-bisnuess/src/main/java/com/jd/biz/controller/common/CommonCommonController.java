
package com.jd.biz.controller.common;

import com.jd.biz.controller.common.converter.EnvironmentCommonConverter;
import com.jd.biz.domain.api.param.EnvironmentPageQueryParam;
import com.jd.biz.domain.api.service.EnvironmentService;
import com.jd.common.tools.base.wrapper.result.ListResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Basic interface
 *
 * @author Jiaju Zhuang
 */
@RequestMapping("/api/common")
@RestController
public class CommonCommonController {

    @Resource
    private EnvironmentService environmentService;
    @Resource
    private EnvironmentCommonConverter environmentCommonConverter;

    /**
     * Query all environments
     *
     * @return
     * @version 2.1.0
     */
    @GetMapping("/environment/list_all")
    public ListResult<SimpleEnvironmentVO> environmentList() {
        EnvironmentPageQueryParam environmentPageQueryParam = new EnvironmentPageQueryParam();
        //environmentPageQueryParam.setPageSize(Integer.MIN_VALUE);
        environmentPageQueryParam.setPageSize(100);
        return ListResult.of(
            environmentCommonConverter.dto2vo(environmentService.pageQuery(environmentPageQueryParam).getData()));
    }

}
