package com.jd.biz.controller.rdb;

import com.jd.biz.domain.api.enums.ExportTypeEnum;
import com.jd.biz.domain.api.param.TablePageQueryParam;
import com.jd.biz.domain.api.param.TableQueryParam;
import com.jd.biz.domain.api.param.TableSelector;
import com.jd.biz.domain.api.service.TableService;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.common.util.EasyEnumUtils;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.doc.DatabaseExportService;
import com.jd.biz.controller.rdb.doc.conf.ExportOptions;
import com.jd.biz.controller.rdb.factory.ExportServiceFactory;
import com.jd.biz.controller.rdb.request.DataExportRequest;
import com.jd.biz.controller.rdb.vo.TableVO;
import com.jd.spi.model.Table;
import cn.hutool.core.date.DatePattern;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Constructor;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RdbDocController
 *
 * @author lzy
 **/
@ConnectionInfoAspect
@RequestMapping("/api/rdb/doc")
@Controller
@Slf4j
public class RdbDocController {

    @Autowired
    private TableService tableService;

    @Resource
    private RdbWebConverter rdbWebConverter;


    /**
     * export data
     *
     * @param request
     */
    @PostMapping("/export")
    public void export(@Valid @RequestBody DataExportRequest request, HttpServletResponse response) throws Exception {
        //复制模板
        ExportTypeEnum exportType = EasyEnumUtils.getEnum(ExportTypeEnum.class, request.getExportType());
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(
                        request.getDatabaseName() + "_" + LocalDateTime.now().format(DatePattern.PURE_DATETIME_FORMATTER),
                        StandardCharsets.UTF_8.toString())
                .replaceAll("\\+", "%20");
        TablePageQueryParam queryParam = rdbWebConverter.tablePageRequest2param(request);
        queryParam.setPageNo(1);
        queryParam.setPageSize(Integer.MAX_VALUE);
        TableSelector tableSelector = new TableSelector();
        tableSelector.setColumnList(true);
        tableSelector.setIndexList(true);
        PageResult<Table> tableDTOPageResult = tableService.pageQuery(queryParam, tableSelector);
        List<TableVO> tableVOS = rdbWebConverter.tableDto2vo(tableDTOPageResult.getData());
        TableQueryParam param = rdbWebConverter.tableRequest2param(request);
        for (TableVO tableVO: tableVOS) {
            param.setTableName(tableVO.getName());
            tableVO.setColumnList(tableService.queryColumns(param));
            tableVO.setIndexList(tableService.queryIndexes(param));
        }
        Class<?> targetClass = ExportServiceFactory.get(exportType.getCode());
        Constructor<?> constructor = targetClass.getDeclaredConstructor();
        DatabaseExportService databaseExportService = (DatabaseExportService) constructor.newInstance();
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + databaseExportService.getSuffix());
        response.setContentType(databaseExportService.getContentType());
        // 设置数据集合
        databaseExportService.setExportList(tableVOS);
        databaseExportService.generate(request.getDatabaseName(), response.getOutputStream(), new ExportOptions());
    }
}
