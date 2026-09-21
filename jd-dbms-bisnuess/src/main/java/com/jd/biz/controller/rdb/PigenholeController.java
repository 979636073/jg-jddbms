package com.jd.biz.controller.rdb;


import cn.hutool.core.util.StrUtil;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.request.ExportPigenholeRequest;
import com.jd.biz.controller.rdb.request.ImportPigenholeRequest;
import com.jd.biz.domain.api.service.PigenholeService;
import com.jd.common.annotation.Log;
import com.jd.common.core.controller.BaseController;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.wrapper.result.DataResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;

@Slf4j
@ConnectionInfoAspect
@RequestMapping("/api/rdb/pigenhole")
@RestController
public class PigenholeController extends BaseController {

    @Autowired
    private PigenholeService pigenholeService;

    @GetMapping("/getPigenhole")
    public AjaxResult getPigenhole() {
        return AjaxResult.success(pigenholeService.getPigengole());

    }

    @Log(title = "导出管理", businessType = BusinessType.EXPORT)
    @PostMapping("/exportTable")
    public void exportTable(@Valid @RequestBody ExportPigenholeRequest exportPigenholeRequest, HttpServletResponse response) {
        pigenholeService.exportTable(exportPigenholeRequest, response);
    }


    @PostMapping("/exportCheckTable")
    public DataResult<String> exportCheckTable(@Valid @RequestBody ExportPigenholeRequest exportPigenholeRequest) {
        try {
            return DataResult.of(pigenholeService.exportCheckTable(exportPigenholeRequest));
        } catch (Exception e) {
            return DataResult.error("500", "导出异常");
        }
    }


    @GetMapping("/getDictUrl")
    public DataResult<List<String>> getDictUrl() {
       return pigenholeService.getDictUrl();
    }

    @Log(title = "导入管理", businessType = BusinessType.IMPORT)
    @PostMapping("/importTable")
    public DataResult importTable(@Valid @RequestBody ImportPigenholeRequest request, HttpServletResponse response) {
        if (StrUtil.isEmpty(request.getFilePath())){
            return DataResult.error("500","文件路径不能为空");
        }
        return pigenholeService.testImport(request);
    }




}
