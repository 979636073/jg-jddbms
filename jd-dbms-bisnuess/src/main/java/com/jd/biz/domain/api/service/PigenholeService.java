package com.jd.biz.domain.api.service;

import com.jd.biz.controller.rdb.request.ExportPigenholeRequest;
import com.jd.biz.controller.rdb.request.ImportPigenholeRequest;
import com.jd.biz.domain.api.chart.ChartCreateParam;
import com.jd.biz.domain.api.chart.ChartListQueryParam;
import com.jd.biz.domain.api.chart.ChartQueryParam;
import com.jd.biz.domain.api.chart.ChartUpdateParam;
import com.jd.biz.domain.api.model.Chart;
import com.jd.biz.domain.repository.entity.PigenholeDO;
import com.jd.common.core.domain.AjaxResult;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;


public interface PigenholeService {


    void exportTable(ExportPigenholeRequest exportPigenholeRequest, HttpServletResponse response);


    DataResult importTable(ExportPigenholeRequest exportPigenholeRequest);


    DataResult testImport(ImportPigenholeRequest request);

    List<PigenholeDO> getPigengole();


    DataResult<List<String>> getDictUrl();


    String exportCheckTable(ExportPigenholeRequest exportPigenholeRequest) throws Exception;

}
