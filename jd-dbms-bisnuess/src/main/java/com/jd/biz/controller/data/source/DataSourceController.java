package com.jd.biz.controller.data.source;

import cn.hutool.core.lang.Validator;
import com.jd.biz.controller.data.source.converter.DataSourceWebConverter;
import com.jd.biz.controller.data.source.converter.SSHWebConverter;
import com.jd.biz.controller.data.source.request.*;
import com.jd.biz.controller.data.source.vo.DataSourceConnectVo;
import com.jd.biz.controller.data.source.vo.DataSourceVO;
import com.jd.biz.controller.data.source.vo.DatabaseVO;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.param.ConsoleCloseParam;
import com.jd.biz.domain.api.param.ConsoleConnectParam;
import com.jd.biz.domain.api.param.datasource.*;
import com.jd.biz.domain.api.service.ConsoleService;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.common.tools.base.enums.DataSourceTypeEnum;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import com.jd.common.tools.common.exception.ConnectionException;
import com.jd.biz.aspect.ConnectionInfoAspect;
import com.jd.spi.model.Database;
import com.jd.spi.ssh.SSHManager;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 数据库连接类
 *
 * @author moji
 * @version ConnectionController.java, v 0.1 2022年09月16日 14:07 moji Exp $
 * @date 2022/09/16
 */
@ConnectionInfoAspect
@RequestMapping("/api/connection")
@RestController
@Slf4j
public class DataSourceController {

    private static final DataSourceSelector DATA_SOURCE_SELECTOR = DataSourceSelector.builder()
        .environment(Boolean.TRUE)
        .build();

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private ConsoleService consoleService;

    @Autowired
    private DataSourceWebConverter dataSourceWebConverter;

    @Autowired
    private SSHWebConverter sshWebConverter;

    /**
     * 数据库连接测试
     *
     * @param request
     * @return
     */
    @RequestMapping("/datasource/pre_connect")
    public ActionResult preConnect(@Validated @RequestBody DataSourceTestRequest request) {
        if(StringUtils.isBlank(request.getUser())){
            return ActionResult.fail("User不能为空");
        }
        if(StringUtils.isBlank(request.getPassword())){
            return ActionResult.fail("Password不能为空");
        }
        if(StringUtils.isBlank(request.getHost())){
            return ActionResult.fail("Host不能为空");
        }
        if(StringUtils.isBlank(request.getPort())){
            return ActionResult.fail("Port不能为空");
        }
        DataSourcePreConnectParam param = dataSourceWebConverter.testRequest2param(request);
        return dataSourceService.preConnect(param);
    }

    /**
     * 数据库连接测试
     *
     * @param request
     * @return
     */
    @RequestMapping("/ssh/pre_connect")
    public ActionResult sshConnect(@RequestBody SSHTestRequest request) {
        Session session = null;
        try {
            session = SSHManager.getSSHSession(sshWebConverter.toInfo(request));
        } catch (Exception e) {
            log.error("sshConnect error", e);
            throw new ConnectionException("connection.ssh.error", null, e);
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
        return ActionResult.isSuccess();
    }

    /**
     * 数据库连接
     *
     * @param request
     * @return
     */
    @GetMapping("/datasource/connect")
    public ListResult<DatabaseVO> attach(@Valid @NotNull DataSourceAttachRequest request) {
        ListResult<Database> databaseDTOListResult = dataSourceService.connect(request.getId());
        List<DatabaseVO> databaseVOS = dataSourceWebConverter.databaseDto2vo(databaseDTOListResult.getData());
        return ListResult.of(databaseVOS);
    }

    /**
     * 关闭数据库连接
     * @param request
     * @return
     */
    @GetMapping("/datasource/close")
    public ActionResult close(@Valid @NotNull DataSourceCloseRequest request) {
        return dataSourceService.close(request.getId());
    }

    /**
     * Console连接
     *
     * @param request
     * @return
     */
    @GetMapping("/console/connect")
    public ActionResult connect(@Valid @NotNull ConsoleConnectRequest request) {
        ConsoleConnectParam consoleConnectParam = dataSourceWebConverter.request2connectParam(request);
        return consoleService.createConsole(consoleConnectParam);
    }

    /**
     * 关闭Console连接
     *
     * @param request
     * @return
     */
    @GetMapping("/console/close")
    public ActionResult closeConsole(@Valid @NotNull ConsoleCloseRequest request) {
        ConsoleCloseParam closeParam = dataSourceWebConverter.request2closeParam(request);
        return consoleService.closeConsole(closeParam);
    }

    /**
     * 查询我建立的数据库连接
     *
     * @param request
     * @return
     * @version 2.1.0
     */
    @GetMapping("/datasource/list")
    public WebPageResult<DataSourceVO> list(DataSourceQueryRequest request) {
        DataSourcePageQueryParam param = dataSourceWebConverter.queryReq2param(request);
        PageResult<DataSource> result = dataSourceService.queryPageWithPermission(param, DATA_SOURCE_SELECTOR);
        List<DataSourceVO> dataSourceVOS = dataSourceWebConverter.dto2vo(result.getData());
        return WebPageResult.of(dataSourceVOS, result.getTotal(), result.getPageNo(), result.getPageSize());
    }

    /**
     * 获取连接内容
     *
     * @param id
     * @return
     */
    @GetMapping("/datasource/{id}")
    public DataResult<DataSourceVO> queryById(@PathVariable("id") Long id) {
        DataResult<DataSource> dataResult = dataSourceService.queryExistent(id, DATA_SOURCE_SELECTOR);
        DataSourceVO dataSourceVO = dataSourceWebConverter.dto2vo(dataResult.getData());
        if (StringUtils.isNotBlank(dataSourceVO.getUser())) {
            dataSourceVO.setAuthenticationType("1");
        } else {
            dataSourceVO.setAuthenticationType("2");
        }
        return DataResult.of(dataSourceVO);
    }

    /**
     * 保存连接
     *
     * @param request
     * @return
     */
    @PostMapping("/datasource/create")
    public DataResult<DataSourceConnectVo> create(@Validated  @RequestBody DataSourceCreateRequest request) {
        DataSourceCreateParam param = dataSourceWebConverter.createReq2param(request);
        if (Validator.isEmpty(param.getChecked())){
            param.setChecked(false);
        }
        return dataSourceService.createWithPermission(param);
    }

    /**
     * 更新连接
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/datasource/update", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<Long> update(@RequestBody DataSourceUpdateRequest request) {
        DataSourceUpdateParam param = dataSourceWebConverter.updateReq2param(request);
        return dataSourceService.updateWithPermission(param);
    }

    /**
     * 克隆连接
     *
     * @param request
     * @return
     */
    @PostMapping("/datasource/clone")
    public DataResult<Long> copy(@RequestBody DataSourceCloneRequest request) {
        return dataSourceService.copyByIdWithPermission(request.getId());
    }

    /**
     * 删除连接
     *
     * @param id
     * @return
     */
    @DeleteMapping("/datasource/{id}")
    public ActionResult delete(@PathVariable Long id) {
        return dataSourceService.deleteWithPermission(id);
    }

}
