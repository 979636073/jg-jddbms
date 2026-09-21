package com.jd.biz.controller.rdb;

import com.dtflys.forest.annotation.Post;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.request.GrantDetailRequest;
import com.jd.biz.controller.rdb.request.TableDetailQueryRequest;
import com.jd.biz.domain.api.service.GrantService;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.ExecuteResult;
import com.jd.spi.model.Sql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@ConnectionInfoAspect
@RequestMapping("/api/rdb/grant")
@RestController
public class GrantController {
    @Autowired
    private GrantService grantService;

    /**
     * 授权Sql
     * @param request
     * @return
     */
    @PostMapping("/grantSql")
    public DataResult<ExecuteResult> grantSql(@Valid GrantDetailRequest request) {
        return grantService.grantSql(request);
    }

    /**
     * 删除授权
     * @param request
     * @return
     */
    @PostMapping("/deleteGrant")
    public DataResult<ExecuteResult> deleteGrant(@Valid GrantDetailRequest request) {
        return grantService.deleteGrant(request);
    }
}
