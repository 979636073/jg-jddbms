package com.jd.biz.controller.rdb;

import com.jd.biz.domain.core.dmp.DmpToolPackage;
import com.jd.biz.domain.core.dmp.DmpToolRegistryService;
import com.jd.common.annotation.Log;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rdb/dmp-tools")
public class DmpToolController {

    @Autowired
    private DmpToolRegistryService registryService;

    @GetMapping
    public ListResult<DmpToolPackage> list() {
        return ListResult.of(registryService.list());
    }

    @Log(title = "DMP客户端工具", businessType = BusinessType.IMPORT)
    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/upload")
    public DataResult<DmpToolPackage> upload(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "version", required = false) String version,
                                             @RequestParam(value = "sourceUrl", required = false) String sourceUrl,
                                             @RequestParam(value = "expectedSha256", required = false) String expectedSha256,
                                             @RequestParam(value = "platform", required = false) String platform) {
        try {
            return DataResult.of(registryService.upload(file, version, sourceUrl, expectedSha256, platform));
        } catch (BusinessException error) {
            return DataResult.error("DMP_TOOL_UPLOAD_FAILED", error.getMessage());
        }
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}/status")
    public DataResult<DmpToolPackage> setEnabled(@PathVariable String id,
                                                 @RequestParam boolean enabled) {
        return DataResult.of(registryService.setEnabled(id, enabled));
    }

    @Log(title = "DMP客户端工具", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}")
    public DataResult<DmpToolPackage> update(@PathVariable String id,
                                             @RequestParam String version,
                                             @RequestParam String platform) {
        try {
            return DataResult.of(registryService.update(id, version, platform));
        } catch (BusinessException error) {
            return DataResult.error("DMP_TOOL_UPDATE_FAILED", error.getMessage());
        }
    }

    @Log(title = "DMP客户端工具", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/{id}/redetect")
    public DataResult<DmpToolPackage> redetect(@PathVariable String id,
                                               @RequestParam(value = "version", required = false) String version) {
        try {
            return DataResult.of(registryService.redetect(id, version));
        } catch (BusinessException error) {
            return DataResult.error("DMP_TOOL_REDETECT_FAILED", error.getMessage());
        }
    }

    @Log(title = "DMP客户端工具", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public ActionResult delete(@PathVariable String id) {
        registryService.delete(id);
        return ActionResult.isSuccess();
    }
}
