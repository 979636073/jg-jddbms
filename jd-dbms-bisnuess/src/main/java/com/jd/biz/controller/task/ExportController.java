package com.jd.biz.controller.task;

import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.request.DataExportRequest;
import com.jd.biz.controller.task.biz.TaskBizService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@ConnectionInfoAspect
@RequestMapping("/api/export")
@RestController
@Slf4j
public class ExportController {

    @Autowired
    private TaskBizService taskBizService;


    /**
     * export data
     *
     * @param request
     * @return
     */
    @PostMapping("/export_data")
    public DataResult<Long> export(@Valid @RequestBody DataExportRequest request) {
        return taskBizService.exportResultData(request);
    }

    @PostMapping("/export_doc")
    public DataResult<Long> exportDoc(@Valid @RequestBody DataExportRequest request) {
        return taskBizService.exportSchemaDoc(request);
    }


}
