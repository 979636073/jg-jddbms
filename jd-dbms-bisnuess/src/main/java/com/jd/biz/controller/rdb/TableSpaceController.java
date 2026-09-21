package com.jd.biz.controller.rdb;

import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.TableBriefQueryRequest;
import com.jd.biz.controller.rdb.request.TableSpaceCreateRequest;
import com.jd.biz.controller.rdb.request.TableSpaceUpdateRequest;
import com.jd.biz.controller.rdb.vo.TableVO;
import com.jd.biz.domain.api.service.TableSpaceService;
import com.jd.common.tools.base.constant.EasyToolsConstant;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.spi.model.Table;
import com.jd.spi.model.TableDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@ConnectionInfoAspect
@RequestMapping("/api/rdb/tableSpace")
@RestController
public class TableSpaceController {

    @Autowired
    private TableSpaceService tableSpaceService;

    @Resource
    private RdbWebConverter rdbWebConverter;

    /**
     * 获取表列表
     *
     * @param request 包含查询条件的请求对象
     * @return 返回包含表信息的 WebPageResult 对象
     */
    @GetMapping("/list")
    public WebPageResult<TableVO> list(@Valid TableBriefQueryRequest request) {
        ListResult<Table> tableSpaces = tableSpaceService.list(request.getDatabaseName(), request.getRequestType());
        Integer pageSize = tableSpaces.getData() != null ? tableSpaces.getData().size() : 0;
        List<TableVO> tableVOS = rdbWebConverter.tableDto2vo(tableSpaces.getData());
        return WebPageResult.of(tableVOS, Long.valueOf(tableSpaces.getData().size()), 1, pageSize);
    }

    /**
     * 获取临时表空间列表
     *
     * @param request 包含查询条件的请求对象
     * @return 返回包含表信息的 ListResult 对象
     */
    @GetMapping("/tableSpaceNames")
    public ListResult<Map<String, String>> tableSpaceNames(@Valid TableBriefQueryRequest request) {
        if (Objects.isNull(request.getRequestType())) {
            throw new BusinessException("参数缺失");
        }
        List<Map<String, String>> tableSpaces = tableSpaceService.tempList(request.getRequestType());
        return ListResult.of(tableSpaces);
    }


    /**
     * 获取表空间文件列表
     *
     * @param request 包含查询条件的请求对象
     * @return 返回包含表信息的 WebPageResult 对象
     */
    @GetMapping("/listPath")
    public WebPageResult<TableDetails> listPath(@Valid TableBriefQueryRequest request) {
        List<TableDetails> tableSpaces = tableSpaceService.listPath(request);
        Long total = Long.parseLong(tableSpaces.size()+"");
        tableSpaces = tableSpaces.stream().skip((long) (request.getPageNo() - 1) * request.getPageSize()).limit(request.getPageSize()).collect(Collectors.toList());
        return WebPageResult.of(tableSpaces, total, request.getPageNo(), request.getPageSize());
    }


    /**
     * 获取表列表
     *
     * @param request 包含查询条件的请求对象
     * @return 返回包含表信息的 WebPageResult 对象
     */
    @GetMapping("/query")
    public DataResult<TableVO> query(@Valid TableBriefQueryRequest request) {
        Table tableSpaces = tableSpaceService.query(request.getPath(), request.getDbType());
        TableVO tableVOS = rdbWebConverter.tableDto2vo(tableSpaces);
        return DataResult.of(tableVOS);
    }


    /**
     * 创建表空间
     *
     * @param request
     * @return
     */
    @PostMapping("/createTablespaceSql")
    public ActionResult createTablespace(@Valid @RequestBody TableSpaceCreateRequest request) {
        return tableSpaceService.createTablespace(request);
    }


    /**
     * 修改
     *
     * @param request
     * @return
     */
    @PostMapping("/updateTablespaceSql")
    public ActionResult updateTablespaceSql(@Valid @RequestBody TableSpaceUpdateRequest request) {
        return tableSpaceService.updateTablespaceSql(request);
    }


    /**
     * 删除表空间
     *
     * @param request
     * @return
     */
    @PostMapping("/dropTablespace")
    public ActionResult dropTablespace(@RequestBody TableSpaceCreateRequest request) {
        try {
            tableSpaceService.dropTablespace(request);
            return ActionResult.isSuccess();
        } catch (Exception e) {
            return ActionResult.fail(EasyToolsConstant.ERROR_CODE, "删除表空间失败.", e.getMessage());
        }
    }
}
