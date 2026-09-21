package com.jd.biz.controller.dashboard;

import com.jd.biz.domain.api.model.Dashboard;
import com.jd.biz.domain.api.param.dashboard.DashboardCreateParam;
import com.jd.biz.domain.api.param.dashboard.DashboardPageQueryParam;
import com.jd.biz.domain.api.param.dashboard.DashboardQueryParam;
import com.jd.biz.domain.api.param.dashboard.DashboardUpdateParam;
import com.jd.biz.domain.api.service.DashboardService;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.biz.controller.dashboard.converter.DashboardWebConverter;
import com.jd.biz.controller.dashboard.request.DashboardCreateRequest;
import com.jd.biz.controller.dashboard.request.DashboardUpdateRequest;
import com.jd.biz.controller.dashboard.vo.DashboardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 保存报表类
 *
 * @author moji
 * @version DashboardController.java, v 0.1 2022年09月18日 10:55 moji Exp $
 * @date 2022/09/18
 */
@RequestMapping("/api/dashboard")
@RestController
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private DashboardWebConverter dashboardWebConverter;

    /**
     * 查询报表列表
     *
     * @param request
     * @return
     */
    @GetMapping("/list")
    public WebPageResult<DashboardVO> list(DashboardPageQueryParam request) {
        request.setUserId(ContextUtils.getUserId());
        PageResult<Dashboard> result = dashboardService.queryPage(request);
        List<DashboardVO> dashboardVOS = dashboardWebConverter.model2vo(result.getData());
        return WebPageResult.of(dashboardVOS, result.getTotal(), result.getPageNo(), result.getPageSize());
    }

    /**
     * 根据id查询报表详情
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public DataResult<DashboardVO> get(@PathVariable("id") Long id) {
        DashboardQueryParam param = new DashboardQueryParam();
        param.setId(id);
        param.setUserId(ContextUtils.getUserId());
        return dashboardService.queryExistent(param)
            .map(dashboardWebConverter::model2vo);
    }

    /**
     * 保存报表
     *
     * @param request
     * @return
     */
    @PostMapping("/create")
    public DataResult<Long> create(@RequestBody DashboardCreateRequest request) {
        DashboardCreateParam param = dashboardWebConverter.req2param(request);
        return dashboardService.createWithPermission(param);
    }

    /**
     * 更新报表
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/update", method = {RequestMethod.POST, RequestMethod.PUT})
    public ActionResult update(@RequestBody DashboardUpdateRequest request) {
        DashboardUpdateParam param = dashboardWebConverter.req2updateParam(request);
        return dashboardService.updateWithPermission(param);
    }

    /**
     * 删除报表
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public ActionResult delete(@PathVariable("id") Long id) {
        return dashboardService.deleteWithPermission(id);
    }
}
