package com.jd.biz.controller.operation.log;

import com.jd.biz.domain.api.model.OperationLog;
import com.jd.biz.domain.api.param.operation.OperationLogCreateParam;
import com.jd.biz.domain.api.param.operation.OperationLogPageQueryParam;
import com.jd.biz.domain.api.service.OperationLogService;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.biz.controller.operation.log.converter.OperationLogWebConverter;
import com.jd.biz.controller.operation.log.request.OperationLogCreateRequest;
import com.jd.biz.controller.operation.log.request.OperationLogQueryRequest;
import com.jd.biz.controller.operation.log.vo.OperationLogVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 历史记录服务类
 *
 * @author moji
 * @version HistoryManageController.java, v 0.1 2022年09月18日 10:55 moji Exp $
 * @date 2022/09/18
 */
@Slf4j
@RequestMapping("/api/operation/log")
@RestController
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private OperationLogWebConverter operationLogWebConverter;

    /**
     * 查询执行记录
     *
     * @param request
     * @return
     */
    @GetMapping("/list")
    public WebPageResult<OperationLogVO> list(OperationLogQueryRequest request) {
        OperationLogPageQueryParam param = operationLogWebConverter.req2param(request);
        log.warn("获取到用户的信息,USER_ID:{}", ContextUtils.getUserId());
        param.setUserId(ContextUtils.getUserId());
        PageResult<OperationLog> result = operationLogService.queryPage(param);
        List<OperationLogVO> operationLogVOList = operationLogWebConverter.dto2vo(result.getData());
        return WebPageResult.of(operationLogVOList, result.getTotal(), result.getPageNo(), result.getPageSize());
    }

    /**
     * 新增历史记录
     *
     * @param request
     * @return
     */
    @PostMapping("/create")
    public DataResult<Long> create(@RequestBody OperationLogCreateRequest request) {
        OperationLogCreateParam param = operationLogWebConverter.createReq2param(request);
        return operationLogService.create(param);
    }


}
