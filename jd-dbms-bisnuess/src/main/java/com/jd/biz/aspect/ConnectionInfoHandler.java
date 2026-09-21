
package com.jd.biz.aspect;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import com.jd.biz.controller.data.source.request.DataSourceConsoleRequestInfo;
import com.jd.biz.controller.rdb.vo.ExecuteResultVO;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.service.DataSourceAccessBusinessService;
import com.jd.biz.domain.api.service.DataSourceService;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.common.exception.ParamBusinessException;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.spi.config.DriverConfig;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author jipengfei
 * @version : ConnectionInfoHandler.java
 */
@Component
@Aspect
@Slf4j
public class ConnectionInfoHandler {

    @Autowired
    private DataSourceService dataSourceService;
    @Resource
    private DataSourceAccessBusinessService dataSourceAccessBusinessService;


    /**
     * 设置数据源连接
     *
     * @param dataSourceId
     */
    public void buildContext(Long dataSourceId, Boolean isXxlJob) {
        // 设置数据源连接
        ConnectInfo connectInfo = toInfo(dataSourceId, null, isXxlJob);
        if (Objects.isNull(connectInfo)) {
            throw new BusinessException("数据源构建失败");
        }
        Chat2DBContext.putContext(connectInfo);
    }

    public ConnectInfo toInfo(Long dataSourceId, String database, Long consoleId, String schemaName, Boolean ref) {
        DataResult<DataSource> result = dataSourceService.queryById(dataSourceId);
        DataSource dataSource = result.getData();
        if (!result.success() || dataSource == null) {
            throw new ParamBusinessException("dataSourceId");
        }
        if (Boolean.TRUE.equals(ref)) {
            // Verify permissions
            dataSourceAccessBusinessService.checkPermission(dataSource);
        }
        ConnectInfo  connectInfo = new ConnectInfo();
        connectInfo.setAlias(dataSource.getAlias());
        connectInfo.setUser(dataSource.getUserName());
        connectInfo.setConsoleId(consoleId);
        connectInfo.setDataSourceId(dataSourceId);
        connectInfo.setPassword(dataSource.getPassword());
        connectInfo.setDbType(dataSource.getType());
        connectInfo.setUrl(dataSource.getUrl());
        connectInfo.setDatabase(database);
        connectInfo.setSchemaName(schemaName);
        connectInfo.setConsoleOwn(false);
        connectInfo.setDriver(dataSource.getDriver());
        connectInfo.setSsh(dataSource.getSsh());
        connectInfo.setSsl(dataSource.getSsl());
        connectInfo.setJdbc(dataSource.getJdbc());
        connectInfo.setExtendInfo(dataSource.getExtendInfo());
        connectInfo.setUrl(dataSource.getUrl());
        connectInfo.setPort(StringUtils.isNotBlank(dataSource.getPort()) ? Integer.parseInt(dataSource.getPort()) : null);
        connectInfo.setHost(dataSource.getHost());
        if (Boolean.TRUE.equals(ref)) {
            connectInfo.setLoginUser(ContextUtils.getLoginUser().getId() + "");
        } else {
            connectInfo.setLoginUser(1+"");
        }
        DriverConfig driverConfig = dataSource.getDriverConfig();
        if (driverConfig != null && driverConfig.notEmpty()) {
            connectInfo.setDriverConfig(driverConfig);
        }
        return connectInfo;
    }

    public ConnectInfo toInfo(Long dataSourceId, String database, Boolean ref) {
        return toInfo(dataSourceId, database, null, null, ref);
    }

    @Around("within(@com.jd.biz.aspect.ConnectionInfoAspect *)")
    public Object connectionInfoHandler(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        boolean isClose = true;
        try {
            Object[] params = proceedingJoinPoint.getArgs();
            if (params != null && params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    if (param instanceof DataSourceBaseRequest) {
                        Long dataSourceId = ((DataSourceBaseRequest)param).getDataSourceId();
                        String schemaName = ((DataSourceBaseRequest)param).getSchemaName();
                        String database = ((DataSourceBaseRequest)param).getDatabaseName();
                        Chat2DBContext.putContext(toInfo(dataSourceId, database, null, schemaName));
                    } else if (param instanceof DataSourceConsoleRequestInfo) {
                        Long dataSourceId = ((DataSourceConsoleRequestInfo)param).getDataSourceId();
                        Long consoleId = ((DataSourceConsoleRequestInfo)param).getConsoleId();
                        String database = ((DataSourceConsoleRequestInfo)param).getDatabaseName();
                        Chat2DBContext.putContext(toInfo(dataSourceId, database, consoleId, null));
                    } else if (param instanceof DataSourceBaseRequestInfo) {
                        Long dataSourceId = ((DataSourceBaseRequestInfo)param).getDataSourceId();
                        String database = ((DataSourceBaseRequestInfo)param).getDatabaseName();
                        Chat2DBContext.putContext(toInfo(dataSourceId, database));
                    }
                }
            }
            Object proceed = proceedingJoinPoint.proceed();
            if (proceed instanceof ListResult) {
                ListResult<?> result = (ListResult<?>) proceed;
                if (CollUtil.isNotEmpty(result.getData()) && result.getData().get(0) instanceof ExecuteResultVO) {
                    ExecuteResultVO executeResult = (ExecuteResultVO) result.getData().get(0);
                    isClose = StrUtil.isBlank(executeResult.getSessionId());
                }
            }
            return proceed;
        } finally {
            if (isClose) {
                Chat2DBContext.removeContext();
            }
        }
    }

    public ConnectInfo toInfo(Long dataSourceId, String database, Long consoleId, String schemaName) {
        DataResult<DataSource> result = dataSourceService.queryById(dataSourceId);
        DataSource dataSource = result.getData();
        if (!result.success() || dataSource == null) {
            throw new ParamBusinessException("dataSourceId");
        }

        // Verify permissions
        dataSourceAccessBusinessService.checkPermission(dataSource);

        ConnectInfo  connectInfo = new ConnectInfo();
        connectInfo.setAlias(dataSource.getAlias());
        connectInfo.setUser(dataSource.getUserName());
        connectInfo.setConsoleId(consoleId);
        connectInfo.setDataSourceId(dataSourceId);
        connectInfo.setPassword(dataSource.getPassword());
        connectInfo.setDbType(dataSource.getType());
        connectInfo.setUrl(dataSource.getUrl());
        connectInfo.setDatabase(database);
        connectInfo.setSchemaName(schemaName);
        connectInfo.setConsoleOwn(false);
        connectInfo.setDriver(dataSource.getDriver());
        connectInfo.setSsh(dataSource.getSsh());
        connectInfo.setSsl(dataSource.getSsl());
        connectInfo.setJdbc(dataSource.getJdbc());
        connectInfo.setExtendInfo(dataSource.getExtendInfo());
        connectInfo.setUrl(dataSource.getUrl());
        connectInfo.setPort(StringUtils.isNotBlank(dataSource.getPort()) ? Integer.parseInt(dataSource.getPort()) : null);
        connectInfo.setHost(dataSource.getHost());
        connectInfo.setLoginUser(ContextUtils.getLoginUser().getId() + "");
        DriverConfig driverConfig = dataSource.getDriverConfig();
        if (driverConfig != null && driverConfig.notEmpty()) {
            connectInfo.setDriverConfig(driverConfig);
        }
        return connectInfo;
    }

    public ConnectInfo toInfo(Long dataSourceId, String database) {
        return toInfo(dataSourceId, database, null, null);
    }

}
