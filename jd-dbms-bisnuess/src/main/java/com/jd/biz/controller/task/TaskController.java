package com.jd.biz.controller.task;

import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.domain.api.model.Task;
import com.jd.biz.domain.api.param.TaskPageParam;
import com.jd.biz.domain.api.service.TaskService;
import com.jd.biz.domain.api.service.DatabaseService;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.common.tools.common.util.ContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import javax.validation.Valid;

import java.net.MalformedURLException;
import java.io.File;
import java.util.Objects;

@ConnectionInfoAspect
@RequestMapping("/api/task")
@RestController
@Slf4j
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private DatabaseService databaseService;


    @GetMapping("/list")
    public WebPageResult<Task> list(@Valid TaskPageParam taskPageParam) {
        taskPageParam.setUserId(ContextUtils.getUserId());
        PageResult<Task> task = taskService.page(taskPageParam);
        return WebPageResult.of(task.getData(), task.getTotal(), task.getPageNo(), task.getPageSize());
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        DataResult<Task> task = taskService.get(id);
        if(task.getData() == null){
            log.error("task is null");
            throw new RuntimeException("task is null");
        }
        if(!Objects.equals(ContextUtils.getUserId(), task.getData().getUserId())){
            log.error("task is not belong to user");
            throw new RuntimeException("task is not belong to user");
        }

        if (task.getData().getDownloadUrl() == null || task.getData().getDownloadUrl().trim().isEmpty()) {
            throw new RuntimeException("task file is empty");
        }
        Resource resource = null;
        try {
            resource = new UrlResource(new File(task.getData().getDownloadUrl()).toURI());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        if (resource.exists() && resource.isReadable()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } else {
            throw new RuntimeException("Could not read the file!");
        }

    }

    @PostMapping("/clear/{id}")
    public ActionResult clear(@PathVariable Long id) {
        return taskService.clear(id, ContextUtils.getUserId());
    }

    @PostMapping("/clearFinished")
    public ActionResult clearFinished() {
        return taskService.clearFinished(ContextUtils.getUserId());
    }

    @PostMapping("/cancel/{id}")
    public ActionResult cancel(@PathVariable Long id) {
        DataResult<Task> taskResult = taskService.get(id);
        Task task = taskResult.getData();
        if (task == null || !Objects.equals(ContextUtils.getUserId(), task.getUserId())) {
            return ActionResult.fail("任务不存在");
        }
        if (!"UPLOAD_TABLE_STRUCTURE".equals(task.getTaskType())
                || (!"INIT".equals(task.getTaskStatus()) && !"PROCESSING".equals(task.getTaskStatus()))) {
            return ActionResult.fail("该任务不能取消");
        }
        return databaseService.cancelDmpImport(id);
    }



}
